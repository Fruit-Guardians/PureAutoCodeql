"""API服务器配置模块"""

from __future__ import annotations

import os
from pathlib import Path
from typing import List

from pydantic import Field
from pydantic_settings import BaseSettings


class APIConfig(BaseSettings):
    """API服务器配置"""

    # 服务器配置
    host: str = Field(default="0.0.0.0", description="服务器监听地址")
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
    cors_allow_methods: List[str] = Field(default=["*"], description="允许的HTTP方法")
    cors_allow_headers: List[str] = Field(default=["*"], description="允许的HTTP头")

    # 项目路径配置
    project_root: Path = Field(
        default_factory=lambda: Path(__file__).parent.parent,
        description="项目根目录"
    )
    projects_dir: Path = Field(
        default_factory=lambda: Path(__file__).parent.parent / "projects",
        description="案例项目目录"
    )

    # API配置
    api_prefix: str = Field(default="/api", description="API路径前缀")
    api_title: str = Field(default="PureAutoCodeql API", description="API标题")
    api_description: str = Field(
        default="CodeQL自动化漏洞分析系统HTTP API",
        description="API描述"
    )
    api_version: str = Field(default="0.1.0", description="API版本")

    # 日志配置
    log_level: str = Field(default="INFO", description="日志级别")
    log_requests: bool = Field(default=True, description="是否记录请求日志")

    # 任务配置
    max_concurrent_tasks: int = Field(default=5, description="最大并发任务数")
    task_timeout: int = Field(default=3600, description="任务超时时间(秒)")

    class Config:
        env_prefix = "API_"
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# 全局配置实例
config = APIConfig()


def get_config() -> APIConfig:
    """获取配置实例"""
    return config
