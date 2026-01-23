"""服务层模块

PureAuto - 提供各种业务服务，包括LLM服务、语言检测等。
"""

from .llm_service import MultiAgentAnalyzer, AgentResult
from .language_detector import LanguageDetector

__all__ = [
    "MultiAgentAnalyzer",
    "AgentResult",
    "LanguageDetector"
]
