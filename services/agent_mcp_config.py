from pathlib import Path
from typing import Dict, Optional, TYPE_CHECKING

from langchain_mcp_adapters.client import MultiServerMCPClient  # noqa: F401  # 预留给未来可能的直接使用

from utils.logger import get_logger
from services.mcp_language_config import MCPLanguageConfigService

if TYPE_CHECKING:
    # 仅用于类型检查，避免运行时引入循环依赖
    from agents.cve_analysis_agent import CVEAnalysisAgent as _CVEAnalysisAgent
    from agents.unified_sink_path_agent import UnifiedSinkPathAgent as _UnifiedSinkPathAgent
    from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent as _UnifiedSourceAnalysisAgent
    from agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent as _CodeQLGenAgent
    from agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent as _CodeQLErrorAgent
    from agents.codeql_gen_agents.codeql_fix_inplace_agent import (
        CodeQLFixInplaceAgent as _CodeQLFixInplaceAgent,
    )
    from agents.codeql_gen_agents.codeql_breakpoint_detect_agent import (
        CodeQLBreakpointAgent as _CodeQLBreakpointAgent,
    )


logger = get_logger(__name__)


# Phase 1: 为避免运行时循环导入，这里仅保存“Agent 类型字符串 → 完整类路径”的映射。
# 如需从类型字符串获取实际类，可在调用侧按需懒加载 import。
AGENT_TYPES: Dict[str, str] = {
    "cve_analysis": "agents.cve_analysis_agent.CVEAnalysisAgent",
    "unified_sink_path": "agents.unified_sink_path_agent.UnifiedSinkPathAgent",
    "unified_source_analysis": "agents.unified_source_analysis_agent.UnifiedSourceAnalysisAgent",
    "codeql_gen": "agents.codeql_gen_agents.codeql_gen_agent.CodeQLGenAgent",
    "codeql_error": "agents.codeql_gen_agents.codeql_error_agent.CodeQLErrorAgent",
    "codeql_fix_inplace": "agents.codeql_gen_agents.codeql_fix_inplace_agent.CodeQLFixInplaceAgent",
    "codeql_breakpoint_detect": "agents.codeql_gen_agents.codeql_breakpoint_detect_agent.CodeQLBreakpointAgent",
}


class AgentMCPConfigService:
    """
    Agent 级别的 MCP 配置管理服务。

    第一阶段：所有 Agent 共用相同的默认 MCP 配置（filesystem、ripgrep、可选 language-server）。
    未来可以在此基础上为不同 agent_type 定制差异化配置。
    """

    def __init__(self, language_config_service: Optional[MCPLanguageConfigService] = None):
        """
        初始化配置服务。

        Args:
            language_config_service: 可选的语言服务器配置服务，便于在测试中注入。
        """
        self._language_config_service = language_config_service or MCPLanguageConfigService()

    def get_config_for_agent(
        self,
        agent_type: str,
        language: Optional[str] = None,
        workspace_path: Optional[str] = None,
    ) -> Dict[str, Dict]:
        """
        返回指定 Agent 的 MCP 服务器配置。

        当前阶段忽略 agent_type，所有 Agent 使用相同的默认配置，
        但保留参数以便未来扩展为按 Agent 定制配置。
        """
        # Phase 1: 所有 Agent 使用相同的默认配置
        return self._get_default_mcp_config(language=language, workspace_path=workspace_path)

    def _get_default_mcp_config(
        self,
        language: Optional[str] = None,
        workspace_path: Optional[str] = None,
    ) -> Dict[str, Dict]:
        """
        构造默认的 MCP 服务器配置。

        逻辑与 `MultiAgentAnalyzer.initialize` 中当前实现保持一致：
        - filesystem MCP
        - ripgrep MCP
        - 可选 language-server（取决于 language / workspace_path 以及配置是否可用）
        """
        mcp_servers: Dict[str, Dict] = {
            "filesystem": {
                "command": "npx",
                "args": [
                    "-y",
                    "@modelcontextprotocol/server-filesystem",
                    str(Path.cwd()),
                ],
                "transport": "stdio",
            },
            "ripgrep": {
                "command": "node",
                "args": [
                    str(
                        Path(__file__).parent.parent
                        / "tools"
                        / "mcp_ripgrep"
                        / "dist"
                        / "index.js"
                    )
                ],
                "transport": "stdio",
            },
        }

        if language and workspace_path:
            try:
                language_config = self._language_config_service
                if language_config.is_language_supported(language):
                    lsp_config = language_config.get_language_server_config(language, workspace_path)
                    mcp_servers["language-server"] = lsp_config
                    logger.info(f"✓ 已添加 {language} LSP MCP 配置")
                else:
                    logger.info(f"ℹ️  语言 {language} 不支持 LSP MCP")
            except Exception as e:  # pragma: no cover - 防御性日志记录
                logger.warning(f"⚠️  LSP MCP 配置失败,继续使用基础 MCP: {e}")

        return mcp_servers


