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

from .path_analysis_prompts import (
    build_path_analysis_prompt,
    build_batch_path_analysis_prompt,
)

from .verification_prompt_manager import (
    VerificationPromptManager,
    verification_prompt_manager,
    load_verification_template,
    build_verification_requirement,
    get_supported_verification_languages,
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
    "build_path_analysis_prompt",
    "build_batch_path_analysis_prompt",
    "VerificationPromptManager",
    "verification_prompt_manager",
    "load_verification_template",
    "build_verification_requirement",
    "get_supported_verification_languages",
]