#!/usr/bin/env python3
"""
LangChain Tool for LSP Function Definition Lookup

Provides a LangChain-compatible tool that allows agents to query
CodeQL function definitions using LSP protocol.
"""

from typing import Optional, Type
from pydantic import BaseModel, Field
from langchain.tools import BaseTool

from tools.lsp_codeql import HotCodeQL
from utils.lsp_definition import LSPDefinitionLookup


class LSPFunctionLookupInput(BaseModel):
    """Input schema for LSP function lookup tool."""
    
    function_name: str = Field(
        description="Name of the CodeQL function to look up (e.g., 'hasQualifiedName', 'getACall')"
    )


class LSPFunctionLookupTool(BaseTool):
    """
    LangChain tool for querying CodeQL function definitions via LSP.
    
    This tool allows agents to look up function definitions from the CodeQL
    standard library to understand usage and fix errors. It reuses an existing
    HotCodeQL LSP engine instance without restarting the service.
    
    Example usage:
        tool = LSPFunctionLookupTool(engine=hot_codeql_instance)
        result = tool.run("hasQualifiedName")
    """
    
    name: str = "lsp_function_lookup"
    description: str = (
        "Look up CodeQL function definitions from the standard library. "
        "Useful when you need to understand how a CodeQL function works, "
        "what parameters it accepts, or see its implementation. "
        "Input should be the function name (e.g., 'hasQualifiedName')."
    )
    args_schema: Type[BaseModel] = LSPFunctionLookupInput
    
    # Custom field for HotCodeQL engine
    engine: Optional[HotCodeQL] = None
    
    class Config:
        """Pydantic configuration."""
        arbitrary_types_allowed = True
    
    def __init__(self, engine: HotCodeQL, **kwargs):
        """
        Initialize the tool with a HotCodeQL engine instance.
        
        Args:
            engine: Running HotCodeQL LSP engine instance
            **kwargs: Additional arguments passed to BaseTool
        """
        super().__init__(engine=engine, **kwargs)
        self._lookup = LSPDefinitionLookup(engine)
    
    def _run(self, function_name: str) -> str:
        """
        Execute the function definition lookup.
        
        Args:
            function_name: Name of the function to look up
            
        Returns:
            Formatted string with file path, line range, and code definition
        """
        if not function_name or not isinstance(function_name, str):
            return "Error: function_name must be a non-empty string"
        
        try:
            result = self._lookup.get_function_definition(
                function_name=function_name.strip(),
                timeout=5.0
            )
            
            if not result:
                return (
                    f"Could not find definition for '{function_name}'. "
                    "Make sure the function is used in the current query file "
                    "and the LSP engine is running."
                )
            
            # Format the result
            output = [
                f"Function: {function_name}",
                f"File: {result['file_path']}",
                f"Lines: {result['start_line']}-{result['end_line']}",
                "",
                "Definition:",
                "```ql",
                result['code'],
                "```"
            ]
            
            return "\n".join(output)
            
        except Exception as e:
            return f"Error looking up function definition: {type(e).__name__}: {e}"
    
    async def _arun(self, function_name: str) -> str:
        """
        Async version of _run.
        
        Currently not implemented as LSP operations are synchronous.
        Falls back to synchronous implementation.
        """
        return self._run(function_name)
