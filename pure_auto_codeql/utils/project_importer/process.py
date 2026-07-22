"""Subprocess execution helpers with logging and timeout support."""

from __future__ import annotations

import logging
import os
import shlex
import subprocess
from pathlib import Path
from typing import List, Optional

logger = logging.getLogger(__name__)


def _run_process_with_timeout(cmd: List[str], *, cwd: Optional[Path], log_path: Optional[Path], timeout: int) -> None:
    """运行进程并实时输出到控制台和日志文件（带超时）"""
    display = " ".join(cmd)
    logger.info("Running command with timeout=%ds: %s (cwd=%s)", timeout, display, cwd or os.getcwd())

    # 打开日志文件
    log_file_handle = None
    if log_path:
        log_file_handle = open(log_path, "a", encoding="utf-8")
        log_file_handle.write(f"$ {display}\n")
        log_file_handle.flush()

    try:
        # 使用 Popen 实时输出
        process = subprocess.Popen(
            cmd,
            cwd=str(cwd) if cwd else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,  # 合并 stderr 到 stdout
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,  # 行缓冲
        )

        # 实时读取并输出
        if process.stdout:
            for line in process.stdout:
                # 打印到控制台
                print(line, end="", flush=True)
                # 写入日志文件
                if log_file_handle:
                    log_file_handle.write(line)
                    log_file_handle.flush()

        # 等待进程完成（带超时）
        try:
            return_code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            raise RuntimeError(f"Command timed out after {timeout} seconds: {display}")

        if return_code != 0:
            error_msg = f"Command failed with exit code {return_code}: {display}"
            logger.error(error_msg)
            raise RuntimeError(error_msg)

    finally:
        if log_file_handle:
            log_file_handle.close()

def _run_process(cmd: List[str], *, cwd: Optional[Path], log_path: Optional[Path]) -> None:
    """运行进程并实时输出到控制台和日志文件"""
    display = " ".join(cmd)
    logger.info("Running command: %s (cwd=%s)", display, cwd or os.getcwd())

    # 打开日志文件
    log_file_handle = None
    if log_path:
        log_file_handle = open(log_path, "a", encoding="utf-8")
        log_file_handle.write(f"$ {display}\n")
        log_file_handle.flush()

    try:
        # 使用 Popen 实时输出
        process = subprocess.Popen(
            cmd,
            cwd=str(cwd) if cwd else None,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,  # 合并 stderr 到 stdout
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,  # 行缓冲
        )

        # 实时读取并输出
        if process.stdout:
            for line in process.stdout:
                # 打印到控制台
                print(line, end="", flush=True)
                # 写入日志文件
                if log_file_handle:
                    log_file_handle.write(line)
                    log_file_handle.flush()

        # 等待进程完成
        return_code = process.wait()

        if return_code != 0:
            error_msg = f"Command failed with exit code {return_code}: {display}"
            logger.error(error_msg)
            raise RuntimeError(error_msg)

    finally:
        if log_file_handle:
            log_file_handle.close()

def _format_command(parts: List[str]) -> str:
    if os.name == "nt":
        formatted = []
        for part in parts:
            if " " in part:
                formatted.append(f'"{part}"')
            else:
                formatted.append(part)
        return " ".join(formatted)
    return " ".join(shlex.quote(part) for part in parts)
