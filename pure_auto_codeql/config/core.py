"""
配置系统核心模块

包含：
- 数据类定义（LLMRole, LLMProvider, ProviderConfig, LLMConfig）
- 服务商注册中心（ProviderRegistry）
- 内置服务商注册
- 核心配置函数
"""

import os
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Optional
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from pure_auto_codeql.paths import get_repo_root

try:
    import tomli as tomllib  # Python < 3.11
except ImportError:
    try:
        import tomllib  # Python >= 3.11
    except ImportError:
        import tomli as tomllib  # fallback


# ============================================================================
# 数据类定义
# ============================================================================

class LLMRole(Enum):
    """LLM 角色枚举"""
    THINK = "think"  # 推理模型，用于 CodeQL 相关任务
    CHAT = "chat"    # 对话模型，用于一般分析任务


class LLMProvider(Enum):
    """支持的服务商（均为 OpenAI 兼容协议）"""
    DEEPSEEK = "deepseek"
    SILICONFLOW = "siliconflow"
    ZHIPU = "zhipu"
    KIMI = "kimi"
    GEMINI = "gemini"


@dataclass
class ProviderConfig:
    """统一的服务商配置"""
    name: str
    display_name: str
    base_url: str
    default_think_model: str
    default_chat_model: str
    env_keys: list[str]
    env_base_urls: list[str]
    available_models: Optional[list[str]] = None
    description: str = ""
    is_builtin: bool = True
    custom_params: dict[str, Any] = field(default_factory=dict)

    def get_api_key(self) -> str:
        """获取 API Key，按优先级：环境变量 > keys.toml"""
        # 优先使用专属环境变量
        for env_name in self.env_keys:
            if os.getenv(env_name):
                return os.getenv(env_name)  # type: ignore

        # 通用兜底环境变量
        for env_name in ["OPENAI_API_KEY", "API_KEY"]:
            if os.getenv(env_name):
                return os.getenv(env_name)  # type: ignore

        # 从 keys.toml 读取（由 ProviderRegistry 设置）
        if hasattr(self, '_dev_key'):
            return self._dev_key

        return ""

    def get_base_url(self) -> str:
        """获取 Base URL，按优先级尝试环境变量"""
        # 优先使用专属环境变量
        for env_name in self.env_base_urls:
            if os.getenv(env_name):
                return os.getenv(env_name)  # type: ignore

        # 通用兜底
        for env_name in ["OPENAI_BASE_URL", "BASE_URL"]:
            if os.getenv(env_name):
                return os.getenv(env_name)  # type: ignore

        return self.base_url

    def is_configured(self) -> bool:
        """检查是否已配置（有 API Key）"""
        return bool(self.get_api_key())

    def is_reachable(self, timeout: float = 2.0) -> bool:
        """检查服务是否可达（基础网络检测）"""
        try:
            req = Request(self.get_base_url(), method="GET")
            with urlopen(req, timeout=timeout):  # noqa: S310
                return True
        except HTTPError:
            return True  # HTTP 错误说明服务可达
        except (URLError, Exception):
            return False

    def validate_api_key(self) -> tuple[bool, str]:
        """真实调用API验证key是否有效

        Returns:
            (is_valid, error_message)
        """
        api_key = self.get_api_key()
        if not api_key:
            return False, "API Key未配置"

        try:
            import httpx

            # 构造一个简单的API调用来验证
            headers = {
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json"
            }

            # 尝试调用models列表接口（OpenAI兼容）
            url = f"{self.get_base_url()}/models"

            with httpx.Client(timeout=5.0) as client:
                response = client.get(url, headers=headers)

                if response.status_code == 200:
                    return True, "验证成功"
                elif response.status_code == 401:
                    return False, "API Key无效"
                elif response.status_code == 403:
                    return False, "API Key无权限"
                else:
                    return False, f"验证失败: {response.status_code}"

        except Exception as e:
            # 如果/models接口不可用，尝试一个最小的chat请求
            try:
                with httpx.Client(timeout=5.0) as client:
                    test_data = {
                        "model": self.default_chat_model,
                        "messages": [{"role": "user", "content": "hi"}],
                        "max_tokens": 1
                    }
                    response = client.post(
                        f"{self.get_base_url()}/chat/completions",
                        headers=headers,
                        json=test_data
                    )

                    if response.status_code in [200, 400]:  # 400可能是参数问题，但key有效
                        return True, "验证成功"
                    elif response.status_code == 401:
                        return False, "API Key无效"
                    else:
                        return False, f"无法验证: {str(e)}"
            except Exception:
                # 最后兜底：如果都失败，说明可能网络问题或API不标准
                return False, f"无法验证: {str(e)}"

    def get_status(self, validate_key: bool = False) -> tuple[str, str]:
        """获取状态标记和描述

        Args:
            validate_key: 是否真实验证API Key（较慢但准确）
        """
        has_key = self.is_configured()

        if not has_key:
            return ("󰀪", "API Key 缺失")

        # 快速检查：网络可达性
        if not validate_key:
            reachable = self.is_reachable()
            if reachable:
                return ("󰄬", "已配置")
            else:
                return ("󰅙", "不可达")

        # 深度检查：真实API调用
        is_valid, error_msg = self.validate_api_key()
        if is_valid:
            return ("󰄬", "已验证")
        else:
            return ("󰅙", error_msg)


@dataclass
class LLMConfig:
    """LLM 配置"""
    model: str
    api_key: str
    base_url: str
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None
    max_retries: int = 3
    provider: Optional[str] = None

    # 重试机制配置
    retry_base_delay: float = 1.0  # 基础延迟时间（秒）
    retry_backoff_factor: float = 2.0  # 退避因子
    retry_jitter: bool = True  # 是否启用抖动算法


# Sarif2JsonConfig 已移除 - 这不是LLM配置，应该在 utils/ 中


# ============================================================================
# 服务商注册中心
# ============================================================================

class ProviderRegistry:
    """服务商注册中心"""
    _providers: dict[str, ProviderConfig] = {}
    _keys_config: dict = {}

    @classmethod
    def register(cls, config: ProviderConfig) -> None:
        """注册一个服务商"""
        cls._providers[config.name] = config

    @classmethod
    def get(cls, name: str) -> Optional[ProviderConfig]:
        """获取服务商配置"""
        return cls._providers.get(name)

    @classmethod
    def list_all(cls) -> list[ProviderConfig]:
        """列出所有已注册的服务商"""
        return list(cls._providers.values())

    @classmethod
    def list_available(cls) -> list[ProviderConfig]:
        """列出所有已配置且可用的服务商"""
        return [p for p in cls._providers.values() if p.is_configured() and p.is_reachable()]

    @classmethod
    def exists(cls, name: str) -> bool:
        """检查服务商是否已注册"""
        return name in cls._providers

    @classmethod
    def load_keys_toml(cls, filepath: Optional[str] = None) -> None:
        """从 keys.toml 加载配置"""
        if filepath is None:
            # 默认路径：config/keys.toml
            filepath = get_repo_root() / "config" / "keys.toml"

        filepath = Path(filepath)
        if not filepath.exists():
            return  # keys.toml 不存在，使用默认配置

        try:
            with open(filepath, 'rb') as f:
                cls._keys_config = tomllib.load(f)

            # 1. 加载内置服务商的 API Keys
            builtin_keys = cls._keys_config.get('builtin_keys', {})
            for name, key in builtin_keys.items():
                if key and name in cls._providers:
                    cls._providers[name]._dev_key = key

            # 2. 注册自定义服务商
            custom_providers = cls._keys_config.get('custom_providers', [])
            for provider_data in custom_providers:
                cls._register_custom_from_toml(provider_data)

        except Exception as e:
            print(f"警告：加载 keys.toml 失败: {e}")

    @classmethod
    def _register_custom_from_toml(cls, data: dict) -> None:
        """从 TOML 数据注册自定义服务商"""
        config = ProviderConfig(
            name=data["name"],
            display_name=data.get("display_name", data["name"]),
            base_url=data["base_url"],
            default_think_model=data.get("think_model", ""),
            default_chat_model=data.get("chat_model", ""),
            env_keys=[],  # 自定义服务商主要用 keys.toml
            env_base_urls=[],
            available_models=data.get("available_models"),
            description=data.get("description", ""),
            is_builtin=False,
        )
        # 设置 API Key
        if "api_key" in data:
            config._dev_key = data["api_key"]
        cls.register(config)

    @classmethod
    def get_default_provider(cls) -> str:
        """获取默认服务商名称"""
        settings = cls._keys_config.get('settings', {})
        return settings.get('default_provider', os.getenv('LLM_PROVIDER', 'deepseek'))

    @classmethod
    def get_default_models(cls) -> tuple[Optional[str], Optional[str]]:
        """获取默认模型（think_model, chat_model）"""
        settings = cls._keys_config.get('settings', {})
        return (
            settings.get('default_think_model') or os.getenv('THINK_MODEL'),
            settings.get('default_chat_model') or os.getenv('CHAT_MODEL')
        )


# ============================================================================
# 内置服务商注册
# ============================================================================

def register_builtin_providers() -> None:
    """注册所有内置服务商"""

    # DeepSeek
    ProviderRegistry.register(ProviderConfig(
        name="deepseek",
        display_name="DeepSeek",
        base_url="https://api.deepseek.com/v1",
        default_think_model="deepseek-reasoner",
        default_chat_model="deepseek-chat",
        env_keys=["DEEPSEEK_API_KEY"],
        env_base_urls=["DEEPSEEK_BASE_URL"],
        description="DeepSeek 官方 API，提供高性能推理和对话模型",
        is_builtin=True,
    ))

    # SiliconFlow
    ProviderRegistry.register(ProviderConfig(
        name="siliconflow",
        display_name="SiliconFlow (硅基流动)",
        base_url="https://api.siliconflow.cn/v1",
        default_think_model="deepseek-ai/DeepSeek-R1",
        default_chat_model="Pro/deepseek-ai/DeepSeek-V3.2-Exp",
        env_keys=["SILICONFLOW_API_KEY", "SF_API_KEY"],
        env_base_urls=["SILICONFLOW_BASE_URL", "SF_BASE_URL"],
        available_models=[
            "deepseek-ai/DeepSeek-R1",
            "Pro/deepseek-ai/DeepSeek-V3.2-Exp",
            "MiniMaxAI/MiniMax-M2",
            "moonshotai/Kimi-K2-Instruct-0905",
            "Qwen/Qwen3-Coder-480B-A35B-Instruct",
        ],
        description="硅基流动提供多种国产大模型 API 服务",
        is_builtin=True,
    ))

    # 智谱 GLM
    ProviderRegistry.register(ProviderConfig(
        name="zhipu",
        display_name="智谱GLM",
        base_url="https://open.bigmodel.cn/api/paas/v4/",
        default_think_model="glm-4.6",
        default_chat_model="glm-4.6",
        env_keys=["ZHIPU_API_KEY", "GLM_API_KEY"],
        env_base_urls=["ZHIPU_BASE_URL", "GLM_BASE_URL"],
        description="智谱 AI 的 GLM 系列模型",
        is_builtin=True,
    ))

    # Kimi
    ProviderRegistry.register(ProviderConfig(
        name="kimi",
        display_name="Kimi (月之暗面)",
        base_url="https://api.moonshot.cn/v1",
        default_think_model="kimi-k2-thinking",
        default_chat_model="kimi-k2-0905-preview",
        env_keys=["KIMI_API_KEY", "MOONSHOT_API_KEY"],
        env_base_urls=["KIMI_BASE_URL", "MOONSHOT_BASE_URL"],
        description="月之暗面 Kimi 智能助手 API",
        is_builtin=True,
    ))

    # Google Gemini
    ProviderRegistry.register(ProviderConfig(
        name="gemini",
        display_name="Google Gemini",
        base_url="https://generativelanguage.googleapis.com/v1beta/openai",
        default_think_model="gemini-2.5-pro",
        default_chat_model="gemini-2.5-pro",
        env_keys=["GEMINI_API_KEY", "GOOGLE_API_KEY"],
        env_base_urls=["GEMINI_BASE_URL", "GOOGLE_BASE_URL"],
        description="Google Gemini 系列模型",
        is_builtin=True,
    ))


# ============================================================================
# 核心配置函数
# ============================================================================

def get_llm_config(
    role: LLMRole,
    provider_name: Optional[str] = None,
    model_name: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    auto_fallback: bool = False
) -> LLMConfig:
    """根据角色获取 LLM 配置"""
    if role not in (LLMRole.THINK, LLMRole.CHAT):
        raise ValueError(f"不支持的 LLM 角色: {role}")

    # 1. 确定使用哪个服务商
    if provider_name:
        target_provider = ProviderRegistry.get(provider_name)
        if not target_provider:
            available = ", ".join(p.name for p in ProviderRegistry.list_all())
            raise ValueError(f"服务商 '{provider_name}' 不存在。可用服务商: {available}")
    else:
        # 从 keys.toml 或环境变量读取默认服务商
        default_name = ProviderRegistry.get_default_provider()
        target_provider = ProviderRegistry.get(default_name)
        if not target_provider:
            target_provider = ProviderRegistry.get("deepseek")

    # 2. 自动切换
    if auto_fallback and target_provider:
        if not (target_provider.is_configured() and target_provider.is_reachable()):
            available_providers = ProviderRegistry.list_available()
            if available_providers:
                target_provider = available_providers[0]

    # 3. 确定 API Key 和 Base URL
    final_api_key = api_key or (target_provider.get_api_key() if target_provider else "")
    final_base_url = base_url or (target_provider.get_base_url() if target_provider else "")

    # 4. 确定模型
    default_think, default_chat = ProviderRegistry.get_default_models()

    if model_name:
        final_model = model_name
    elif role == LLMRole.THINK:
        final_model = default_think or (target_provider.default_think_model if target_provider else "")
    else:
        final_model = default_chat or (target_provider.default_chat_model if target_provider else "")

    return LLMConfig(
        model=final_model,
        api_key=final_api_key,
        base_url=final_base_url,
        temperature=0,
        streaming=True,
        max_tokens=None,
        max_retries=3,
        provider=target_provider.name if target_provider else "unknown",
    )


def get_think_config() -> LLMConfig:
    """获取推理模型配置"""
    return get_llm_config(LLMRole.THINK)


def get_chat_config() -> LLMConfig:
    """获取对话模型配置"""
    return get_llm_config(LLMRole.CHAT)


# ============================================================================
# 初始化
# ============================================================================

# 注册内置服务商
register_builtin_providers()

# 加载 keys.toml
ProviderRegistry.load_keys_toml()

# 全局配置实例
THINK_CONFIG = get_llm_config(LLMRole.THINK)
CHAT_CONFIG = get_llm_config(LLMRole.CHAT)
