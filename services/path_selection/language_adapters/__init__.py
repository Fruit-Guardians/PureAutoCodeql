"""语言适配器

为不同编程语言提供特定的分析逻辑
"""

from .base import LanguageAdapter
from .python_adapter import PythonAdapter
from .java_adapter import JavaAdapter
from .c_adapter import CAdapter


def get_language_adapter(language: str) -> LanguageAdapter:
    """
    获取指定语言的适配器
    
    Args:
        language: 编程语言 (python/java/c)
    
    Returns:
        对应的语言适配器
    """
    language = language.lower()
    
    adapters = {
        "python": PythonAdapter,
        "java": JavaAdapter,
        "c": CAdapter,
        "cpp": CAdapter,  # C++ 使用 C 适配器
        "c++": CAdapter,
    }
    
    adapter_class = adapters.get(language)
    if not adapter_class:
        raise ValueError(f"不支持的语言: {language}")
    
    return adapter_class()


__all__ = [
    "LanguageAdapter",
    "PythonAdapter",
    "JavaAdapter",
    "CAdapter",
    "get_language_adapter"
]

