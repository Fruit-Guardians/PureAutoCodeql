"""
向后兼容模块

包含旧版配置系统的函数，保持向后兼容性
"""

from .core import LLMConfig, LLMRole, ProviderRegistry

# ============================================================================
# 旧版兼容函数
# ============================================================================


def get_resilient_llm_config(role: LLMRole) -> LLMConfig:
    """返回具备自动切换能力的 LLM 配置（旧版函数，保留兼容）"""
    from .core import get_llm_config
    return get_llm_config(role, auto_fallback=True)


def get_llm_config_by_provider(provider_name: str, role: LLMRole) -> LLMConfig:
    """根据提供商名称和角色获取 LLM 配置（便捷函数）"""
    from .core import get_llm_config
    return get_llm_config(role, provider_name=provider_name)


def get_siliconflow_models() -> list[str]:
    """获取硅基流动可用的模型列表"""
    return [
        "deepseek-ai/DeepSeek-R1",
        "Pro/deepseek-ai/DeepSeek-V3.2-Exp",
        "MiniMaxAI/MiniMax-M2",
        "moonshotai/Kimi-K2-Instruct-0905",
        "Qwen/Qwen3-Coder-480B-A35B-Instruct",
    ]


def list_siliconflow_models() -> None:
    """列出硅基流动的所有可用模型"""
    models = get_siliconflow_models()
    print("\n" + "=" * 80)
    print("󰈙 硅基流动 (SiliconFlow) 可用模型列表:")
    print("=" * 80)
    for i, model in enumerate(models, 1):
        marker = "⭐" if "DeepSeek-R1" in model or "DeepSeek-V3.2" in model else "  "
        print(f"{marker} {i}. {model}")
    print("=" * 80 + "\n")


def list_available_providers() -> list[dict]:
    """列出所有可用的提供商及其信息（旧版函数，保留兼容）"""
    providers_info = []

    for provider in ProviderRegistry.list_all():
        emoji, status_text = provider.get_status()

        provider_info = {
            "name": provider.name,
            "display_name": provider.display_name,
            "think_model": provider.default_think_model,
            "chat_model": provider.default_chat_model,
            "base_url": provider.get_base_url(),
            "has_api_key": provider.is_configured(),
            "is_reachable": provider.is_reachable(),
            "status": f"{emoji} {status_text}",
            "is_builtin": provider.is_builtin,
        }

        if provider.available_models:
            provider_info["available_models"] = provider.available_models

        providers_info.append(provider_info)

    return providers_info

