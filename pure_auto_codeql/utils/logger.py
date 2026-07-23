"""统一日志配置模块

提供项目统一的日志配置和管理。
"""

import logging
import re
import sys
from pathlib import Path
from typing import Optional

# 全局日志配置标志，确保只配置一次
_logging_configured = False

ICONS = {
    "info": "󰋼",
    "success": "󰄬",
    "warning": "󰀪",
    "error": "󰅙",
    "progress": "󰑓",
    "result": "󰈙",
}

_DECORATIVE_PREFIX = re.compile(
    r"^\s*(?:"
    r"✅|❌|⚠️?|ℹ️?|🔄|📋|🚀|🎯|💭|📄|🤖|📦|📁|📂|🧱|"
    r"🗂️?|💻|📣|🔧|🛠️?|🔗|🔍|🎉|⏱️?|⏭️?|⚙️?|📜|🚚|"
    r"󰄬|󰅙|󰀪|󰋼|󰑓|󰈙|󰜎|󰓾|󰧑|󰚩|󰏗|󰉋|󰡁|󰉖|"
    r"󰍹|󰍡|󰢛|󰌷|󰍉|󰥔|󰒭|󰒓|󰓟"
    r")\s*"
)


def clean_terminal_message(message: str) -> str:
    """Remove legacy decorative emoji prefixes from terminal messages."""
    cleaned = str(message)
    while _DECORATIVE_PREFIX.match(cleaned):
        cleaned = _DECORATIVE_PREFIX.sub("", cleaned, count=1)
    return cleaned


class ConsoleFormatter(logging.Formatter):
    """Compact formatter for interactive terminal logs."""

    LEVEL_ICONS = {
        logging.DEBUG: "󰃤",
        logging.INFO: ICONS["info"],
        logging.WARNING: ICONS["warning"],
        logging.ERROR: ICONS["error"],
        logging.CRITICAL: ICONS["error"],
    }

    def format(self, record: logging.LogRecord) -> str:
        rendered = clean_terminal_message(record.getMessage())
        marker = self.LEVEL_ICONS.get(record.levelno, ICONS["info"])
        if record.exc_info:
            rendered = f"{rendered}\n{self.formatException(record.exc_info)}"
        return f"{marker}  {rendered}"


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

    # 文件日志保留完整上下文；终端使用紧凑格式。
    file_format = (
        format_string
        or "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )

    # 转换日志级别字符串为logging常量
    numeric_level = getattr(logging, level.upper(), logging.INFO)

    # 配置根日志记录器
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(ConsoleFormatter())
    handlers = [console_handler]

    # 如果指定了日志文件，添加文件处理器
    if log_file:
        log_file = Path(log_file)
        log_file.parent.mkdir(parents=True, exist_ok=True)
        file_handler = logging.FileHandler(log_file, encoding="utf-8")
        handlers.append(file_handler)

    # 配置日志格式
    file_formatter = logging.Formatter(file_format, datefmt="%Y-%m-%d %H:%M:%S")

    # 配置根日志记录器
    logging.basicConfig(
        level=numeric_level,
        handlers=handlers,
        force=force
    )

    if log_file:
        file_handler.setFormatter(file_formatter)

    # 第三方客户端的逐请求 INFO 会淹没分析步骤。
    for noisy_logger in (
        "httpx",
        "httpcore",
        "openai",
        "asyncio",
        "mcp",
        "urllib3",
    ):
        logging.getLogger(noisy_logger).setLevel(logging.WARNING)

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
    print(f"{ICONS['info']}  {clean_terminal_message(message)}")


def print_user_success(message: str) -> None:
    """打印成功信息"""
    print(f"{ICONS['success']}  {clean_terminal_message(message)}")


def print_user_warning(message: str) -> None:
    """打印警告信息"""
    print(f"{ICONS['warning']}  {clean_terminal_message(message)}")


def print_user_error(message: str) -> None:
    """打印错误信息"""
    print(f"{ICONS['error']}  {clean_terminal_message(message)}")


def print_user_progress(message: str) -> None:
    """打印进度信息"""
    print(f"{ICONS['progress']}  {clean_terminal_message(message)}")


def print_user_result(message: str) -> None:
    """打印结果信息"""
    print(f"{ICONS['result']}  {clean_terminal_message(message)}")


def print_user_section(title: str) -> None:
    """打印章节标题"""
    clean_title = clean_terminal_message(title)
    rule_width = max(8, 58 - len(clean_title))
    print(f"\n── {clean_title} {'─' * rule_width}")
