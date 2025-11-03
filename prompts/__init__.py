# Prompts module for storing and managing prompt templates
"""
Prompts模块用于存储和管理各种提示词模板。
"""

from .source_prompts import (
    build_source_analysis_prompt,
    build_source_analysis_with_codeql_prompt,
    build_source_analysis_with_sink_prompt,
    get_language_specific_focus,
    get_language_specific_instructions,
)

from .sink_prompt_manager import (
    SinkPromptManager,
    prompt_manager,
    build_sink_prompt,
    get_supported_languages,
)

__all__ = [
    "build_source_analysis_prompt",
    "build_source_analysis_with_codeql_prompt",
    "build_source_analysis_with_sink_prompt",
    "get_language_specific_focus",
    "get_language_specific_instructions",
    "SinkPromptManager",
    "prompt_manager",
    "build_sink_prompt",
    "get_supported_languages",
]