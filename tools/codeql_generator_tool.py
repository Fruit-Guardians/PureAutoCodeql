"""LangChain tool for generating CodeQL queries from natural language."""

import re
from typing import Optional, Type, Any
from pydantic import BaseModel, Field
from langchain_core.tools import BaseTool


class CodeQLGeneratorInput(BaseModel):
    """Input schema for CodeQL Generator Tool."""
    requirement: str = Field(
        description="Natural language description of the CodeQL query requirement. "
                    "For example: 'Find all user input sources' or 'Find paths from user input to SQL execution'"
    )


class CodeQLGeneratorTool(BaseTool):
    """Tool for generating CodeQL query code from natural language requirements."""
    
    name: str = "codeql_generator"
    description: str = (
        "Generates CodeQL query code based on natural language requirements. "
        "Input should be a clear description of what you want to find in the code. "
        "Returns a complete CodeQL query that can be executed against a CodeQL database."
    )
    args_schema: Type[BaseModel] = CodeQLGeneratorInput
    
    # The analyzer will be injected during initialization
    analyzer: Optional[object] = None
    
    def __init__(self, analyzer=None, **kwargs):
        """
        Initialize the tool with a MultiAgentAnalyzer instance.
        
        Args:
            analyzer: Instance of MultiAgentAnalyzer that provides LLM capabilities
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
        run_manager: Optional[Any] = None
    ) -> str:
        """
        Synchronous execution (not supported for async-only agent).
        
        Args:
            requirement: Natural language requirement for CodeQL query
            run_manager: Callback manager for tool execution
            
        Returns:
            Generated CodeQL query code or error message
        """
        return "Synchronous execution not supported. Please use async version (arun)."
    
    async def _arun(
        self,
        requirement: str,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        Asynchronously generate CodeQL query code.
        
        Args:
            requirement: Natural language requirement for CodeQL query
            run_manager: Async callback manager for tool execution
            
        Returns:
            Generated CodeQL query code or error message
        """
        if not self.analyzer:
            return "Error: No analyzer configured. Tool needs to be initialized with a MultiAgentAnalyzer instance."
        
        try:
            # Import here to avoid circular dependency
            from agents.codeql_generator_agent import CodeQLGeneratorAgent
            
            agent = CodeQLGeneratorAgent(self.analyzer)
            result = await agent.generate_codeql(requirement)
            
            if result.success:
                codeql_code = self._extract_codeql_from_response(result.content)
                return codeql_code
            else:
                error_msg = result.error if result.error else "Unknown error occurred"
                return f"Error generating CodeQL: {error_msg}"
        
        except Exception as e:
            return f"Error during CodeQL generation: {str(e)}"

