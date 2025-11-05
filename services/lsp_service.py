"""CodeQL LSP语法检查服务

提供CodeQL语言服务器协议的封装，用于语法检查和验证。
"""

import requests
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict


class CodeQLLSPService:
    """CodeQL LSP语法检查服务管理器。"""

    def __init__(self, pack_root: str = None):
        self.pack_root = pack_root or str(Path.cwd())
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
                "--pack-root", self.pack_root,
                "--port", str(self.port),
                "--quiet-logs"
            ]

            print(f"启动LSP服务命令: {' '.join(cmd)}")
            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            # 等待服务启动
            print(f"等待LSP服务启动... (超时时间: {self.init_timeout}秒)")
            for i in range(self.init_timeout):  # 最多等待指定秒数
                try:
                    response = requests.get(f"{self.base_url}/health", timeout=1)
                    if response.status_code == 200:
                        print(f"✅ LSP服务在第{i+1}秒启动成功")
                        return True
                except Exception as e:
                    if i % 5 == 0:  # 每5秒显示一次等待状态
                        print(f"等待LSP服务启动... ({i+1}/{self.init_timeout}秒)")
                time.sleep(1)

            # 如果服务启动超时，检查进程输出
            print("❌ LSP服务启动超时")
            if self.process and self.process.poll() is not None:
                stdout, stderr = self.process.communicate()
                print(f"LSP进程退出码: {self.process.returncode}")
                if stdout:
                    print(f"LSP进程标准输出: {stdout}")
                if stderr:
                    print(f"LSP进程错误输出: {stderr}")

            return False

        except Exception as e:
            print(f"❌ LSP服务启动失败: {e}")
            import traceback
            traceback.print_exc()
            return False

    def check_syntax(self, codeql_code: str) -> Dict[str, Any]:
        """检查CodeQL代码语法。"""
        try:
            response = requests.post(
                f"{self.base_url}/check",
                json={"code": codeql_code},
                timeout=10
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
            except:
                pass

            # 等待进程结束
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.terminate()

            self.process = None