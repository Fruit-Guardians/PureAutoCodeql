"""流水线的 LLM 配置解析。"""

from typing import Any

from ..context import AnalysisContext


def _get_llm_config_from_context(context: AnalysisContext, role) -> Any:
    """从上下文中获取LLM配置，支持配置中的模型、API Key和Base URL"""
    config = getattr(context, '_config', None)
    if not config:
        from pure_auto_codeql.configuration import LLMRole, get_resilient_llm_config
        return get_resilient_llm_config(role)

    from pure_auto_codeql.configuration import LLMRole, get_llm_config
    # 从配置中获取参数
    provider = config.llm_provider
    model_name = config.think_model if role == LLMRole.THINK else config.chat_model
    api_key = config.api_key
    base_url = config.base_url

    return get_llm_config(
        role,
        provider_name=provider,
        model_name=model_name,
        api_key=api_key,
        base_url=base_url
    )
