"""
Prompt manager for sink/source verification agents.

This module provides a centralized way to manage and retrieve
verification prompt templates for different programming languages.
"""

from pathlib import Path
from typing import Optional, Dict


class VerificationPromptManager:
    """管理 Sink/Source 验证提示词的类。"""

    def __init__(self):
        self.template_dir = Path(__file__).parent / "verification"
        
        # 语言到模板文件的映射
        self.sink_templates = {
            "java": "sink_verification_java.md",
            "python": "sink_verification_python.md",
            "cpp": "sink_verification_cpp.md",
            "c++": "sink_verification_cpp.md",  # 支持 c++ 作为 cpp 的别名
            "c": "sink_verification_cpp.md",    # C 语言使用 C++ 模板
        }
        
        self.source_templates = {
            "java": "source_verification_java.md",
            "python": "source_verification_python.md",
            "cpp": "source_verification_cpp.md",
            "c++": "source_verification_cpp.md",  # 支持 c++ 作为 cpp 的别名
            "c": "source_verification_cpp.md",    # C 语言使用 C++ 模板
        }

    def load_verification_template(self, language: str, verification_type: str) -> Optional[str]:
        """
        加载指定语言和类型的验证模板。
        
        Args:
            language: 编程语言（java/python/cpp/c++/c）
            verification_type: 验证类型（sink/source）
        
        Returns:
            模板内容字符串，如果模板不存在则返回 None
        """
        language = language.lower()
        verification_type = verification_type.lower()
        
        # 选择对应的模板映射
        if verification_type == "sink":
            templates = self.sink_templates
        elif verification_type == "source":
            templates = self.source_templates
        else:
            raise ValueError(f"未知的验证类型: {verification_type}，仅支持 'sink' 或 'source'")
        
        # 获取模板文件名
        template_file = templates.get(language)
        if not template_file:
            return None
        
        # 读取模板文件
        template_path = self.template_dir / template_file
        if not template_path.exists():
            raise FileNotFoundError(f"模板文件不存在: {template_path}")
        
        with open(template_path, "r", encoding="utf-8") as f:
            return f.read()

    def build_verification_requirement(self, sink_or_source_info: Dict[str, str], verification_type: str) -> str:
        """
        根据 Sink/Source 信息构建验证需求的自然语言描述。
        
        Args:
            sink_or_source_info: Sink/Source 信息字典，包含 function, file_path, line 等字段
            verification_type: 验证类型（sink/source）
        
        Returns:
            自然语言需求描述
        """
        function_name = sink_or_source_info.get("function", "unknown")
        file_path = sink_or_source_info.get("file_path", "unknown")
        line = sink_or_source_info.get("line", "")
        
        if verification_type.lower() == "sink":
            if line:
                return f"验证函数 {function_name} 在文件 {file_path} 的第 {line} 行是否存在作为 Sink 点"
            else:
                return f"验证函数 {function_name} 在文件 {file_path} 中是否存在作为 Sink 点"
        elif verification_type.lower() == "source":
            if line:
                return f"验证函数 {function_name} 在文件 {file_path} 的第 {line} 行是否存在作为 Source 点"
            else:
                return f"验证函数 {function_name} 在文件 {file_path} 中是否存在作为 Source 点"
        else:
            raise ValueError(f"未知的验证类型: {verification_type}")

    def get_supported_languages(self) -> list:
        """获取支持的语言列表。"""
        return list(set(self.sink_templates.keys()) | set(self.source_templates.keys()))


# 创建全局实例
verification_prompt_manager = VerificationPromptManager()


def load_verification_template(language: str, verification_type: str) -> Optional[str]:
    """
    加载验证模板的便捷函数。
    
    Args:
        language: 编程语言（java/python/cpp/c++/c）
        verification_type: 验证类型（sink/source）
    
    Returns:
        模板内容字符串，如果模板不存在则返回 None
    """
    return verification_prompt_manager.load_verification_template(language, verification_type)


def build_verification_requirement(sink_or_source_info: Dict[str, str], verification_type: str) -> str:
    """
    构建验证需求的便捷函数。
    
    Args:
        sink_or_source_info: Sink/Source 信息字典
        verification_type: 验证类型（sink/source）
    
    Returns:
        自然语言需求描述
    """
    return verification_prompt_manager.build_verification_requirement(sink_or_source_info, verification_type)


def get_supported_verification_languages() -> list:
    """获取支持的语言列表。"""
    return verification_prompt_manager.get_supported_languages()
