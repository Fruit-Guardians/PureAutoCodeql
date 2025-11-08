"""LLM服务模块

提供大语言模型的服务封装，包括Agent管理和执行。
"""

import json
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


def _format_tool_output(tool_name: str, output: Any) -> str:
    """简化常用工具的输出，便于终端阅读。"""

    text = str(output).strip()
    if not text:
        return "完成"

    if tool_name == "list_allowed_directories":
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        if lines and lines[0].lower().startswith("allowed"):
            lines = lines[1:]
        count = len(lines)
        return f"找到 {count} 个目录"

    if tool_name == "directory_tree":
        try:
            data = json.loads(text)
            if isinstance(data, list):
                count = len(data)
                return f"读取目录结构 ({count} 个条目)"
        except Exception:
            pass
        return "读取目录结构"

    if tool_name == "search_files":
        # 清理输出，移除可能的tool_call_id等元数据
        cleaned_text = text.split("' name=")[0] if "' name=" in text else text
        cleaned_text = cleaned_text.strip().strip("'\"")
        
        lines = [line.strip() for line in cleaned_text.splitlines() if line.strip()]
        count = len(lines)
        if count == 0:
            return "未找到文件"
        elif count == 1:
            # 提取文件名
            try:
                filename = Path(lines[0]).name if lines[0] else "文件"
            except Exception:
                filename = "文件"
            return f"找到文件: {filename}"
        else:
            return f"找到 {count} 个文件"

    if tool_name == "read_text_file":
        lines = text.splitlines()
        line_count = len(lines)
        return f"读取文件 ({line_count} 行)"

    # 其他工具简化输出
    if len(text) > 100:
        return "完成"
    return text[:100]


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

    async def run_agent(self, prompt: str, show_thinking: bool = True, event_callback=None) -> AgentResult:
        """使用给定的提示词运行单个Agent，可选择显示思考过程。"""
        try:
            if not self.llm or not self.tools:
                await self.initialize()

            agent = create_agent(self.llm, self.tools)
            content_parts = []
            
            # 跟踪工具执行状态
            current_tool = None
            tool_start_time = {}
            output_started = False
            ai_streaming = False  # 跟踪AI是否正在流式输出

            async for event in agent.astream_events(
                {"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 100}
            ):
                event_name = event.get("event")
                
                # Phase 3: 推送事件到回调
                if event_callback:
                    from datetime import datetime
                    await event_callback({
                        "type": "agent_event",
                        "event_name": event_name,
                        "data": event.get("data", {}),
                        "timestamp": datetime.now().isoformat()
                    })

                # 显示AI的思考过程
                if show_thinking:
                    if event_name == "on_tool_start":
                        # 工具开始执行
                        tool_name = event.get("name", "")
                        current_tool = tool_name
                        # 如果AI正在流式输出，先换行
                        if ai_streaming:
                            print()
                            ai_streaming = False
                        print(f"🔧 {tool_name} → ", end="", flush=True)

                    elif event_name == "on_tool_end":
                        # 工具执行完成，在同一行显示结果
                        tool_name = event.get("name", "")
                        output = event.get("data", {}).get("output", "")
                        output_preview = _format_tool_output(tool_name, output)
                        print(f"✅ {output_preview}")
                        current_tool = None

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
                                # 第一次输出时添加分隔符
                                if not output_started:
                                    print("\n" + "="*50)
                                    output_started = True
                                print(text, end="", flush=True)
                                ai_streaming = True  # 标记AI正在流式输出

            final_content = "".join(content_parts)
            if show_thinking and output_started:
                print("\n" + "="*50)
                print("🎯 AI推理完成\n")

            return AgentResult(content=final_content, success=True)

        except Exception as e:
            if show_thinking:
                print(f"\n❌ 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))
