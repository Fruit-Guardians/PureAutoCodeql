"""分析步骤抽象基类。"""

from abc import ABC, abstractmethod
from typing import Any

from ..context import AnalysisContext


class AnalysisStep(ABC):
    """分析步骤抽象基类。"""

    def __init__(self, name: str, agent_name: str = None):
        self.name = name
        self.agent_name = agent_name or name

    @abstractmethod
    async def execute(self, context: AnalysisContext) -> Any:
        """执行分析步骤。"""
        pass
