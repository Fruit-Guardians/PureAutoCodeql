"""
向后兼容模块

包含旧版配置系统的函数，保持向后兼容性
"""

import os
from typing import Optional
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

from .core import LLMRole, LLMProvider, LLMConfig, ProviderRegistry


# ============================================================================
# 旧版兼容函数
# ============================================================================

def _default_base_url(provider: LLMProvider) -> str:
    """按服务商提供 OpenAI 兼容的默认 base_url"""
    mapping = {
        LLMProvider.DEEPSEEK: "https://api.deepseek.com/v1",
        LLMProvider.SILICONFLOW: "https://api.siliconflow.cn/v1",
        LLMProvider.ZHIPU: "https://open.bigmodel.cn/api/paas/v4/",
        LLMProvider.KIMI: "https://api.moonshot.cn/v1",
        LLMProvider.GEMINI: "https://generativelanguage.googleapis.com/v1beta/openai",
    }
    return mapping.get(provider, "https://api.deepseek.com/v1")


def _read_env_provider() -> LLMProvider:
    """读取默认服务商"""
    raw = (os.getenv("LLM_PROVIDER") or "deepseek").strip().lower()
    for p in LLMProvider:
        if p.value == raw:
            return p
    return LLMProvider.DEEPSEEK


def _read_env_api_key(provider: LLMProvider) -> str:
    """按服务商读取对应的 API Key 环境变量"""
    mapping = {
        LLMProvider.DEEPSEEK: ["DEEPSEEK_API_KEY"],
        LLMProvider.SILICONFLOW: ["SILICONFLOW_API_KEY", "SF_API_KEY"],
        LLMProvider.ZHIPU: ["ZHIPU_API_KEY", "GLM_API_KEY"],
        LLMProvider.KIMI: ["KIMI_API_KEY", "MOONSHOT_API_KEY"],
        LLMProvider.GEMINI: ["GEMINI_API_KEY", "GOOGLE_API_KEY"],
    }
    
    for env_name in mapping.get(provider, []):
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    
    for env_name in ["OPENAI_API_KEY", "API_KEY"]:
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    
    return ""


def _read_env_base_url(provider: LLMProvider) -> str:
    """按服务商读取 base_url"""
    mapping = {
        LLMProvider.DEEPSEEK: ["DEEPSEEK_BASE_URL"],
        LLMProvider.SILICONFLOW: ["SILICONFLOW_BASE_URL", "SF_BASE_URL"],
        LLMProvider.ZHIPU: ["ZHIPU_BASE_URL", "GLM_BASE_URL"],
        LLMProvider.KIMI: ["KIMI_BASE_URL", "MOONSHOT_BASE_URL"],
        LLMProvider.GEMINI: ["GEMINI_BASE_URL", "GOOGLE_BASE_URL"],
    }
    
    for env_name in mapping.get(provider, []):
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    
    for env_name in ["OPENAI_BASE_URL", "BASE_URL"]:
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    
    return _default_base_url(provider)


def _read_env_models() -> tuple[Optional[str], Optional[str]]:
    """返回 (think_model, chat_model) 的环境覆盖"""
    return os.getenv("THINK_MODEL"), os.getenv("CHAT_MODEL")


def _is_reachable(base_url: str, timeout: float = 2.0) -> bool:
    """粗略可达性检查"""
    try:
        req = Request(base_url, method="GET")
        with urlopen(req, timeout=timeout) as resp:  # noqa: S310
            return True
    except HTTPError:
        return True
    except (URLError, Exception):
        return False


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
    print("📋 硅基流动 (SiliconFlow) 可用模型列表:")
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

