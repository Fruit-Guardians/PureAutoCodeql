"""Python语言适配器"""

from __future__ import annotations

import re
from typing import Any, Dict, List

from .base import LanguageAdapter


class PythonAdapter(LanguageAdapter):
    """Python语言适配器"""

    def __init__(self):
        self.dangerous_apis = [
            # 命令执行
            "exec", "eval", "compile", "__import__",
            "os.system", "os.popen", "subprocess.call", "subprocess.run",
            "subprocess.Popen", "commands.getoutput",

            # 文件操作
            "open", "file", "os.open", "io.open",

            # 网络请求
            "requests.get", "requests.post", "urllib.request.urlopen",
            "httpx.get", "httpx.post", "http.client.HTTPConnection",

            # SQL
            "execute", "executemany", "cursor.execute",

            # 序列化
            "pickle.loads", "pickle.load", "yaml.load",
            "marshal.loads", "shelve.open",

            # 模板
            "render_template_string", "jinja2.Template",
        ]

    def analyze_source_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析Python Source点"""

        description = location.get("description", "")

        # 识别Source类型
        source_type = "unknown"
        if any(kw in description.lower() for kw in ["request", "req"]):
            source_type = "http_request"
        elif any(kw in description.lower() for kw in ["param", "arg", "argument"]):
            source_type = "parameter"
        elif any(kw in description.lower() for kw in ["input", "stdin"]):
            source_type = "user_input"
        elif any(kw in description.lower() for kw in ["body", "data"]):
            source_type = "request_body"
        elif any(kw in description.lower() for kw in ["query", "form"]):
            source_type = "query_param"

        # 提取变量名
        variable_name = self._extract_variable_name(description)

        return {
            "type": source_type,
            "description": description,
            "variable": variable_name,
            "language": "python"
        }

    def analyze_sink_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析Python Sink点"""

        description = location.get("description", "")

        # 提取API调用
        api_calls = self.extract_api_calls(code_context)

        # 识别危险API
        dangerous_apis_found = [
            api for api in api_calls
            if any(dangerous in api for dangerous in self.dangerous_apis)
        ]

        # 识别Sink类型
        sink_type = "unknown"
        if any(kw in description.lower() for kw in ["exec", "eval", "system", "popen", "subprocess"]):
            sink_type = "command_execution"
        elif any(kw in description.lower() for kw in ["open", "file"]):
            sink_type = "file_operation"
        elif any(kw in description.lower() for kw in ["get", "post", "request", "urlopen", "httpx", "http"]):
            sink_type = "http_request"
        elif any(kw in description.lower() for kw in ["execute", "sql", "query"]):
            sink_type = "sql_execution"
        elif any(kw in description.lower() for kw in ["pickle", "yaml", "marshal"]):
            sink_type = "deserialization"
        elif any(kw in description.lower() for kw in ["render", "template"]):
            sink_type = "template_injection"

        # 从代码上下文中提取关键行
        key_line = self._extract_key_line(code_context, location.get("startLine", 0))

        return {
            "type": sink_type,
            "description": description,
            "dangerous_apis": dangerous_apis_found,
            "key_line": key_line,
            "language": "python"
        }

    def get_dangerous_apis(self) -> List[str]:
        """获取Python危险API列表"""
        return self.dangerous_apis

    def _extract_variable_name(self, description: str) -> str:
        """从描述中提取变量名"""
        # 匹配 "ControlFlowNode for variable_name" 模式
        match = re.search(r'for\s+(\w+)', description)
        if match:
            return match.group(1)
        return ""

    def _extract_key_line(self, code_context: str, line_number: int) -> str:
        """提取关键代码行"""
        if not code_context:
            return ""

        lines = code_context.split('\n')
        for line in lines:
            # 查找标记为关键行的代码（>>>开头）
            if line.strip().startswith('>>>'):
                return line.split('|', 1)[1].strip() if '|' in line else line

        return ""

