"""Java语言适配器"""

from __future__ import annotations

import re
from typing import Any, Dict, List

from .base import LanguageAdapter


class JavaAdapter(LanguageAdapter):
    """Java语言适配器"""
    
    def __init__(self):
        self.dangerous_apis = [
            # 命令执行
            "Runtime.exec", "ProcessBuilder.start", "ProcessBuilder.command",
            
            # 反射
            "Class.forName", "Method.invoke", "Constructor.newInstance",
            "Class.newInstance",
            
            # 文件操作
            "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
            "File.createTempFile", "Files.write", "Files.copy",
            
            # 网络请求
            "URL.openConnection", "URLConnection.connect", "HttpURLConnection",
            "HttpClient.send", "OkHttpClient.newCall",
            
            # SQL
            "Statement.execute", "Statement.executeQuery", "PreparedStatement.execute",
            "Connection.prepareStatement", "EntityManager.createQuery",
            
            # 序列化
            "ObjectInputStream.readObject", "XMLDecoder.readObject",
            "Gson.fromJson", "Jackson.readValue",
            
            # JNDI
            "InitialContext.lookup", "Context.lookup",
            
            # LDAP
            "LdapContext.search", "DirContext.search",
            
            # XPath
            "XPath.evaluate", "XPathExpression.evaluate",
            
            # Script Engine
            "ScriptEngine.eval", "ScriptEngineManager.getEngineByName",
        ]
    
    def analyze_source_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析Java Source点"""
        
        description = location.get("description", "")
        
        # 识别Source类型
        source_type = "unknown"
        if any(kw in description.lower() for kw in ["request", "httprequest", "servletrequest"]):
            source_type = "http_request"
        elif any(kw in description.lower() for kw in ["parameter", "param", "getparameter"]):
            source_type = "request_parameter"
        elif any(kw in description.lower() for kw in ["header", "getheader"]):
            source_type = "http_header"
        elif any(kw in description.lower() for kw in ["body", "inputstream", "reader"]):
            source_type = "request_body"
        elif any(kw in description.lower() for kw in ["cookie", "getcookie"]):
            source_type = "cookie"
        elif any(kw in description.lower() for kw in ["pathvariable", "requestparam"]):
            source_type = "spring_annotation"
        
        # 提取方法名
        method_name = self._extract_method_name(code_context)
        
        return {
            "type": source_type,
            "description": description,
            "method": method_name,
            "language": "java"
        }
    
    def analyze_sink_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """分析Java Sink点"""
        
        description = location.get("description", "")
        
        # 提取API调用
        api_calls = self.extract_api_calls(code_context)
        
        # 识别危险API
        dangerous_apis_found = [
            api for api in api_calls
            if any(dangerous in api for dangerous in ["exec", "invoke", "lookup", "execute", "eval", "readObject"])
        ]
        
        # 识别Sink类型
        sink_type = "unknown"
        if any(kw in description.lower() for kw in ["runtime.exec", "processbuilder", "process"]):
            sink_type = "command_execution"
        elif any(kw in description.lower() for kw in ["invoke", "forname", "newinstance", "reflection"]):
            sink_type = "reflection"
        elif any(kw in description.lower() for kw in ["fileinputstream", "fileoutputstream", "file"]):
            sink_type = "file_operation"
        elif any(kw in description.lower() for kw in ["urlconnection", "httpclient", "url"]):
            sink_type = "http_request"
        elif any(kw in description.lower() for kw in ["execute", "executequery", "preparestatement", "sql"]):
            sink_type = "sql_execution"
        elif any(kw in description.lower() for kw in ["readobject", "deserialize"]):
            sink_type = "deserialization"
        elif any(kw in description.lower() for kw in ["lookup", "jndi", "ldap"]):
            sink_type = "jndi_injection"
        elif any(kw in description.lower() for kw in ["xpath", "evaluate"]):
            sink_type = "xpath_injection"
        elif any(kw in description.lower() for kw in ["scriptengine", "eval"]):
            sink_type = "script_injection"
        
        # 提取关键行
        key_line = self._extract_key_line(code_context, location.get("startLine", 0))
        
        return {
            "type": sink_type,
            "description": description,
            "dangerous_apis": dangerous_apis_found,
            "key_line": key_line,
            "language": "java"
        }
    
    def get_dangerous_apis(self) -> List[str]:
        """获取Java危险API列表"""
        return self.dangerous_apis
    
    def _extract_method_name(self, code_context: str) -> str:
        """提取方法名"""
        # 匹配Java方法调用：identifier.method()
        match = re.search(r'\.(\w+)\s*\(', code_context)
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


