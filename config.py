import os
from dataclasses import dataclass
from typing import Optional
from enum import Enum


class LLMRole(Enum):
    THINK = "think"  # 推理模型，用于 CodeQL 相关任务
    CHAT = "chat"    # 对话模型，用于一般分析任务


@dataclass
class LLMConfig:
    model: str
    api_key: str
    base_url: str
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None
    max_retries: int = 3


def _get_env_config() -> tuple[str, str]:
    api_key = os.getenv("DEEPSEEK_API_KEY")
    base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1")
    
    if not api_key:
        api_key = "sk-2f0911b0688c4d5684ec7f75a8caecce"
    
    return api_key, base_url


def get_llm_config(role: LLMRole) -> LLMConfig:
    """根据角色获取 LLM 配置
    
    Args:
        role: LLM 角色（think 或 chat）
        
    Returns:
        LLMConfig: 对应的 LLM 配置
    """
    api_key, base_url = _get_env_config()
    
    if role == LLMRole.THINK:
        # 推理模型配置，用于 CodeQL 生成/验证
        return LLMConfig(
            model="deepseek-reasoner",
            api_key=api_key,
            base_url=base_url,
            temperature=0,
            streaming=True,
            max_tokens=None,
            max_retries=3
        )
    elif role == LLMRole.CHAT:
        # 对话模型配置，用于一般分析任务
        return LLMConfig(
            model="deepseek-chat",
            api_key=api_key,
            base_url=base_url,
            temperature=0,
            streaming=True,
            max_tokens=None,
            max_retries=3
        )
    else:
        raise ValueError(f"不支持的 LLM 角色: {role}")


THINK_CONFIG = get_llm_config(LLMRole.THINK)
CHAT_CONFIG = get_llm_config(LLMRole.CHAT)


def get_think_config() -> LLMConfig:
    return get_llm_config(LLMRole.THINK)


def get_chat_config() -> LLMConfig:
    return get_llm_config(LLMRole.CHAT)


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
