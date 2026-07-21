"""Canonical configuration import surface for PureAutoCodeQL.

This module is the preferred namespace for application code that needs LLM
configuration helpers. The root-level `config.py` and `config/` package remain
available as legacy compatibility surfaces. User keys stay at
`config/keys.toml` under the repository root.
"""

from pure_auto_codeql.config import (  # noqa: F401
    CHAT_CONFIG,
    THINK_CONFIG,
    LLMConfig,
    LLMProvider,
    LLMRole,
    ProviderConfig,
    ProviderRegistry,
    display_all_providers,
    display_provider_detail,
    display_providers_status,
    display_validation_result,
    get_chat_config,
    get_llm_config,
    get_llm_config_by_provider,
    get_resilient_llm_config,
    get_siliconflow_models,
    get_think_config,
    list_available_providers,
    list_siliconflow_models,
    validate_provider,
)

__all__ = [
    "CHAT_CONFIG",
    "THINK_CONFIG",
    "LLMConfig",
    "LLMProvider",
    "LLMRole",
    "ProviderConfig",
    "ProviderRegistry",
    "display_all_providers",
    "display_provider_detail",
    "display_providers_status",
    "display_validation_result",
    "get_chat_config",
    "get_llm_config",
    "get_llm_config_by_provider",
    "get_resilient_llm_config",
    "get_siliconflow_models",
    "get_think_config",
    "list_available_providers",
    "list_siliconflow_models",
    "validate_provider",
]
