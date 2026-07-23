"""API服务器配置模块"""

from __future__ import annotations

from pathlib import Path
from typing import List

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

from pure_auto_codeql.paths import get_repo_root

try:
    import tomli as tomllib  # Python < 3.11
except ImportError:
    try:
        import tomllib  # Python >= 3.11
    except ImportError:
        import tomli as tomllib  # fallback


class APIConfig(BaseSettings):
    """API服务器配置"""

    model_config = SettingsConfigDict(
        env_prefix="API_",
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # 服务器配置
    host: str = Field(default="127.0.0.1", description="服务器监听地址")
    port: int = Field(default=8000, description="服务器监听端口")
    reload: bool = Field(default=False, description="开发模式自动重载")
    workers: int = Field(default=1, description="工作进程数")

    # CORS配置
    cors_enabled: bool = Field(default=True, description="是否启用CORS")
    cors_origins: List[str] = Field(
        default=["http://localhost:3000", "http://localhost:8000","http://localhost:8080"],
        description="允许的跨域来源"
    )
    cors_allow_credentials: bool = Field(default=True, description="允许携带凭证")
    cors_allow_methods: List[str] = Field(
        default=["GET", "POST", "DELETE", "OPTIONS"],
        description="允许的HTTP方法（收窄到 API 实际使用的方法）",
    )
    cors_allow_headers: List[str] = Field(default=["*"], description="允许的HTTP头")

    # 项目路径配置
    project_root: Path = Field(
        default_factory=lambda: get_repo_root(),
        description="项目根目录"
    )
    projects_dir: Path = Field(
        default_factory=lambda: get_repo_root() / "projects",
        description="案例项目目录"
    )
    import_sources_dir: Path = Field(
        default_factory=lambda: get_repo_root() / "imports",
        description="允许通过 API 导入的源项目根目录"
    )
    allow_external_import_paths: bool = Field(
        default=False,
        description="是否允许 API 从 import_sources_dir 之外的任意本机路径导入"
    )

    # API配置
    api_prefix: str = Field(default="/api/v1", description="API路径前缀")
    legacy_api_prefix: str = Field(default="/api", description="兼容API路径前缀")
    api_title: str = Field(default="PureAutoCodeql API", description="API标题")
    api_description: str = Field(
        default="CodeQL自动化漏洞分析系统HTTP API",
        description="API描述"
    )
    api_version: str = Field(default="0.1.0", description="API版本")
    auth_token: str = Field(default="", description="API Bearer token；为空时不启用认证")
    rate_limit_per_minute: int = Field(default=120, ge=1, description="每个客户端每分钟请求上限")

    # 日志配置
    log_level: str = Field(default="INFO", description="日志级别")
    log_requests: bool = Field(default=True, description="是否记录请求日志")

    # 任务配置
    max_concurrent_tasks: int = Field(default=5, description="最大并发任务数")
    task_timeout: int = Field(default=3600, description="任务超时时间(秒)")

    # CodeQL 构建配置
    use_docker_for_cpp: bool = Field(default=False, description="C/C++ 项目是否使用 Docker 进行构建")
    docker_builder_image: str = Field(default="pure-codeql-cpp:latest", description="Docker 构建镜像名称")
    prefer_local_cpp_build: bool = Field(default=True, description="C/C++ 项目优先尝试本地两步走构建，失败后再用Docker")
    local_build_prepare_timeout: int = Field(default=300, description="本地准备阶段（configure/cmake）超时时间(秒)")

    # 自动依赖安装配置
    auto_install_dependencies: bool = Field(default=True, description="自动检测并安装缺失的C/C++构建依赖")
    auto_install_max_retries: int = Field(default=5, description="自动安装依赖后的最大重试次数")
    allow_api_build_commands: bool = Field(
        default=False,
        description="是否允许 API 请求提供 C/C++ 构建命令或脚本"
    )

def _load_keys_toml_settings() -> dict:
    """从 keys.toml 加载 [settings] 配置"""
    keys_toml_path = get_repo_root() / "config" / "keys.toml"

    if not keys_toml_path.exists():
        return {}

    try:
        with open(keys_toml_path, 'rb') as f:
            data = tomllib.load(f)
            return data.get('settings', {})
    except Exception:
        return {}


def _create_config() -> APIConfig:
    """创建配置实例，API_* 环境变量优先，keys.toml 作为默认补充。"""
    toml_settings = _load_keys_toml_settings()
    env_config = APIConfig()

    values = {}
    for key in (
        "prefer_local_cpp_build",
        "local_build_prepare_timeout",
        "use_docker_for_cpp",
        "docker_builder_image",
    ):
        if key in toml_settings and key not in env_config.model_fields_set:
            values[key] = toml_settings[key]

    if not values:
        return env_config
    return env_config.model_copy(update=values)


# 全局配置实例
config = _create_config()


def get_config() -> APIConfig:
    """获取配置实例"""
    return config
