from pathlib import Path
from typing import Dict, Optional, TYPE_CHECKING

from langchain_mcp_adapters.client import MultiServerMCPClient  # noqa: F401  # 预留给未来可能的直接使用

from utils.logger import get_logger
from .mcp_language_config import MCPLanguageConfigService
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.paths import get_repo_root

if TYPE_CHECKING:
    from pure_auto_codeql.agents.cve_analysis_agent import CVEAnalysisAgent as _CVEAnalysisAgent
    from pure_auto_codeql.agents.unified_sink_path_agent import UnifiedSinkPathAgent as _UnifiedSinkPathAgent
    from pure_auto_codeql.agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent as _UnifiedSourceAnalysisAgent
    from pure_auto_codeql.agents.path_analysis_agent import PathAnalysisAgent as _PathAnalysisAgent
    from pure_auto_codeql.agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent as _CodeQLGenAgent
    from pure_auto_codeql.agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent as _CodeQLErrorAgent
    from pure_auto_codeql.agents.codeql_gen_agents.codeql_fix_inplace_agent import (
        CodeQLFixInplaceAgent as _CodeQLFixInplaceAgent,
    )
    from pure_auto_codeql.agents.codeql_gen_agents.codeql_breakpoint_detect_agent import (
        CodeQLBreakpointAgent as _CodeQLBreakpointAgent,
    )


logger = get_logger(__name__)


AGENT_TYPES: Dict[str, str] = {
    "cve_analysis": "pure_auto_codeql.agents.cve_analysis_agent.CVEAnalysisAgent",
    "unified_sink_path": "pure_auto_codeql.agents.unified_sink_path_agent.UnifiedSinkPathAgent",
    "unified_source_analysis": "pure_auto_codeql.agents.unified_source_analysis_agent.UnifiedSourceAnalysisAgent",
    "path_analysis": "pure_auto_codeql.agents.path_analysis_agent.PathAnalysisAgent",
    "codeql_gen": "pure_auto_codeql.agents.codeql_gen_agents.codeql_gen_agent.CodeQLGenAgent",
    "codeql_error": "pure_auto_codeql.agents.codeql_gen_agents.codeql_error_agent.CodeQLErrorAgent",
    "codeql_fix_inplace": "pure_auto_codeql.agents.codeql_gen_agents.codeql_fix_inplace_agent.CodeQLFixInplaceAgent",
    "codeql_breakpoint_detect": "pure_auto_codeql.agents.codeql_gen_agents.codeql_breakpoint_detect_agent.CodeQLBreakpointAgent",
    "template_refinement": "pure_auto_codeql.agents.codeql_gen_agents.template_refinement_agent.TemplateRefinementAgent",
    "sink_verification": "pure_auto_codeql.agents.sink_verification_agent.SinkVerificationAgent",
    "source_verification": "pure_auto_codeql.agents.source_verification_agent.SourceVerificationAgent",
}

AGENT_MCP_PROFILES: Dict[str, list[str]] = {
    "cve_analysis": [],
    "unified_sink_path": ["filesystem", "ripgrep", "language-server"],
    # "unified_source_analysis": ["language-server"],
    "unified_source_analysis": ["tree_sitter", "language-server", "filesystem"],
    "source_analysis": ["tree_sitter", "language-server", "filesystem"],
    "path_analysis": ["tree_sitter", "ripgrep"],
    "codeql_gen": [],
    "codeql_generation": ["filesystem", "ripgrep", "language-server"],
    "codeql_error": ["filesystem", "ripgrep", "language-server"],
    "codeql_fix_inplace": ["filesystem", "ripgrep", "language-server"],
    "codeql_breakpoint_detect": ["tree_sitter", "ripgrep"],
    "template_refinement": ["filesystem"],
    "source_sink_fallback": ["ripgrep"],  # 需要 ripgrep 以支持 lsplookup 工具
    "sink_verification": ["ripgrep", "language-server"],  # 需要 ripgrep 和 LSP 以支持 LSPFunctionLookupTool
    "source_verification": ["ripgrep", "language-server"],  # 需要 ripgrep 和 LSP 以支持 LSPFunctionLookupTool
    "default": [],
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

        # 针对 Source 分析类 Agent,根据语言调整 MCP 组合策略
        normalized_language = (language or "").lower()
        if agent_type in {"unified_source_analysis", "source_analysis"} and normalized_language:
            if normalized_language == "java":
                # Java: 使用 ripgrep + language-server + filesystem
                profile = ["ripgrep", "language-server"]
            elif normalized_language == "python":
                # Python: 使用 tree_sitter + language-server + filesystem
                profile = ["language-server", "ripgrep", "tree_sitter"]
            elif normalized_language in {"c", "cpp", "c++"}:
                # C/C++: 使用 tree_sitter + filesystem
                profile = ["tree_sitter", "ripgrep"]

        # 构建完整的MCP服务器配置
        mcp_servers: Dict[str, Dict] = {}

        # 添加文件系统MCP
        if "filesystem" in profile:
            if not workspace_path:
                logger.warning("⚠️  filesystem MCP 需要 workspace_path 参数")
            else:
                workspace_path_str = str(workspace_path).replace("source_code", "")
                workspace_path_obj = Path(workspace_path_str)
                parents = workspace_path_obj.parents
                repo_root = parents[1] if len(parents) > 1 else workspace_path_obj.parent

                # 添加对于错误整理Agent的MCP配置
                if agent_type == "template_refinement":
                    prompts_path = prompts_dir()
                    mcp_servers["filesystem"] = {
                        "command": "npx",
                        "args": [
                            "-y",
                            "@modelcontextprotocol/server-filesystem",
                            str(prompts_path),
                        ],
                        "transport": "stdio",
                    }
                else:
                    temp_codeql_path = repo_root / "temp" / "codeql_temp"
                    prompts_path = prompts_dir()
                    mcp_servers["filesystem"] = {
                        "command": "npx",
                        "args": [
                            "-y",
                            "@modelcontextprotocol/server-filesystem",
                            workspace_path_str,
                            str(temp_codeql_path),
                            str(prompts_path),
                        ],
                        "transport": "stdio",
                    }

        # 添加ripgrep MCP
        if "ripgrep" in profile:
            mcp_servers["ripgrep"] = {
                "command": "node",
                "args": [
                    str(
                        get_repo_root()
                        / "tools"
                        / "mcp_ripgrep"
                        / "dist"
                        / "index.js"
                    )
                ],
                "transport": "stdio",
            }

        # 添加 tree_sitter MCP
        if "tree_sitter" in profile:
            if not workspace_path:
                logger.warning("⚠️  tree_sitter MCP 需要 workspace_path 参数")
            else:
                mcp_servers["tree_sitter"] = {
                    "command": "uv",
                    "args": [
                        "--directory",
                        str(workspace_path),
                        "run",
                        "-m",
                        "mcp_server_tree_sitter.server",
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
