import asyncio
import re
from pathlib import Path
from typing import Optional
from dataclasses import dataclass

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI

# 导入集中化配置
from config import get_think_config, LLMConfig, get_resilient_llm_config, LLMRole

from tools.codeql_compose import CodeQLComposeTool
from rag_codeql_tool import create_codeql_rag_tool


# AgentConfig 已移至 config.py，此处保留类型提示
# 使用 get_think_config() 获取推理模型配置


@dataclass
class AgentResult:
    """Result from an agent execution."""
    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """Multi-agent analyzer for CodeQL generation."""
    
    def __init__(self, config: LLMConfig = None):
        # 默认使用具备自动切换的推理模型配置
        self.config = config or get_resilient_llm_config(LLMRole.THINK)
        self.llm = None
        self.mcp_client = None
        self.tools = None
    
    async def initialize(self) -> None:
        """Initialize LLM and MCP client for reuse across agents."""
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
                "sequential-thinking": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-sequential-thinking",
                    ],
                    "transport": "stdio",
                },
            }
        )
        
        self.tools = await self.mcp_client.get_tools()
    
    async def run_agent_stream(self, prompt: str, output_callback=None, show_thinking: bool = True, event_callback=None, agent_name: str = None, agent_type: str = None):
        """Run a single agent with the given prompt and stream output, with optional thinking process display."""
        try:
            if not self.llm or not self.tools:
                await self.initialize()
            
            agent = create_agent(self.llm, self.tools)
            content_parts = []
            
            async for event in agent.astream_events({"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 100}):
                event_name = event.get("event")
                
                # Phase 3.2: 推送 AGENT_THINKING 事件（当检测到思考标记）
                if event_callback and event_name == "on_agent_action":
                    from datetime import datetime
                    action = event.get("data", {}).get("action")
                    if action and hasattr(action, "tool"):
                        thinking_message = f"决定使用工具 '{action.tool}'"
                        await event_callback({
                            "type": "agent_thinking",
                            "timestamp": datetime.now().isoformat(),
                            "agent_name": agent_name,
                            "agent_type": agent_type,
                            "message": thinking_message,
                            "data": {
                                "tool": action.tool,
                                "tool_input": action.tool_input if hasattr(action, "tool_input") else None
                            }
                        })
                
                # Phase 3.3: 推送 AGENT_TOOL_CALL 事件（当检测到工具调用）
                if event_callback and event_name == "on_tool_start":
                    from datetime import datetime
                    tool_name = event.get("name", "")
                    await event_callback({
                        "type": "agent_tool_call",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": agent_name,
                        "agent_type": agent_type,
                        "message": f"开始调用工具: {tool_name}",
                        "data": {
                            "tool_name": tool_name,
                            "event_data": event.get("data", {})
                        }
                    })
                
                # Phase 3.6: 保持CLI模式的 show_thinking 终端输出不受影响
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
                            # 优化输出显示：完整显示文件内容和目录列表
                            output_str = str(output)
                            
                            # 检查是否是目录列表（JSON格式）
                            if tool_name == "server-filesystem" and "list_directory" in event.get("name", ""):
                                try:
                                    # 尝试解析JSON格式的目录列表
                                    import json
                                    dir_data = json.loads(output_str)
                                    print(f"✅ 工具完成: {tool_name}")
                                    print("📁 目录列表:")
                                    if isinstance(dir_data, list):
                                        for item in dir_data:
                                            if isinstance(item, dict):
                                                name = item.get('name', '')
                                                item_type = item.get('type', '')
                                                if item_type == 'directory':
                                                    print(f"   📂 {name}/")
                                                else:
                                                    print(f"   📄 {name}")
                                    else:
                                        print(f"   完整输出: {output_str}")
                                except json.JSONDecodeError:
                                    # 如果不是JSON，显示完整输出
                                    print(f"✅ 工具完成: {tool_name} - 输出: {output_str}")
                            
                            # 检查是否是文件内容读取
                            elif tool_name == "server-filesystem" and "read_file" in event.get("name", ""):
                                print(f"✅ 工具完成: {tool_name}")
                                print("📖 文件内容:")
                                print("-" * 40)
                                print(output_str)
                                print("-" * 40)
                            
                            else:
                                # 其他工具保持原有显示方式
                                print(f"✅ 工具完成: {tool_name} - 输出: {output_str}")
                        else:
                            print(f"✅ 工具完成: {tool_name}")
                    
                    elif event_name == "on_agent_step":
                        # AI完成一步思考
                        step_output = event.get("data", {}).get("output", "")
                        if step_output and hasattr(step_output, "intermediate_steps"):
                            print("💭 AI完成一步推理")
                
                # Phase 3.5: 捕获最终的模型输出（确保流式输出的完整性）
                if event_name == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join([c.get("text", "") for c in chunk.content if isinstance(c, dict)])
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            # Phase 3.5: 确保所有内容都被收集（不丢失内容）
                            content_parts.append(text)
                            
                            # Phase 3.2: 推送流式输出作为 AGENT_THINKING 事件
                            if event_callback:
                                from datetime import datetime
                                await event_callback({
                                    "type": "agent_thinking",
                                    "timestamp": datetime.now().isoformat(),
                                    "agent_name": agent_name,
                                    "agent_type": agent_type,
                                    "message": text,
                                    "data": {"stream_chunk": text}
                                })
                            
                            # Phase 3.6: 保持CLI模式的实时显示AI的最终回答
                            if show_thinking:
                                print(text, end="", flush=True)
                            if output_callback:
                                output_callback(text)

            # Phase 3.5: 确保完整内容被返回
            final_content = "".join(content_parts)
            if show_thinking:
                print("\n🎯 AI推理完成")
            
            return AgentResult(content=final_content, success=True)
        
        except Exception as e:
            if show_thinking:
                print(f"❌ 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))


def extract_codeql_from_content(content: str) -> str:
    """Extract CodeQL code from content wrapped in <codeql></codeql> tags."""
    pattern = r'<codeql>(.*?)</codeql>'
    match = re.search(pattern, content, re.DOTALL)
    if match:
        return match.group(1).strip()
    return content.strip()

async def generate_codeql_query(requirement: str = None, use_rag: bool = True, db_path: str = None, show_thinking: bool = False) -> None:
    """
    生成基于用户需求的CodeQL查询，支持RAG增强。
    
    Args:
        requirement: 用户需求描述，如果为None则使用默认需求
        use_rag: 是否使用RAG技术增强提示词
        show_thinking: 是否显示AI思考过程
    """
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        codeql_tool = CodeQLComposeTool(
            analyzer=analyzer,
            database_path=db_path or "",
            language="java",
        )
        
        # 设置默认需求
        if requirement is None:
            requirement = "查找Java 的可能的Source点"
        
        print("正在生成 CodeQL 查询...")
        print(f"需求: {requirement}")
        print(f"使用RAG增强: {'是' if use_rag else '否'}")
        print("=" * 50)
        
        # 使用RAG增强提示词
        if use_rag:
            print("正在检索CodeQL Java标准库文档...")
            rag_tool = create_codeql_rag_tool()
            
            # 获取相关文档上下文
            context = rag_tool.get_relevant_context(requirement)
            print("检索完成，开始生成查询...")
            
            # 构建增强的提示词
            enhanced_prompt = f"""
基于以下CodeQL Java标准库知识，请生成相应的CodeQL查询：

相关文档上下文：
{context}

用户需求：{requirement}

请基于上述文档生成准确、符合CodeQL语法的查询。确保：
1. 使用正确的导入语句
2. 遵循CodeQL最佳实践
3. 包含适当的注释说明
4. 输出完整的可执行查询

请直接生成CodeQL代码，用<codeql>标签包裹：
"""
            prompt = enhanced_prompt
        else:
            # 非RAG时直接使用用户需求作为compose的requirement
            prompt = requirement
        
        # 流式输出
        full_content = ""
        def stream_callback(text: str):
            nonlocal full_content
            full_content += text
            print(text, end='', flush=True)
        
        # 通过Compose工具执行生成+验证
        compose_output = await codeql_tool._arun(prompt, show_thinking=show_thinking)
        print("\n" + "=" * 50)
        print("Compose 输出:")
        print(compose_output)

        # 提取 CodeQL 代码
        # 优先提取```ql代码块，否则提取<codeql>标签
        import re
        code_block = re.search(r"```ql\s*\n(.*?)\n```", compose_output, re.DOTALL)
        if code_block:
            codeql_code = code_block.group(1).strip()
        else:
            codeql_code = extract_codeql_from_content(compose_output)

        if codeql_code:
            print("生成的CodeQL查询:")
            print("=" * 50)
            print(codeql_code)
            # 保存到文件
            output_file = "generated_query.ql"
            with open(output_file, "w", encoding="utf-8") as f:
                f.write(codeql_code)
            print(f"\n查询已保存到: {output_file}")
        else:
            print("未能从Compose输出中提取CodeQL代码")
            
    except Exception as e:  
        print(f"生成过程中出现错误: {e}")

async def main() -> None:
    """主函数，支持命令行参数控制RAG功能和AI思考过程显示。"""
    import argparse
    
    parser = argparse.ArgumentParser(description="生成并验证CodeQL查询（使用CodeQLComposeTool）")
    parser.add_argument("--requirement", "-r", type=str, 
                       help="用户需求描述，例如：查找Java中的SQL注入漏洞")
    parser.add_argument("--no-rag", action="store_true", 
                       help="禁用RAG增强功能")
    parser.add_argument("--db", type=str, help="CodeQL数据库路径，用于Compose验证执行")
    parser.add_argument("--stream", action="store_true", 
                       help="启用AI思考过程显示模式")
    
    args = parser.parse_args()
    
    requirement = args.requirement
    use_rag = not args.no_rag
    
    await generate_codeql_query(requirement, use_rag, args.db, args.stream)


if __name__ == "__main__":
    asyncio.run(main())

