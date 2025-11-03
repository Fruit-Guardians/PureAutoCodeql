"""
Prompt manager for unified source analysis agent.

This module provides a centralized way to manage and retrieve
prompt templates for different programming languages in source analysis.
"""

from pathlib import Path
from typing import Optional, List

from .source_prompts import (
    build_source_analysis_prompt,
    build_source_analysis_with_codeql_prompt
)


class SourcePromptManager:
    """管理source分析提示词的类。"""
    
    def __init__(self):
        self.prompt_builders = {
            "java": build_source_analysis_prompt,
            "python": build_source_analysis_prompt,
            "cpp": build_source_analysis_prompt,
            "c++": build_source_analysis_prompt,  # 支持c++作为cpp的别名
        }
        
        self.codeql_prompt_builders = {
            "java": build_source_analysis_with_codeql_prompt,
            "python": build_source_analysis_with_codeql_prompt,
            "cpp": build_source_analysis_with_codeql_prompt,
            "c++": build_source_analysis_with_codeql_prompt,  # 支持c++作为cpp的别名
        }
    
    def get_prompt_builder(self, language: str):
        """获取指定语言的普通提示词构建器。"""
        return self.prompt_builders.get(language.lower())
    
    def get_codeql_prompt_builder(self, language: str):
        """获取指定语言的CodeQL提示词构建器。"""
        return self.codeql_prompt_builders.get(language.lower())
    
    def build_prompt(self, language: str, cve_analysis: str, source_paths: List[str], 
                    current_dir: str = ".", file_extension: str = "ext") -> str:
        """构建指定语言的普通source分析提示词。"""
        builder = self.get_prompt_builder(language)
        if builder:
            return builder(language, cve_analysis, source_paths, current_dir, file_extension)
        else:
            return f"未知语言: {language}"
    
    def build_prompt_with_codeql(self, language: str, cve_analysis: str, source_paths: List[str],
                                current_dir: str = ".", codeql_query: str = "", 
                                query_results: str = "", file_extension: str = "ext") -> str:
        """构建包含CodeQL查询结果的source分析提示词。"""
        builder = self.get_codeql_prompt_builder(language)
        if builder:
            return builder(language, cve_analysis, source_paths, current_dir, 
                         codeql_query, query_results, file_extension)
        else:
            return f"未知语言: {language}"
    
    def get_supported_languages(self) -> list:
        """获取支持的语言列表。"""
        return list(self.prompt_builders.keys())


# 创建全局实例
source_prompt_manager = SourcePromptManager()


def build_source_prompt(language: str, cve_analysis: str, source_paths: List[str], 
                       current_dir: str = ".", file_extension: str = "ext") -> str:
    """构建source分析提示词的便捷函数。"""
    return source_prompt_manager.build_prompt(language, cve_analysis, source_paths, 
                                            current_dir, file_extension)


def build_source_prompt_with_codeql(language: str, cve_analysis: str, source_paths: List[str],
                                   current_dir: str = ".", codeql_query: str = "", 
                                   query_results: str = "", file_extension: str = "ext") -> str:
    """构建包含CodeQL查询结果的source分析提示词的便捷函数。"""
    return source_prompt_manager.build_prompt_with_codeql(language, cve_analysis, source_paths,
                                                         current_dir, codeql_query, 
                                                         query_results, file_extension)


def get_supported_source_languages() -> list:
    """获取支持的语言列表。"""
    return source_prompt_manager.get_supported_languages()