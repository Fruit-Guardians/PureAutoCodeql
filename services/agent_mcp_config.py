from pathlib import Path
from typing import Dict, Optional, TYPE_CHECKING

from langchain_mcp_adapters.client import MultiServerMCPClient  # noqa: F401  # 预留给未来可能的直接使用

from utils.logger import get_logger
from services.mcp_language_config import MCPLanguageConfigService

if TYPE_CHECKING:
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


AGENT_TYPES: Dict[str, str] = {
    "cve_analysis": "agents.cve_analysis_agent.CVEAnalysisAgent",
    "unified_sink_path": "agents.unified_sink_path_agent.UnifiedSinkPathAgent",
    "unified_source_analysis": "agents.unified_source_analysis_agent.UnifiedSourceAnalysisAgent",
    "codeql_gen": "agents.codeql_gen_agents.codeql_gen_agent.CodeQLGenAgent",
    "codeql_error": "agents.codeql_gen_agents.codeql_error_agent.CodeQLErrorAgent",
    "codeql_fix_inplace": "agents.codeql_gen_agents.codeql_fix_inplace_agent.CodeQLFixInplaceAgent",
    "codeql_breakpoint_detect": "agents.codeql_gen_agents.codeql_breakpoint_detect_agent.CodeQLBreakpointAgent",
}

AGENT_MCP_PROFILES: Dict[str, list[str]] = {
    "cve_analysis": [],
    "unified_sink_path": ["filesystem", "ripgrep", "language-server"],
    # "unified_source_analysis": ["language-server"]
    "unified_source_analysis": ["language-server", "ripgrep"],
    "source_analysis": ["language-server", "ripgrep"],
    "codeql_gen": ["filesystem", "ripgrep", "language-server"],
    "codeql_error": ["filesystem", "ripgrep", "language-server"],
    "codeql_fix_inplace": ["filesystem", "ripgrep", "language-server"],
    "codeql_breakpoint_detect": ["filesystem", "ripgrep", "language-server"],
    "default": ["filesystem", "ripgrep", "language-server"],
}


class AgentMCPConfigService:
    """
    Agent 级别的 MCP 配置管理服务。
    """

    def __init__(self, language_config_service: Optional[MCPLanguageConfigService] = None):
        """
        初始化配置服务。
        """
        self._language_config_service = language_config_service or MCPLanguageConfigService()

    def get_config_for_agent(
        self,
        agent_type: str,
        language: Optional[str] = None,
        workspace_path: Optional[str] = None,
    ) -> Dict[str, Dict]:
        # 获取Agent对应的MCP配置文件，如果不存在则使用默认配置
        profile = AGENT_MCP_PROFILES.get(agent_type, AGENT_MCP_PROFILES["default"])

        # 构建完整的MCP服务器配置
        mcp_servers: Dict[str, Dict] = {}

        # 添加文件系统MCP
        if "filesystem" in profile:
            if not workspace_path:
                logger.warning("⚠️  filesystem MCP 需要 workspace_path 参数")
            else:
                workspace_path_str = str(workspace_path).replace("source_code", "")
                mcp_servers["filesystem"] = {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-filesystem",
                        workspace_path_str,
                    ],
                    "transport": "stdio",
                }

        # 添加ripgrep MCP
        if "ripgrep" in profile:
            mcp_servers["ripgrep"] = {
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
            }

        # 添加语言服务器MCP
        if "language-server" in profile and language and workspace_path:
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
        elif "language-server" in profile:
            logger.warning("⚠️  language-server MCP 需要 language 和 workspace_path 参数")

        return mcp_servers
