import os
from dataclasses import dataclass
from typing import Optional
from enum import Enum
from urllib.request import Request, urlopen
from urllib.error import URLError


class LLMRole(Enum):
    THINK = "think"  # 推理模型，用于 CodeQL 相关任务
    CHAT = "chat"    # 对话模型，用于一般分析任务


class LLMProvider(Enum):
    """支持的服务商（均为 OpenAI 兼容协议）。"""

    DEEPSEEK = "deepseek"
    SILICONFLOW = "siliconflow"  # 硅基流动
    ZHIPU = "zhipu"  # GLM（智谱）


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
    # 兜底：视为 DeepSeek
    return "https://api.deepseek.com/v1"


def _read_env_provider() -> LLMProvider:
    """读取默认服务商，默认 deepseek。通过 LLM_PROVIDER 指定：deepseek/siliconflow/zhipu"""
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
        LLMProvider.DEEPSEEK: "sk-a2d1b4e295d6404694f45f45cb236c91",
        LLMProvider.ZHIPU: "42e801a9a6994a5cb002ed8568ac1379.xLirJ33dyqMIPDiy",
    }
    return dev_defaults.get(provider, "")


def _read_env_base_url(provider: LLMProvider) -> str:
    """按服务商读取 base_url，支持通用 OPENAI_BASE_URL 覆盖。"""
    mapping = {
        LLMProvider.DEEPSEEK: ["DEEPSEEK_BASE_URL"],
        LLMProvider.SILICONFLOW: ["SILICONFLOW_BASE_URL", "SF_BASE_URL"],
        LLMProvider.ZHIPU: ["ZHIPU_BASE_URL", "GLM_BASE_URL"],
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
            # GLM 系列：无“思考模型”，默认 think 与 chat 均使用 glm-4.6
            "think": "glm-4.6",
            "chat": "glm-4.6",
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
    for p in [LLMProvider.DEEPSEEK, LLMProvider.SILICONFLOW, LLMProvider.ZHIPU]:
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
    except URLError:
        return False
    except Exception:
        # 其他错误也视为不可达
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


def get_llm_config(role: LLMRole) -> LLMConfig:
    """根据角色获取 LLM 配置
    
    Args:
        role: LLM 角色（think 或 chat）
        
    Returns:
        LLMConfig: 对应的 LLM 配置
    """
    api_key, base_url, model = _get_env_config(role)
    provider = _read_env_provider().value

    if role not in (LLMRole.THINK, LLMRole.CHAT):
        raise ValueError(f"不支持的 LLM 角色: {role}")

    return LLMConfig(
        model=model,
        api_key=api_key,
        base_url=base_url,
        temperature=0,
        streaming=True,
        max_tokens=None,
        max_retries=3,
        provider=provider,
    )


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
