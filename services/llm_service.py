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
        return "读取目录结构"

    if tool_name == "search_files":
        # 清理输出，移除可能的tool_call_id等元数据
        cleaned_text = text.split("' name=")[0] if "' name=" in text else text
        cleaned_text = cleaned_text.strip().strip("'\"")

        # 检查是否是 "No matches found" 的情况
        if "no matches found" in cleaned_text.lower() or cleaned_text.lower() == "no matches found":
            return "未找到匹配"

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

    if tool_name == "ripgrep":
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        line_count = len(lines)
        if "no matches found" in text.lower() or text.lower() == "no matches found" or line_count == 0:
            return "未找到匹配"
        elif line_count > 2000:
            first_part = lines[:1000]
            last_part = lines[-1000:]
            truncated_output = '\n'.join(first_part) + '\n...\n' + '\n'.join(last_part)

            feedback = f"搜索结果共{line_count}行，已截断显示前1000行和后1000行。\n"
            feedback += "建议：使用更精确的搜索参数（如 -m 限制匹配数、更具体的关键词）来获取更少的结果。\n\n"
            feedback += truncated_output

            return feedback
        else:
            if line_count == 1:
                return f"找到1个匹配: {lines[0]}"
            else:
                return f"找到{line_count}个匹配:\n" + '\n'.join(lines[:10]) + (f"\n... 还有{line_count-10}个匹配" if line_count > 10 else "")

    if tool_name == "read_text_file":
        return "读取文件"

    # 其他工具简化输出
    if len(text) > 100:
        return "完成"
    return text[:100]


def _print_detailed_tool_output(tool_name: str, output: Any) -> None:
    """打印工具输出的详细内容。"""
    import json
    import re

    # 如果输出为 None 或空字符串，跳过详细显示（空列表会显示"空目录"）
    if output is None or output == "":
        return

    # 检查是否是目录列表工具
    is_list_dir = (
        tool_name == "list_directory" or
        "list_directory" in tool_name or
        "listDirectory" in tool_name or
        tool_name == "directory_tree"
    )

    # 检查是否是文件读取工具
    is_read_file = (
        tool_name == "read_text_file" or
        tool_name == "read_file" or
        "read_file" in tool_name or
        "readFile" in tool_name or
        "read_text_file" in tool_name
    )

    if is_list_dir:
        try:
            output_str = str(output)
            content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
            if content_match:
                content = content_match.group(1)
                content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
                output_str = content

            try:
                dir_data = json.loads(output_str)
            except (json.JSONDecodeError, ValueError):
                if isinstance(output_str, str):
                    lines = output_str.strip().split('\n')
                    items = []
                    for line in lines:
                        line = line.strip()
                        if line.startswith('[DIR]'):
                            name = line[5:].strip()
                            items.append({'name': name, 'type': 'directory'})
                        elif line.startswith('[FILE]'):
                            name = line[6:].strip()
                            items.append({'name': name, 'type': 'file'})

                    if items:
                        dir_data = items
                    else:
                        raise ValueError("无法解析目录列表")
                else:
                    dir_data = output

            print("📁 目录列表:")

            if isinstance(dir_data, list):
                if len(dir_data) == 0:
                    print("   (空目录)")
                else:
                    for item in dir_data:
                        if isinstance(item, dict):
                            name = item.get('name', '')
                            item_type = item.get('type', '')
                            icon = "📂" if item_type == 'directory' else "📄"
                            suffix = "/" if item_type == 'directory' else ""
                            print(f"   {icon} {name}{suffix}")
                        else:
                            print(f"   📄 {item}")
            elif isinstance(dir_data, dict):
                # 处理树形结构
                def print_tree(data: dict, prefix: str = "", is_last: bool = True):
                    """递归打印目录树"""
                    if "type" in data:
                        if data["type"] == "directory":
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}📂 {data.get('name', '')}/")
                            if "children" in data:
                                children = list(data["children"].items())
                                for i, (name, child) in enumerate(children):
                                    is_last_child = i == len(children) - 1
                                    extension = "    " if is_last else "│   "
                                    print_tree(child, prefix + extension, is_last_child)
                        else:
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}📄 {data.get('name', '')}")
                print_tree(dir_data)
            else:
                print(f"   完整输出: {output}")
        except (json.JSONDecodeError, TypeError, ValueError) as e:
            # 如果所有解析都失败，尝试按行显示原始内容
            output_str = str(output)
            # 提取 content='...' 中的内容（处理转义的单引号）
            content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
            if content_match:
                content = content_match.group(1)
                # 处理转义字符
                content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
                lines = content.strip().split('\n')
                print("📁 目录列表:")
                for line in lines:
                    if line.strip():
                        # 尝试识别 [DIR] 和 [FILE] 标记
                        if line.strip().startswith('[DIR]'):
                            name = line.strip()[5:].strip()
                            print(f"   📂 {name}/")
                        elif line.strip().startswith('[FILE]'):
                            name = line.strip()[6:].strip()
                            print(f"   📄 {name}")
                        else:
                            print(f"   📄 {line.strip()}")
            elif '\n' in output_str:
                lines = output_str.strip().split('\n')
                print("📁 目录列表:")
                for line in lines:
                    if line.strip():
                        print(f"   📄 {line.strip()}")

    elif is_read_file:
        # 文件读取操作：格式化显示文件内容（显示前N行）
        MAX_PREVIEW_LINES = 30  # 最多显示30行

        output_str = str(output)

        # 处理可能包含 content='...' 格式的字符串（处理转义的单引号）
        content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
        if content_match:
            content = content_match.group(1)
            # 处理转义的换行符和其他转义字符
            content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
            output_str = content

        # 处理可能包含转义字符的字符串
        if '\\n' in output_str and '\n' not in output_str:
            output_str = output_str.replace('\\n', '\n')

        lines = output_str.split('\n')
        total_lines = len(lines)

        print(f"📖 文件内容 (共 {total_lines} 行):")
        print("-" * 60)

        # 显示前N行
        preview_lines = lines[:MAX_PREVIEW_LINES]
        for i, line in enumerate(preview_lines, 1):
            print(f"{i:4d} | {line}")

        if total_lines > MAX_PREVIEW_LINES:
            print(f"... (还有 {total_lines - MAX_PREVIEW_LINES} 行未显示)")

        print("-" * 60)


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
                },
                "ripgrep": {
                    "command": "npx",
                    "args": ["-y", "mcp-ripgrep@latest"],
                    "transport": "stdio",
                },
            }
        )

        self.tools = await self.mcp_client.get_tools()
        try:
            for t in self.tools:
                try:
                    setattr(t, "handle_tool_error", True)
                except Exception:
                    pass
                try:
                    setattr(t, "handle_validation_error", True)
                except Exception:
                    pass
        except Exception:
            pass

    async def run_agent(self, prompt: str, show_thinking: bool = True, event_callback=None, agent_name: str = None, agent_type: str = None) -> AgentResult:
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

                if show_thinking:
                    if event_name == "on_agent_action":
                        action = event.get("data", {}).get("action")
                        if action and hasattr(action, "tool"):
                            print(f"🤔 AI思考: 决定使用工具 '{action.tool}'")
                            if hasattr(action, "tool_input") and action.tool_input:
                                print(f"   工具输入: {action.tool_input}")

                    elif event_name == "on_tool_start":
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
                        status = None
                        try:
                            status = getattr(output, "status", None)
                        except Exception:
                            status = None
                        if status is None and isinstance(output, dict):
                            status = output.get("status")

                        if status == "error":
                            print(f"❌ {output_preview}")
                        else:
                            if "未找到" in output_preview or "no matches found" in str(output).lower():
                                print(f"⚠️ {output_preview}")
                            else:
                                print(f"✅ {output_preview}")

                        # 显示详细内容（针对特定工具）
                        _print_detailed_tool_output(tool_name, output)
                        current_tool = None

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
