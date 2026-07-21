"""Legacy config package.

Implementation lives under ``pure_auto_codeql.config``. User secrets remain at
repository-root ``config/keys.toml`` / ``config/keys.example.toml``.
"""

from pure_auto_codeql.config import (
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
