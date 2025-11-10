"""C/C++语言适配器"""

from __future__ import annotations

import re
from typing import Any, Dict, List

from .base import LanguageAdapter


class CAdapter(LanguageAdapter):
    """C/C++语言适配器"""
    
    def __init__(self):
        self.dangerous_apis = [
            # 命令执行
            "system", "popen", "execve", "execl", "execlp", "execle",
            "execv", "execvp", "execvpe",
            
            # 不安全的字符串操作
            "strcpy", "strcat", "sprintf", "vsprintf", "gets", "scanf",
            
            # 内存操作
            "memcpy", "memmove", "strncpy", "strncat",
            
            # 格式化字符串
            "printf", "fprintf", "snprintf", "vprintf", "vfprintf",
            
            # 文件操作
            "fopen", "open", "read", "write", "fread", "fwrite",
            
            # 网络操作
            "socket", "connect", "bind", "listen", "accept", "send", "recv",
            
            # 指针操作
            "malloc", "calloc", "realloc", "free",
        ]
    
    def analyze_source_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析C/C++ Source点"""
        
        description = location.get("description", "")
        
        # 识别Source类型
        source_type = "unknown"
        if any(kw in description.lower() for kw in ["argv", "argc"]):
            source_type = "command_line_argument"
        elif any(kw in description.lower() for kw in ["stdin", "gets", "scanf", "fgets"]):
            source_type = "user_input"
        elif any(kw in description.lower() for kw in ["read", "fread", "recv"]):
            source_type = "external_input"
        elif any(kw in description.lower() for kw in ["getenv", "environment"]):
            source_type = "environment_variable"
        elif any(kw in description.lower() for kw in ["socket", "network"]):
            source_type = "network_input"
        
        # 提取变量名
        variable_name = self._extract_variable_name(description, code_context)
        
        return {
            "type": source_type,
            "description": description,
            "variable": variable_name,
            "language": "c"
        }
    
    def analyze_sink_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析C/C++ Sink点"""
        
        description = location.get("description", "")
        
        # 提取API调用
        api_calls = self.extract_api_calls(code_context)
        
        # 识别危险API
        dangerous_apis_found = [
            api for api in api_calls
            if api in self.dangerous_apis
        ]
        
        # 识别Sink类型
        sink_type = "unknown"
        if any(kw in description.lower() for kw in ["system", "exec", "popen"]):
            sink_type = "command_execution"
        elif any(kw in description.lower() for kw in ["strcpy", "strcat", "sprintf", "gets"]):
            sink_type = "buffer_overflow"
        elif any(kw in description.lower() for kw in ["printf", "fprintf", "format"]):
            sink_type = "format_string"
        elif any(kw in description.lower() for kw in ["fopen", "open", "write", "fwrite"]):
            sink_type = "file_operation"
        elif any(kw in description.lower() for kw in ["memcpy", "memmove", "memory"]):
            sink_type = "memory_operation"
        elif any(kw in description.lower() for kw in ["socket", "connect", "send"]):
            sink_type = "network_operation"
        
        # 提取关键行
        key_line = self._extract_key_line(code_context, location.get("startLine", 0))
        
        return {
            "type": sink_type,
            "description": description,
            "dangerous_apis": dangerous_apis_found,
            "key_line": key_line,
            "language": "c"
        }
    
    def get_dangerous_apis(self) -> List[str]:
        """获取C/C++危险API列表"""
        return self.dangerous_apis
    
    def _extract_variable_name(self, description: str, code_context: str) -> str:
        """提取变量名"""
        # 从描述中提取
        match = re.search(r'for\s+(\w+)', description)
        if match:
            return match.group(1)
        
        # 从代码中提取（查找关键行的变量）
        if code_context:
            match = re.search(r'>>>\s*\d+\s*\|\s*.*?(\w+)\s*[=\(]', code_context)
            if match:
                return match.group(1)
        
        return ""
    
    def _extract_key_line(self, code_context: str, line_number: int) -> str:
        """提取关键代码行"""
        if not code_context:
            return ""
        
        lines = code_context.split('\n')
        for line in lines:
            if line.strip().startswith('>>>'):
                return line.split('|', 1)[1].strip() if '|' in line else line
        
        return ""

