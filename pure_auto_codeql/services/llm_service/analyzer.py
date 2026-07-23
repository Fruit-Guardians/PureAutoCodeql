"""Multi-agent orchestration: build, run, and stream LangChain agents."""

import asyncio
import functools
import time
from contextlib import AsyncExitStack

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_mcp_adapters.sessions import create_session
from langchain_mcp_adapters.tools import load_mcp_tools

from pure_auto_codeql.analysis_models import AgentResult
from pure_auto_codeql.configuration import LLMConfig, LLMRole, get_resilient_llm_config
from pure_auto_codeql.utils.logger import get_logger
from pure_auto_codeql.utils.mcp_schema_fixer import fix_mcp_tools_schemas
from pure_auto_codeql.utils.terminal_ui import (
    StreamBlock,
    print_tool_end,
    print_tool_start,
    verbose_tool_output_enabled,
)

from ..agent_mcp_config import AgentMCPConfigService
from .chat_client import RetryableChatOpenAI
from .retry import AgentRetryTracker
from .tool_output import (
    _format_tool_output,
    _limit_tool_output_tokens,
    _print_detailed_tool_output,
)


class MultiAgentAnalyzer:
    """
    用于漏洞分析工作流的多Agent分析器

    支持动态集成 LSP MCP 服务器,根据语言类型提供高级代码分析能力。
    """

    def __init__(self, config: LLMConfig = None):
        """初始化多Agent分析器。"""
        # 优先使用外部传入；否则采用具备自动切换的配置（网络不好时自动换服务商）
        self.config = config or get_resilient_llm_config(LLMRole.CHAT)
        self.llm = None
        self.mcp_client = None
        self.tools = None
        self.retry_tracker = AgentRetryTracker()
        self._mcp_sessions = {}
        self._session_stack = None
        self.language = None  # Store language for tools that need it

    async def initialize(
        self,
        event_callback=None,
        language: str = None,
        workspace_path: str = None,
        agent_type: str = "default",
    ) -> None:
        """
        初始化LLM和MCP客户端以便在多个Agent之间复用

        Args:
            event_callback: 可选的事件回调函数
            language: 可选的语言类型 (java/python/cpp/c),用于启用对应的 LSP MCP 服务
            workspace_path: 可选的工作空间路径,用于 LSP 服务器初始化
            agent_type: 可选的 Agent 类型标识,用于未来按 Agent 定制 MCP 配置,默认值保证向后兼容
        """
        logger = get_logger(__name__)
        self.llm = RetryableChatOpenAI(self.config, self.retry_tracker, event_callback)

        # Store language for tools that need it
        self.language = language

        # 使用 Agent 级别 MCP 配置服务构造 mcp_servers, 保持默认行为与原实现一致
        mcp_config_service = AgentMCPConfigService()
        mcp_servers = mcp_config_service.get_config_for_agent(
            agent_type=agent_type,
            language=language,
            workspace_path=workspace_path,
        )

        self.mcp_client = MultiServerMCPClient(mcp_servers)
        self.tools = []

        self._mcp_sessions = {}
        self._session_stack = AsyncExitStack()

        # 定义可选的 MCP 服务器（失败时可以继续）
        # tree_sitter 和 language-server 都可能因为项目配置问题失败，但不应该阻止整个流程
        optional_servers = {"language-server", "tree_sitter"}
        failed_servers = []

        for server_name, connection in mcp_servers.items():
            try:
                if server_name == "tree_sitter":
                    session = await self._session_stack.enter_async_context(
                        create_session(connection)
                    )
                    await session.initialize()
                    self._mcp_sessions[server_name] = session
                    server_tools = await load_mcp_tools(session, connection=connection)
                else:
                    server_tools = await load_mcp_tools(None, connection=connection)

                self.tools.extend(server_tools)
                logger.info(f"✓ MCP 服务器 '{server_name}' 加载成功，提供 {len(server_tools)} 个工具")
            except Exception as e:
                # 判断是否为可选服务器
                if server_name in optional_servers:
                    logger.warning(f"󰀪  可选 MCP 服务器 '{server_name}' 加载失败，将继续使用其他工具: {e}")
                    logger.warning(f"  配置: {connection}")
                    failed_servers.append(server_name)
                else:
                    # 必需的服务器失败时，清理并抛出异常
                    logger.error(f"󰅙 必需的 MCP 服务器 '{server_name}' 加载失败: {e}")
                    logger.error(f"配置的 MCP 服务器: {list(mcp_servers.keys())}")
                    for srv_name in mcp_servers.keys():
                        logger.error(f"  - {srv_name}: {mcp_servers[srv_name]}")
                    if self._session_stack is not None:
                        await self._session_stack.aclose()
                        self._session_stack = None
                        self._mcp_sessions = {}
                    raise

        if failed_servers:
            logger.info(f"󰋼  跳过了 {len(failed_servers)} 个可选 MCP 服务器: {', '.join(failed_servers)}")

        # Add LSP Function Lookup Tool (uses ripgrep, no LSP engine needed)
        # 只有当配置包含 ripgrep 时才添加 LSPFunctionLookupTool（因为该工具依赖 ripgrep）
        if "ripgrep" in mcp_servers:
            from pure_auto_codeql.tools.lsp_lookup_tool import LSPFunctionLookupTool
            # Pass language context to the tool if available
            lsp_lookup_tool = LSPFunctionLookupTool(default_language=self.language or "java")
            self.tools.append(lsp_lookup_tool)

        # Fix MCP tools schemas for Ubuntu compatibility
        # 修复Ubuntu环境下MCP工具schema不完整的问题
        # 在某些环境下，langchain-mcp-adapters返回的schema只有$schema字段，
        # 缺少type和properties，导致DeepSeek等API拒绝
        fix_mcp_tools_schemas(self.tools)

        # Wrap all tools with token limiting
        logger = get_logger(__name__)
        logger.info(f"正在包装 {len(self.tools)} 个工具（包含MCP工具和LSP查询工具）...")

        for t in self.tools:
            tool_name = getattr(t, 'name', 'unknown')

            # Wrap the _run and _arun methods (internal methods used by BaseTool)
            if hasattr(t, '_run') and callable(t._run):
                original_run = t._run

                def create_wrapped_run(original_func, name):
                    @functools.wraps(original_func)
                    def wrapped_run(*args, **kwargs):
                        logger = get_logger(__name__)
                        logger.debug(f"󰢛 调用工具 {name} (sync)")

                        # 同步工具暂不添加超时（主要是 MCP 工具使用异步）
                        result = original_func(*args, **kwargs)
                        return _limit_tool_output_tokens(result, tool_name=name)
                    return wrapped_run

                t._run = create_wrapped_run(original_run, tool_name)

            if hasattr(t, '_arun') and callable(t._arun):
                original_arun = t._arun

                def create_wrapped_arun(original_func, name):
                    @functools.wraps(original_func)
                    async def wrapped_arun(*args, **kwargs):
                        logger = get_logger(__name__)
                        logger.debug(f"󰢛 调用工具 {name} (async)")

                        # 为 MCP 工具添加 30 秒超时
                        timeout_seconds = 30
                        try:
                            result = await asyncio.wait_for(
                                original_func(*args, **kwargs),
                                timeout=timeout_seconds
                            )
                            return _limit_tool_output_tokens(result, tool_name=name)
                        except asyncio.TimeoutError:
                            logger.warning(f"󰥔 工具 {name} 调用超时 (>{timeout_seconds}秒)")
                            timeout_msg = (
                                f"工具调用超时: '{name}' 执行时间超过 {timeout_seconds} 秒。\n"
                                f"可能原因:\n"
                                f"1. LSP 服务器响应缓慢\n"
                                f"2. 工作空间文件过多\n"
                                f"3. 网络或系统资源问题\n\n"
                                f"建议: 尝试使用更具体的查询参数，或检查 LSP 服务器状态。"
                            )
                            return (timeout_msg, None)
                        except Exception as e:
                            logger.error(f"󰅙 工具 {name} 调用失败: {e}")
                            raise
                    return wrapped_arun

                t._arun = create_wrapped_arun(original_arun, tool_name)
                logger.debug(f"  ✓ 已包装工具: {tool_name}")

            # Set error handling attributes
            try:
                setattr(t, "handle_tool_error", True)
            except Exception:
                pass
            try:
                setattr(t, "handle_validation_error", True)
            except Exception:
                pass

        logger.info(f"✓ 完成包装 {len(self.tools)} 个工具")


    async def aclose(self) -> None:
        if self._session_stack is not None:
            try:
                await self._session_stack.aclose()
            finally:
                self._session_stack = None
                self._mcp_sessions = {}

    async def run_agent(
        self,
        prompt: str,
        show_thinking: bool = True,
        event_callback=None,
        agent_name: str = None,
        agent_type: str = None,
        recursion_limit: int = 300, # 思考的最大递归深度
    ) -> AgentResult:
        """使用给定的提示词运行单个Agent，可选择显示思考过程。"""
        try:
            # 只在尚未初始化LLM时自动初始化
            # 如果调用方已经显式调用 initialize() 并设置了自定义 agent_type / MCP 配置，
            # 即便 tools 为空也不应在此使用默认参数覆盖之前的配置。
            if not self.llm:
                await self.initialize(event_callback=event_callback)

            agent = create_agent(self.llm, self.tools)
            content_parts = []

            # 跟踪工具执行状态
            output_started = False
            ai_streaming = False  # 跟踪AI是否正在流式输出
            stream_block = StreamBlock()
            tool_started_at: dict[str, float] = {}

            async for event in agent.astream_events(
                {"messages": [("user", prompt)]},
                version="v1",
                config={"recursion_limit": recursion_limit},
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
                        # on_tool_start renders the same decision with a concise
                        # input summary, so avoid printing it twice.
                        pass

                    elif event_name == "on_tool_start":
                        tool_name = event.get("name", "")
                        tool_input = event.get("data", {}).get("input", {})
                        tool_key = str(event.get("run_id") or tool_name)
                        tool_started_at[tool_key] = time.monotonic()

                        if ai_streaming:
                            print()
                            ai_streaming = False

                        print_tool_start(tool_name, tool_input)

                    elif event_name == "on_tool_end":
                        tool_name = event.get("name", "")
                        tool_key = str(event.get("run_id") or tool_name)
                        elapsed = time.monotonic() - tool_started_at.pop(
                            tool_key,
                            time.monotonic(),
                        )
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
                            display_status = "error"
                        elif (
                            "未找到" in output_preview
                            or "no matches found" in str(output).lower()
                        ):
                            display_status = "empty"
                        else:
                            display_status = "success"
                        print_tool_end(tool_name, display_status, elapsed)

                        if verbose_tool_output_enabled():
                            _print_detailed_tool_output(tool_name, output)

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
                                    stream_block.start(agent_name)
                                    output_started = True
                                stream_block.write(text)
                                ai_streaming = True  # 标记AI正在流式输出

            final_content = "".join(content_parts)
            if show_thinking and output_started:
                stream_block.finish()

            return AgentResult(content=final_content, success=True)

        except Exception as e:
            if show_thinking:
                print(f"\n󰅙 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))
