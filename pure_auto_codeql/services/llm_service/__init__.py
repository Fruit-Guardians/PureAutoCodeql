"""LLM service package (split from the former llm_service module).

Provides LLM service wrappers including agent management and execution.
Public surface is unchanged: import AgentResult and MultiAgentAnalyzer
exactly as before.
"""

from .analyzer import AgentResult, MultiAgentAnalyzer
from .chat_client import RetryableChatOpenAI
from .retry import (
    AgentRetryTracker,
    APIErrorClassifier,
    llm_retry_decorator,
)

__all__ = [
    "AgentResult",
    "MultiAgentAnalyzer",
    "RetryableChatOpenAI",
    "AgentRetryTracker",
    "APIErrorClassifier",
    "llm_retry_decorator",
]
