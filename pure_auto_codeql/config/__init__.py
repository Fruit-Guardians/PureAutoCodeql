"""
PureAutoCodeQL LLM 配置系统

统一的配置导出模块，保持向后兼容性
"""

# 核心数据类和枚举
from .core import (
    LLMRole,
    LLMProvider,
    ProviderConfig,
    LLMConfig,
    ProviderRegistry,
    get_llm_config,
    get_think_config,
    get_chat_config,
    THINK_CONFIG,
    CHAT_CONFIG,
)

# 展示函数
from .display import (
    display_providers_status,
    display_provider_detail,
    display_all_providers,
    validate_provider,
    display_validation_result,
)

# 向后兼容函数（精简）
from .legacy import (
    list_available_providers,
    get_llm_config_by_provider,
    get_resilient_llm_config,
    list_siliconflow_models,
    get_siliconflow_models,
)

# 导出列表
__all__ = [
    # 核心类和枚举
    "LLMRole",
    "LLMProvider",
    "ProviderConfig",
    "LLMConfig",
    "ProviderRegistry",
    
    # 核心配置函数
    "get_llm_config",
    "get_think_config",
    "get_chat_config",
    
    # 全局配置实例
    "THINK_CONFIG",
    "CHAT_CONFIG",
    
    # 展示函数
    "display_providers_status",
    "display_provider_detail",
    "display_all_providers",
    "validate_provider",
    "display_validation_result",
    
    # 向后兼容函数
    "list_available_providers",
    "get_llm_config_by_provider",
    "get_resilient_llm_config",
    "list_siliconflow_models",
    "get_siliconflow_models",
]

