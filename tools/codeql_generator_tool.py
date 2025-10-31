"""用于从自然语言生成CodeQL查询的LangChain工具。"""

import re
from typing import Optional, Type, Any
from pydantic import BaseModel, Field
from langchain_core.tools import BaseTool


class CodeQLGeneratorInput(BaseModel):
    """CodeQL生成器工具的输入模式。"""
    requirement: str = Field(
        description="CodeQL查询需求的自然语言描述。 "
                    "例如：'查找所有用户输入源'或'查找从用户输入到SQL执行的路径'"
    )
    # Added: allow selecting target language
    language: Optional[str] = Field(
        default="java",
        description="CodeQL查询的目标编程语言（'java', 'python', 'c'）。默认为'java'。"
    )


class CodeQLGeneratorTool(BaseTool):
    """用于从自然语言需求生成CodeQL查询代码的工具。"""
    
    name: str = "codeql_generator"
    description: str = (
        "基于自然语言需求生成CodeQL查询代码。 "
        "输入应清晰描述您想在代码中查找的内容。 "
        "可选指定目标语言（java/python/c）。 "
        "返回可在CodeQL数据库上执行的完整CodeQL查询。"
    )
    args_schema: Type[BaseModel] = CodeQLGeneratorInput
    
    # The analyzer will be injected during initialization
    analyzer: Optional[object] = None
    
    def __init__(self, analyzer=None, **kwargs):
        """
        使用MultiAgentAnalyzer实例初始化工具。
        
        Args:
            analyzer: 提供LLM能力的MultiAgentAnalyzer实例
        """
        super().__init__(**kwargs)
        self.analyzer = analyzer
    
    def _extract_codeql_from_response(self, content: str) -> str:
        match = re.search(r'<codeql>(.*?)</codeql>', content, re.DOTALL)
        if match:
            return match.group(1).strip()
        return content.strip()
    
    def _run(
        self,
        requirement: str,
        language: Optional[str] = None,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        同步执行（不支持仅异步的agent）。
        
        Args:
            requirement: CodeQL查询的自然语言需求
            language: 目标编程语言（java/python/c）
            run_manager: 工具执行的回调管理器
            
        Returns:
            生成的CodeQL查询代码或错误消息
        """
        return "Synchronous execution not supported. Please use async version (arun)."
    
    async def _arun(
        self,
        requirement: str,
        language: Optional[str] = None,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        异步生成CodeQL查询代码。
        
        Args:
            requirement: CodeQL查询的自然语言需求
            language: 目标编程语言（java/python/c）。如果未提供，默认为'java'
            run_manager: 异步工具执行的回调管理器
            
        Returns:
            生成的CodeQL查询代码或错误消息
        """
        if not self.analyzer:
            return "Error: No analyzer configured. Tool needs to be initialized with a MultiAgentAnalyzer instance."
        
        try:
            # Import here to avoid circular dependency
            from agents.codeql_generator_agent import CodeQLGeneratorAgent
            
            agent = CodeQLGeneratorAgent(self.analyzer)
            # Pass language down to the agent (default to java)
            result = await agent.generate_codeql(requirement, language=(language or "java"))
            
            if result.success:
                codeql_code = self._extract_codeql_from_response(result.content)
                return codeql_code
            else:
                error_msg = result.error if result.error else "Unknown error occurred"
                return f"Error generating CodeQL: {error_msg}"
        
        except Exception as e:
            return f"Error during CodeQL generation: {str(e)}"

