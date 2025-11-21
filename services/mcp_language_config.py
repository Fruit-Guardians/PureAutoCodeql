
import logging
import os
import platform
import shlex
import tomllib
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


class MCPLanguageConfigService:
    """
    LSP MCP 配置服务

    负责为不同语言生成 LSP MCP 服务器配置,从 config/keys.toml 读取语言特定的配置参数。
    """

    SUPPORTED_LANGUAGES = {"java", "python"}

    def __init__(self, config_provider=None):
        """
        初始化配置服务

        Args:
            config_provider: 可选的配置提供者,用于测试注入
        """
        self._config = None
        self._config_provider = config_provider
        self._load_config()

    def _load_config(self) -> None:
        if self._config_provider:
            self._config = self._config_provider
            return

        config_path = Path(__file__).parent.parent / "config" / "keys.toml"
        if not config_path.exists():
            logger.warning(f"配置文件不存在: {config_path},将使用默认配置")
            self._config = {}
            return

        try:
            with open(config_path, 'rb') as f:
                self._config = tomllib.load(f)
        except Exception as e:
            logger.error(f"加载配置文件失败: {e}")
            self._config = {}

    def is_language_supported(self, language: str) -> bool:
        normalized = language.lower()
        return normalized in self.SUPPORTED_LANGUAGES

    def get_language_server_config(
        self,
        language: str,
        workspace_path: str
    ) -> dict:
        """
        生成指定语言的 LSP MCP 配置

        Args:
            language: 语言类型 (java/python/cpp/c)
            workspace_path: 工作空间路径

        Returns:
            MCP 客户端配置字典

        Raises:
            ValueError: 不支持的语言或配置缺失
        """
        normalized_lang = language.lower()
        if not self.is_language_supported(normalized_lang):
            raise ValueError(f"不支持的语言: {language}")

        if normalized_lang in ["c"]:
            normalized_lang = "cpp"

        method_name = f"_generate_{normalized_lang}_config"
        generator = getattr(self, method_name, None)
        if not generator:
            raise ValueError(f"未实现的语言配置生成: {language}")

        return generator(workspace_path)

    def _get_mcp_command(self) -> str:
        system = platform.system()
        if system == "Windows":
            cmd_path = Path("utils") / "lsp" / "lsp-mcp.exe"
        else:
            cmd_path = Path("utils") / "lsp" / "lsp-mcp"

        cmd_str = str(cmd_path)

        if not Path(cmd_str).exists():
            logger.warning(f"MCP 可执行文件不存在: {cmd_str}")
            raise FileNotFoundError(f"MCP executable not found: {cmd_str}")

        return cmd_str

    def _format_args_from_template(
        self,
        template: str,
        **kwargs
    ) -> list:
        """
        从格式化字符串模板生成参数列表

        Args:
            template: 格式化字符串模板,如 "--workspace {workspace} --lsp {lsp}"
            **kwargs: 替换值

        Returns:
            参数列表,如 ["--workspace", "/path", "--lsp", "jdtls"]
        """
        try:
            # 替换占位符为实际值（不加引号）
            formatted = template.format(**kwargs)

            # 总是使用 posix=True 来正确处理引号和转义
            # 这样引号会被正确移除，路径参数会被正确分割
            args = shlex.split(formatted, posix=True)
            return args
        except KeyError as e:
            logger.error(f"模板缺少占位符: {e}")
            raise
        except Exception as e:
            logger.error(f"格式化参数模板失败: {e}")
            raise

    def _get_lang_config_section(self, language: str) -> dict:
        mcp_ls_config = self._config.get("mcp_language_server", {})
        return mcp_ls_config.get(language, {})

    def _get_builtin_key(self, key: str, default: str = "") -> str:
        builtin_keys = self._config.get("builtin_keys", {})
        return builtin_keys.get(key, default)

    def _escape_path(self, path: str) -> str:
        """
        转义路径中的反斜杠,以便 shlex 正确处理
        在 Windows 上 C:\Path 变为 C:\\Path, shlex 解析后变回 C:\Path
        在 Linux 上保持不变 (除非路径本身包含反斜杠)
        """
        if not path:
            return ""
        return path.replace("\\", "\\\\")

    def _normalize_path(self, path: str) -> str:
        """
        规范化路径,处理相对路径和绝对路径
        返回系统原生的路径格式 (Windows使用反斜杠, Linux使用正斜杠)
        """
        if not path:
            return ""

        path_obj = Path(path)
        if path_obj.is_absolute():
            return str(path_obj.resolve())

        project_root = Path(__file__).parent.parent
        return str((project_root / path_obj).resolve())

    def _generate_java_config(self, workspace_path: str) -> dict:
        lang_config = self._get_lang_config_section("java")

        lsp_executable = lang_config.get("lsp_executable", "")
        if not lsp_executable:
            lsp_executable = self._get_default_java_lsp_executable()
        else:
            lsp_executable = self._normalize_path(lsp_executable)

        if not os.path.exists(lsp_executable):
            logger.warning(f"Java LSP 可执行文件不存在: {lsp_executable}")

        # 优先从 builtin_keys 读取（即 keys.toml 的 [builtin_keys] 部分）
        java_home = self._get_builtin_key("java_home", "")
        if not java_home:
            # 如果配置文件没写，尝试环境变量
            java_home = os.getenv("JAVA_HOME", "")

        if not java_home:
            raise ValueError("Java LSP 需要配置 java_home 或设置 JAVA_HOME 环境变量")

        # 确保 java_home 也是规范化的绝对路径
        java_home = self._normalize_path(java_home)
        if not os.path.exists(java_home):
             # 即使路径不存在也抛出异常，避免后续执行失败
             # 注意：这里我们只检查 java_home 目录是否存在
             logger.warning(f"配置的 JAVA_HOME 路径不存在: {java_home}")

        java_executable = self._get_java_executable(java_home)
        
        # 确保 workspace_path 是原生格式
        workspace_path = str(Path(workspace_path).resolve())

        args_template = lang_config.get(
            "args_template",
            "--workspace {workspace} --lsp {lsp} -- --java-executable {java_executable}"
        )

        # 对所有路径参数进行转义，防止 shlex 吞掉反斜杠
        args = self._format_args_from_template(
            args_template,
            workspace=self._escape_path(workspace_path),
            lsp=self._escape_path(lsp_executable),
            java_executable=self._escape_path(java_executable)
        )

        return {
            "command": self._get_mcp_command(),
            "args": args,
            "transport": "stdio"
        }

    def _get_default_java_lsp_executable(self) -> str:
        system = platform.system()
        if system == "Windows":
            return str(Path("utils") / "lsp" / "jdt-language-server" / "bin" / "jdtls.bat")
        else:
            return str(Path("utils") / "lsp" / "jdt-language-server" / "bin" / "jdtls")

    def _get_java_executable(self, java_home: str) -> str:
        system = platform.system()
        if system == "Windows":
            return str(Path(java_home) / "bin" / "java.exe")
        else:
            return str(Path(java_home) / "bin" / "java")

    def _generate_python_config(self, workspace_path: str) -> dict:
        lang_config = self._get_lang_config_section("python")

        lsp_executable = lang_config.get("lsp_executable", "pyright-langserver")

        if "/" in lsp_executable or "\\" in lsp_executable:
            lsp_executable = self._normalize_path(lsp_executable)
            if not os.path.exists(lsp_executable):
                logger.warning(f"Python LSP 可执行文件不存在: {lsp_executable}")
        
        # 确保 workspace_path 是原生格式
        workspace_path = str(Path(workspace_path).resolve())

        args_template = lang_config.get(
            "args_template",
            "--workspace {workspace} --lsp {lsp} -- --stdio"
        )

        args = self._format_args_from_template(
            args_template,
            workspace=self._escape_path(workspace_path),
            lsp=self._escape_path(lsp_executable)
        )

        return {
            "command": self._get_mcp_command(),
            "args": args,
            "transport": "stdio"
        }

    # C/C++ LSP MCP 支持已退役,不再提供 _generate_cpp_config 实现
