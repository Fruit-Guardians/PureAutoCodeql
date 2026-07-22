"""CodeQL LSP语法检查服务

提供CodeQL语言服务器协议的封装，用于语法检查和验证。
"""

import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict

import requests

from pure_auto_codeql.utils.logger import get_logger

logger = get_logger(__name__)


class CodeQLLSPService:
    """CodeQL LSP语法检查服务管理器。"""

    def __init__(self, pack_root: Path = None, query_file: Path = None):
        self.pack_root = pack_root
        self.query_file = query_file
        self.process = None
        self.port = 8766
        self.base_url = f"http://127.0.0.1:{self.port}"
        self.init_timeout = 60  # 增加初始化超时时间到60秒

    def start(self) -> bool:
        """启动LSP服务。"""


        try:
            # 启动LSP服务
            cmd = [
                sys.executable, "-m", "tools.lsp_codeql",
                "--pack-root", str(self.pack_root),
                "--port", str(self.port),
                "--query-file", str(self.query_file),
                "--quiet-logs"
            ]

            logger.debug(f"启动LSP服务命令: {' '.join(cmd)}")

            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            # 等待服务启动
            logger.info(f"等待LSP服务启动... (超时时间: {self.init_timeout}秒)")
            for i in range(self.init_timeout):  # 最多等待指定秒数
                try:
                    response = requests.get(f"{self.base_url}/health", timeout=1)
                    if response.status_code == 200:
                        logger.info(f"LSP服务在第{i+1}秒启动成功")
                        return True
                    else:
                        logger.debug(f"服务返回状态码 {response.status_code}，继续等待...")
                except Exception:
                    if i % 5 == 0:  # 每5秒显示一次等待状态
                        logger.debug(f"等待LSP服务启动... ({i+1}/{self.init_timeout}秒)")
                time.sleep(1)

            # 如果服务启动超时，检查进程输出
            logger.error("LSP服务启动超时")
            if self.process and self.process.poll() is not None:
                stdout, stderr = self.process.communicate()
                logger.error(f"LSP进程退出码: {self.process.returncode}")
                if stdout:
                    logger.error(f"LSP进程标准输出: {stdout}")
                if stderr:
                    logger.error(f"LSP进程错误输出: {stderr}")

            return False

        except Exception as e:
            logger.exception(f"LSP服务启动失败: {e}")
            return False

    def check_syntax(self, codeql_code: str) -> Dict[str, Any]:
        """检查CodeQL代码语法。"""
        try:
            response = requests.post(
                f"{self.base_url}/check",
                json={"code": codeql_code},
                timeout=30
            )



            if response.status_code == 200:
                return response.json()
            else:
                return {"error": f"HTTP {response.status_code}"}

        except Exception as e:
            return {"error": str(e)}

    def stop(self):
        """停止LSP服务。"""
        if self.process:
            try:
                # 发送关闭请求
                requests.post(f"{self.base_url}/shutdown", timeout=5)
            except Exception:
                logger.debug("发送 LSP shutdown 请求失败", exc_info=True)

            # 等待进程结束
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                # 如果进程没有正常结束，强制终止
                try:
                    self.process.terminate()
                    self.process.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait(timeout=1)

            # 确保进程被清理
            if self.process.poll() is None:
                self.process.kill()

            self.process = None

            # 强制清理可能的端口占用
            import socket
            try:
                # 尝试绑定端口来释放可能的TIME_WAIT状态
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                s.bind(('127.0.0.1', self.port))
                s.close()
            except Exception:
                logger.debug("释放 LSP 端口 %s 失败", self.port, exc_info=True)
