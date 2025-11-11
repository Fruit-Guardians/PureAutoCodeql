"""语言适配器基类"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, Dict, List


class LanguageAdapter(ABC):
    """语言适配器基类 - 定义语言特定的分析接口"""
    
    @abstractmethod
    def analyze_source_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """
        分析Source点
        
        Args:
            location: 位置信息
            code_context: 代码上下文
        
        Returns:
            Source点分析结果
        """
        pass
    
    @abstractmethod
    def analyze_sink_point(
        self,
        location: Dict[str, Any],
        code_context: str
    ) -> Dict[str, Any]:
        """
        分析Sink点
        
        Args:
            location: 位置信息
            code_context: 代码上下文
        
        Returns:
            Sink点分析结果
        """
        pass
    
    @abstractmethod
    def get_dangerous_apis(self) -> List[str]:
        """获取该语言的危险API列表"""
        pass
    
    def extract_api_calls(self, code: str) -> List[str]:
        """提取代码中的API调用（通用实现）"""
        # 简单的实现，子类可以覆盖
        import re
        
        # 匹配函数调用模式：identifier(
        pattern = r'\b(\w+)\s*\('
        matches = re.findall(pattern, code)
        
        return matches


