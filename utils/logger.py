"""统一日志配置模块

提供项目统一的日志配置和管理。
"""

import logging
import sys
from pathlib import Path
from typing import Optional


# 全局日志配置标志，确保只配置一次
_logging_configured = False


def setup_logging(
    level: str = "INFO",
    format_string: Optional[str] = None,
    log_file: Optional[Path] = None,
    force: bool = False
) -> None:
    """配置全局日志系统。

    Args:
        level: 日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        format_string: 自定义日志格式字符串，如果为None则使用默认格式
        log_file: 可选的文件路径，如果指定则同时输出到文件
        force: 是否强制重新配置（即使已经配置过）
    """
    global _logging_configured

    if _logging_configured and not force:
        return

    # 默认格式：时间戳 - 模块名 - 级别 - 消息
    if format_string is None:
        format_string = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"

    # 转换日志级别字符串为logging常量
    numeric_level = getattr(logging, level.upper(), logging.INFO)

    # 配置根日志记录器
    handlers = [logging.StreamHandler(sys.stdout)]

    # 如果指定了日志文件，添加文件处理器
    if log_file:
        log_file = Path(log_file)
        log_file.parent.mkdir(parents=True, exist_ok=True)
        file_handler = logging.FileHandler(log_file, encoding="utf-8")
        handlers.append(file_handler)

    # 配置日志格式
    formatter = logging.Formatter(format_string, datefmt="%Y-%m-%d %H:%M:%S")

    # 配置根日志记录器
    logging.basicConfig(
        level=numeric_level,
        format=format_string,
        handlers=handlers,
        force=force
    )

    # 为所有处理器设置格式
    for handler in handlers:
        handler.setFormatter(formatter)

    _logging_configured = True


def get_logger(name: Optional[str] = None) -> logging.Logger:
    """获取日志记录器。

    Args:
        name: 日志记录器名称，通常使用 __name__。如果为None，返回根日志记录器。

    Returns:
        配置好的日志记录器实例
    """
    # 确保日志系统已配置
    if not _logging_configured:
        setup_logging()

    if name is None:
        return logging.getLogger()
    return logging.getLogger(name)


# 用户交互输出工具函数（保留print但统一格式）
def print_user_info(message: str) -> None:
    """打印用户信息（INFO级别）"""
    print(f"ℹ️  {message}")


def print_user_success(message: str) -> None:
    """打印成功信息"""
    print(f"✅ {message}")


def print_user_warning(message: str) -> None:
    """打印警告信息"""
    print(f"⚠️  {message}")


def print_user_error(message: str) -> None:
    """打印错误信息"""
    print(f"❌ {message}")


def print_user_progress(message: str) -> None:
    """打印进度信息"""
    print(f"🔄 {message}")


def print_user_result(message: str) -> None:
    """打印结果信息"""
    print(f"📋 {message}")


def print_user_section(title: str) -> None:
    """打印章节标题"""
    print(f"\n{'=' * 60}")
    print(f"  {title}")
    print(f"{'=' * 60}\n")

