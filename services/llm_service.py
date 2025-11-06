"""LLM服务模块

提供大语言模型的服务封装，包括Agent管理和执行。
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_openai import ChatOpenAI

from config import get_chat_config, LLMConfig, get_resilient_llm_config, LLMRole


@dataclass
class AgentResult:
    """Agent执行的结果。"""

    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """用于漏洞分析工作流的多Agent分析器。"""

    def __init__(self, config: LLMConfig = None):
        """初始化多Agent分析器。"""
        # 优先使用外部传入；否则采用具备自动切换的配置（网络不好时自动换服务商）
        self.config = config or get_resilient_llm_config(LLMRole.CHAT)
        self.llm = None
        self.mcp_client = None
        self.tools = None

    async def initialize(self) -> None:
        """初始化LLM和MCP客户端以便在多个Agent之间复用。"""
        self.llm = ChatOpenAI(
            model=self.config.model,
            api_key=self.config.api_key,
            base_url=self.config.base_url,
            temperature=self.config.temperature,
            streaming=self.config.streaming,
            max_tokens=self.config.max_tokens,
            max_retries=self.config.max_retries,
        )

        self.mcp_client = MultiServerMCPClient(
            {
                "filesystem": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-filesystem",
                        str(Path.cwd() / "projects"),
                    ],
                    "transport": "stdio",
                }
            }
        )

        self.tools = await self.mcp_client.get_tools()

    async def run_agent(self, prompt: str, show_thinking: bool = True) -> AgentResult:
        """使用给定的提示词运行单个Agent，可选择显示思考过程。"""
        try:
            if not self.llm or not self.tools:
                await self.initialize()

            agent = create_agent(self.llm, self.tools)
            content_parts = []

            async for event in agent.astream_events(
                {"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 100}
            ):
                event_name = event.get("event")

                # 显示AI的思考过程
                if show_thinking:
                    if event_name == "on_agent_action":
                        # AI决定使用工具
                        action = event.get("data", {}).get("action")
                        if action and hasattr(action, "tool"):
                            print(f"🤔 AI思考: 决定使用工具 '{action.tool}'")
                            if hasattr(action, "tool_input") and action.tool_input:
                                print(f"   工具输入: {action.tool_input}")

                    elif event_name == "on_tool_start":
                        # 工具开始执行
                        tool_name = event.get("name", "")
                        print(f"🔧 工具执行: {tool_name}")

                    elif event_name == "on_tool_end":
                        # 工具执行完成
                        tool_name = event.get("name", "")
                        output = event.get("data", {}).get("output", "")
                        if output:
                            # 截断过长的输出
                            output_preview = str(output)[:200] + ("..." if len(str(output)) > 200 else "")
                            print(f"✅ 工具完成: {tool_name} - 输出: {output_preview}")
                        else:
                            print(f"✅ 工具完成: {tool_name}")

                    elif event_name == "on_agent_step":
                        # AI完成一步思考
                        step_output = event.get("data", {}).get("output", "")
                        if step_output and hasattr(step_output, "intermediate_steps"):
                            print("💭 AI完成一步推理")

                # 捕获最终的模型输出
                if event_name == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join(
                                    [
                                        c.get("text", "")
                                        for c in chunk.content
                                        if isinstance(c, dict)
                                    ]
                                )
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            content_parts.append(text)
                            # 实时显示AI的最终回答
                            if show_thinking:
                                print(text, end="", flush=True)

            final_content = "".join(content_parts)
            if show_thinking:
                print("\n🎯 AI推理完成")

            return AgentResult(content=final_content, success=True)

        except Exception as e:
            if show_thinking:
                print(f"❌ 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))
