import os
from dataclasses import dataclass
from typing import Optional
from enum import Enum
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError


class LLMRole(Enum):
    THINK = "think"  # 推理模型，用于 CodeQL 相关任务
    CHAT = "chat"    # 对话模型，用于一般分析任务


class LLMProvider(Enum):
    """支持的服务商（均为 OpenAI 兼容协议）。"""

    DEEPSEEK = "deepseek"
    SILICONFLOW = "siliconflow"  # 硅基流动
    ZHIPU = "zhipu"  # GLM（智谱）
    KIMI = "kimi"  # Kimi（月之暗面）
    GEMINI = "gemini"  # Google Gemini


@dataclass
class LLMConfig:
    model: str
    api_key: str
    base_url: str
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None
    max_retries: int = 3
    provider: Optional[str] = None  # 仅做记录，兼容调用方


def _default_base_url(provider: LLMProvider) -> str:
    """按服务商提供 OpenAI 兼容的默认 base_url。"""
    if provider == LLMProvider.DEEPSEEK:
        return "https://api.deepseek.com/v1"
    if provider == LLMProvider.SILICONFLOW:
        return "https://api.siliconflow.cn/v1"
    if provider == LLMProvider.ZHIPU:
        return "https://open.bigmodel.cn/api/paas/v4/"
    if provider == LLMProvider.KIMI:
        return "https://api.moonshot.cn/v1"
    if provider == LLMProvider.GEMINI:
        return "https://generativelanguage.googleapis.com/v1beta/openai"
    # 兜底：视为 DeepSeek
    return "https://api.deepseek.com/v1"


def _read_env_provider() -> LLMProvider:
    """读取默认服务商，默认 deepseek。通过 LLM_PROVIDER 指定：deepseek/siliconflow/zhipu/kimi/gemini"""
    raw = (os.getenv("LLM_PROVIDER") or "deepseek").strip().lower()
    for p in LLMProvider:
        if p.value == raw:
            return p
    return LLMProvider.DEEPSEEK


def _read_env_api_key(provider: LLMProvider) -> str:
    """按服务商读取对应的 API Key 环境变量，允许通用 OPENAI_API_KEY 兜底。"""
    # 优先专属变量
    mapping = {
        LLMProvider.DEEPSEEK: ["DEEPSEEK_API_KEY"],
        LLMProvider.SILICONFLOW: ["SILICONFLOW_API_KEY", "SF_API_KEY"],
        LLMProvider.ZHIPU: ["ZHIPU_API_KEY", "GLM_API_KEY"],
        LLMProvider.KIMI: ["KIMI_API_KEY", "MOONSHOT_API_KEY"],
        LLMProvider.GEMINI: ["GEMINI_API_KEY", "GOOGLE_API_KEY"],
    }
    # 通用兜底
    generic_keys = ["OPENAI_API_KEY", "API_KEY"]

    for env_name in mapping.get(provider, []):
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    for env_name in generic_keys:
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    # 开发环境：本地默认密钥（仅当未设置环境变量时启用）
    dev_defaults = {
        LLMProvider.SILICONFLOW: "sk-olcgfjcykujmbkdzxrqdfmsncygabvoxpxmmcpxwifwxneld",
        LLMProvider.DEEPSEEK: "sk-5f09a827bfeb4392acfae82fa32f973d",
        LLMProvider.ZHIPU: "42e801a9a6994a5cb002ed8568ac1379.xLirJ33dyqMIPDiy",
        LLMProvider.KIMI: "sk-8nbJZ4bYJCTjO7qZaGvSbxflPeTscsX1JaS0hJBfpCOVnHny",
        LLMProvider.GEMINI: "AIzaSyDS-Moyzuf1sUdAUsw9bkr1z_cZMVdyvpc",
    }
    return dev_defaults.get(provider, "")


def _read_env_base_url(provider: LLMProvider) -> str:
    """按服务商读取 base_url，支持通用 OPENAI_BASE_URL 覆盖。"""
    mapping = {
        LLMProvider.DEEPSEEK: ["DEEPSEEK_BASE_URL"],
        LLMProvider.SILICONFLOW: ["SILICONFLOW_BASE_URL", "SF_BASE_URL"],
        LLMProvider.ZHIPU: ["ZHIPU_BASE_URL", "GLM_BASE_URL"],
        LLMProvider.KIMI: ["KIMI_BASE_URL", "MOONSHOT_BASE_URL"],
        LLMProvider.GEMINI: ["GEMINI_BASE_URL", "GOOGLE_BASE_URL"],
    }
    generic_keys = ["OPENAI_BASE_URL", "BASE_URL"]

    for env_name in mapping.get(provider, []):
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    for env_name in generic_keys:
        if os.getenv(env_name):
            return os.getenv(env_name)  # type: ignore
    return _default_base_url(provider)


def _read_env_models() -> tuple[Optional[str], Optional[str]]:
    """返回 (think_model, chat_model) 的环境覆盖。"""
    return os.getenv("THINK_MODEL"), os.getenv("CHAT_MODEL")


def get_siliconflow_models() -> list[str]:
    """获取硅基流动可用的模型列表。
    
    Returns:
        模型名称列表
    """
    return [
        "deepseek-ai/DeepSeek-R1",  # 默认推理模型
        "Pro/deepseek-ai/DeepSeek-V3.2-Exp",  # 默认对话模型
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


def _get_env_config(role: LLMRole) -> tuple[str, str, str]:
    """综合环境变量与默认映射，返回 (api_key, base_url, model)。"""
    provider = _read_env_provider()
    api_key = _read_env_api_key(provider)
    base_url = _read_env_base_url(provider)
    env_think_model, env_chat_model = _read_env_models()

    # 每个服务商的合理默认模型（可被 THINK_MODEL/CHAT_MODEL 覆盖）
    defaults = {
        LLMProvider.DEEPSEEK: {
            "think": "deepseek-reasoner",
            "chat": "deepseek-chat",
        },
        LLMProvider.SILICONFLOW: {
            # 常用：DeepSeek 模型经硅基流动路由；用户可用 THINK_MODEL/CHAT_MODEL 覆盖
            "think": "deepseek-ai/DeepSeek-R1",
            "chat": "Pro/deepseek-ai/DeepSeek-V3.2-Exp",
        },
        LLMProvider.ZHIPU: {
            # GLM 系列：无"思考模型"，默认 think 与 chat 均使用 glm-4.6
            "think": "glm-4.6",
            "chat": "glm-4.6",
        },
        LLMProvider.KIMI: {
            "think": "kimi-k2-thinking",
            "chat": "kimi-k2-0905-preview",
        },
        LLMProvider.GEMINI: {
            "think": "gemini-2.5-pro",
            "chat": "gemini-2.5-pro",
        },
    }

    if role == LLMRole.THINK:
        model = env_think_model or defaults[provider]["think"]
    else:
        model = env_chat_model or defaults[provider]["chat"]

    return api_key, base_url, model


def _provider_priority(primary: LLMProvider) -> list[LLMProvider]:
    """将首选服务商置前，其余按固定顺序排在后面。"""
    ordered = [primary]
    for p in [LLMProvider.DEEPSEEK, LLMProvider.SILICONFLOW, LLMProvider.ZHIPU, LLMProvider.KIMI, LLMProvider.GEMINI]:
        if p not in ordered:
            ordered.append(p)
    return ordered


def _is_reachable(base_url: str, timeout: float = 2.0) -> bool:
    """粗略可达性检查：尝试 GET base_url（允许 40x/50x，只要能连通即认为可达）。"""
    try:
        req = Request(base_url, method="GET")
        with urlopen(req, timeout=timeout) as resp:  # noqa: S310 (stdlib)
            # 任意返回即视为可达
            return True
    except HTTPError:
        # HTTP 错误（401 Unauthorized, 404 Not Found, 500等）说明服务可达
        # 只是需要认证或路径不对，但服务本身是可达的
        return True
    except URLError:
        # URL 错误（连接超时、DNS解析失败等）视为不可达
        return False
    except Exception:
        # 其他未知错误视为不可达
        return False


def get_resilient_llm_config(role: LLMRole) -> LLMConfig:
    """返回具备自动切换能力的 LLM 配置。

    策略：
    1) 首选当前 LLM_PROVIDER 指定的服务商。
    2) 若其 base_url 不可达，按固定优先级尝试其它服务商，返回第一个可达的配置。
    3) 若都不可达，仍返回首选服务商的配置（由上层在调用时处理错误）。
    """
    primary = _read_env_provider()
    for provider in _provider_priority(primary):
        # 为指定 provider 构建配置
        # 复用 _get_env_config 的逻辑：临时把读取顺序“设想”为该 provider
        # 这里直接模拟 role 下该 provider 的产出
        # 注意：_get_env_config 内部读取 provider 来自 _read_env_provider()
        # 因此我们在此手动生成 api_key/base_url/model
        api_key = _read_env_api_key(provider)
        base_url = _read_env_base_url(provider)
        env_think_model, env_chat_model = _read_env_models()

        defaults = {
            LLMProvider.DEEPSEEK: {"think": "deepseek-reasoner", "chat": "deepseek-chat"},
            LLMProvider.SILICONFLOW: {"think": "deepseek-ai/DeepSeek-R1", "chat": "Pro/deepseek-ai/DeepSeek-V3.2-Exp"},
            LLMProvider.ZHIPU: {"think": "glm-4.6", "chat": "glm-4.6"},
            LLMProvider.KIMI: {"think": "kimi-k2-thinking", "chat": "kimi-k2-0905-preview"},
            LLMProvider.GEMINI: {"think": "gemini-2.5-pro", "chat": "gemini-2.5-pro"},
        }

        model = env_think_model or defaults[provider]["think"] if role == LLMRole.THINK else env_chat_model or defaults[provider]["chat"]

        if _is_reachable(base_url):
            return LLMConfig(
                model=model,
                api_key=api_key,
                base_url=base_url,
                temperature=0,
                streaming=True,
                max_tokens=None,
                max_retries=3,
                provider=provider.value,
            )

    # 全部不可达，返回首选 provider 的配置（可能会在调用处失败）
    api_key, base_url, model = _get_env_config(role)
    return LLMConfig(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=0,
        streaming=True,
        max_tokens=None,
        max_retries=3,
        provider=_read_env_provider().value,
    )


def get_llm_config(
    role: LLMRole, 
    provider_name: Optional[str] = None,
    model_name: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None
) -> LLMConfig:
    """根据角色获取 LLM 配置

    Args:
        role: LLM 角色（think 或 chat）
        provider_name: 可选的提供商名称（deepseek/siliconflow/zhipu/kimi/gemini），如果指定则覆盖环境变量
        model_name: 可选的模型名称，如果指定则覆盖默认模型和环境变量
        api_key: 可选的API Key，如果指定则覆盖环境变量
        base_url: 可选的Base URL，如果指定则覆盖环境变量

    Returns:
        LLMConfig: 对应的 LLM 配置
    """
    # 如果指定了提供商，使用指定的；否则从环境变量读取
    if provider_name:
        provider = None
        provider_lower = provider_name.strip().lower()
        for p in LLMProvider:
            if p.value == provider_lower:
                provider = p
                break
        if provider is None:
            raise ValueError(f"不支持的提供商: {provider_name}。支持的提供商: {', '.join(p.value for p in LLMProvider)}")
    else:
        provider = _read_env_provider()

    # 获取该提供商的配置（如果未通过参数指定，则从环境变量读取）
    final_api_key = api_key or _read_env_api_key(provider)
    final_base_url = base_url or _read_env_base_url(provider)
    env_think_model, env_chat_model = _read_env_models()

    # 每个服务商的合理默认模型（可被 model_name 或 THINK_MODEL/CHAT_MODEL 覆盖）
    defaults = {
        LLMProvider.DEEPSEEK: {
            "think": "deepseek-reasoner",
            "chat": "deepseek-chat",
        },
        LLMProvider.SILICONFLOW: {
            "think": "deepseek-ai/DeepSeek-R1",
            "chat": "Pro/deepseek-ai/DeepSeek-V3.2-Exp",
        },
        LLMProvider.ZHIPU: {
            "think": "glm-4.6",
            "chat": "glm-4.6",
        },
        LLMProvider.KIMI: {
            "think": "kimi-k2-thinking",
            "chat": "kimi-k2-0905-preview",
        },
        LLMProvider.GEMINI: {
            "think": "gemini-2.5-pro",
            "chat": "gemini-2.5-pro",
        },
    }

    # 优先级：model_name > 环境变量 > 默认值
    if model_name:
        final_model = model_name
    elif role == LLMRole.THINK:
        final_model = env_think_model or defaults[provider]["think"]
    else:
        final_model = env_chat_model or defaults[provider]["chat"]

    if role not in (LLMRole.THINK, LLMRole.CHAT):
        raise ValueError(f"不支持的 LLM 角色: {role}")

    return LLMConfig(
        model=final_model,
        api_key=final_api_key,
        base_url=final_base_url,
        temperature=0,
        streaming=True,
        max_tokens=None,
        max_retries=3,
        provider=provider.value,
    )


def get_llm_config_by_provider(provider_name: str, role: LLMRole) -> LLMConfig:
    """根据提供商名称和角色获取 LLM 配置（便捷函数）

    Args:
        provider_name: 提供商名称（deepseek/siliconflow/zhipu/kimi/gemini）
        role: LLM 角色（think 或 chat）

    Returns:
        LLMConfig: 对应的 LLM 配置
    """
    return get_llm_config(role, provider_name=provider_name)


def list_available_providers() -> list[dict]:
    """列出所有可用的提供商及其信息

    Returns:
        包含提供商信息的字典列表
    """
    providers_info = []
    for provider in LLMProvider:
        api_key = _read_env_api_key(provider)
        base_url = _read_env_base_url(provider)
        defaults = {
            LLMProvider.DEEPSEEK: {"think": "deepseek-reasoner", "chat": "deepseek-chat"},
            LLMProvider.SILICONFLOW: {"think": "deepseek-ai/DeepSeek-R1", "chat": "Pro/deepseek-ai/DeepSeek-V3.2-Exp"},
            LLMProvider.ZHIPU: {"think": "glm-4.6", "chat": "glm-4.6"},
            LLMProvider.KIMI: {"think": "kimi-k2-thinking", "chat": "kimi-k2-0905-preview"},
            LLMProvider.GEMINI: {"think": "gemini-2.5-pro", "chat": "gemini-2.5-pro"},
        }
        
        # 检查可达性
        is_reachable = _is_reachable(base_url)
        has_api_key = bool(api_key)
        
        provider_info = {
            "name": provider.value,
            "display_name": {
                LLMProvider.DEEPSEEK: "DeepSeek",
                LLMProvider.SILICONFLOW: "SiliconFlow (硅基流动)",
                LLMProvider.ZHIPU: "智谱GLM",
                LLMProvider.KIMI: "Kimi (月之暗面)",
                LLMProvider.GEMINI: "Google Gemini",
            }.get(provider, provider.value),
            "think_model": defaults[provider]["think"],
            "chat_model": defaults[provider]["chat"],
            "base_url": base_url,
            "has_api_key": has_api_key,
            "is_reachable": is_reachable,
            "status": "✅ 可用" if (has_api_key and is_reachable) else ("⚠️  API Key缺失" if not has_api_key else "❌ 不可达"),
        }
        
        # 为硅基流动添加可用模型列表
        if provider == LLMProvider.SILICONFLOW:
            provider_info["available_models"] = get_siliconflow_models()
        
        providers_info.append(provider_info)
    
    return providers_info


THINK_CONFIG = get_llm_config(LLMRole.THINK)
CHAT_CONFIG = get_llm_config(LLMRole.CHAT)


def get_think_config() -> LLMConfig:
    return get_llm_config(LLMRole.THINK)


def get_chat_config() -> LLMConfig:
    return get_llm_config(LLMRole.CHAT)


@dataclass
class Sarif2JsonConfig:
    """SARIF 转 JSON 的全局配置。"""

    max_results: int = 3
    threadflow_index: int = 0
    rule_filter: Optional[str] = None


SARIF2JSON_CONFIG = Sarif2JsonConfig()


def get_sarif2json_config() -> Sarif2JsonConfig:
    """获取 SARIF 转 JSON 的配置实例。"""

    return SARIF2JSON_CONFIG


"""
使用方式：

1. 推荐方式：通过角色获取配置
   from config import get_llm_config, LLMRole

   # CodeQL 相关任务使用推理模型
   think_config = get_llm_config(LLMRole.THINK)

   # 一般分析任务使用对话模型
   chat_config = get_llm_config(LLMRole.CHAT)

2. 便捷方式：使用便捷函数
   from config import get_think_config, get_chat_config

   think_config = get_think_config()
   chat_config = get_chat_config()
"""
