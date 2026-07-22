"""
Prompt manager for unified sink path agent.

This module provides a centralized way to manage and retrieve
prompt templates for different programming languages.
"""


from .cpp_sink_prompt import build_cpp_sink_prompt
from .java_sink_prompt import build_java_sink_prompt
from .python_sink_prompt import build_python_sink_prompt


class SinkPromptManager:
    """管理sink分析提示词的类。"""

    def __init__(self):
        self.prompt_builders = {
            "java": build_java_sink_prompt,
            "python": build_python_sink_prompt,
            "cpp": build_cpp_sink_prompt,
            "c++": build_cpp_sink_prompt,  # 支持c++作为cpp的别名
        }

    def get_prompt_builder(self, language: str):
        """获取指定语言的提示词构建器。"""
        return self.prompt_builders.get(language.lower())

    def build_prompt(self, language: str, cve_analysis: str, source_path: str, diff_path: str = "") -> str:
        """构建指定语言的提示词。"""
        builder = self.get_prompt_builder(language)
        if builder:
            # 将Path对象转换为字符串
            diff_path_str = str(diff_path) if diff_path else ""
            return builder(cve_analysis, source_path, diff_path_str)
        else:
            return f"未知语言: {language}"

    def get_supported_languages(self) -> list:
        """获取支持的语言列表。"""
        return list(self.prompt_builders.keys())


# 创建全局实例
prompt_manager = SinkPromptManager()


def build_sink_prompt(language: str, cve_analysis: str, source_path: str, diff_path: str = "") -> str:
    """构建sink分析提示词的便捷函数。"""
    return prompt_manager.build_prompt(language, cve_analysis, source_path, diff_path)


def get_supported_languages() -> list:
    """获取支持的语言列表。"""
    return prompt_manager.get_supported_languages()
