#!/usr/bin/env python3
"""
LangChain Tool for LSP Function Definition Lookup

Provides a LangChain-compatible tool that allows agents to query
CodeQL function definitions using LSP protocol.
"""

from typing import Optional, Type

from langchain.tools import BaseTool
from pydantic import BaseModel, ConfigDict, Field

from pure_auto_codeql.utils.lsp_definition import LSPDefinitionLookup

from .lsp_codeql import HotCodeQL


class LSPFunctionLookupInput(BaseModel):
    """Input schema for LSP function lookup tool."""

    function_name: str = Field(
        description="Name of the CodeQL function to look up (e.g., 'hasQualifiedName', 'getACall')"
    )
    language: str = Field(
        default="java",
        description="Target language for the CodeQL query (e.g., 'java', 'python', 'cpp', 'javascript', 'go', 'csharp', 'ruby')"
    )


class LSPFunctionLookupTool(BaseTool):
    """
    LangChain tool for querying CodeQL function definitions.

    This tool allows agents to look up function definitions from the CodeQL
    standard library using ripgrep (fast) or Python fallback (cross-platform).
    No LSP engine required - works independently.

    Example usage:
        tool = LSPFunctionLookupTool()
        result = tool.run("hasQualifiedName")
    """

    name: str = "lsp_function_lookup"
    description: str = (
        "Look up CodeQL function definitions from the standard library. "
        "Useful when you need to understand how a CodeQL function works, "
        "what parameters it accepts, or see its implementation. "
        "Input should be the function name (e.g., 'hasQualifiedName') and optionally the target language. "
        "Supports multiple languages: java, python, cpp, javascript, go, csharp, ruby, etc. "
        "If language is not specified, defaults to 'java'."
    )
    args_schema: Type[BaseModel] = LSPFunctionLookupInput

    # Custom field for HotCodeQL engine (optional, for backward compatibility)
    engine: Optional[HotCodeQL] = None

    # Default language for lookups (can be overridden per call)
    default_language: str = "java"

    model_config = ConfigDict(arbitrary_types_allowed=True)

    def __init__(self, engine: Optional[HotCodeQL] = None, default_language: str = "java", **kwargs):
        """
        Initialize the tool.

        Args:
            engine: Optional HotCodeQL LSP engine instance (for backward compatibility)
                   If None, uses ripgrep/Python search (recommended)
            default_language: Default language for lookups (default: java)
            **kwargs: Additional arguments passed to BaseTool
        """
        super().__init__(engine=engine, default_language=default_language, **kwargs)

        # Create a dummy engine if none provided (for LSPDefinitionLookup compatibility)
        if engine is None:
            class DummyEngine:
                """Dummy engine for ripgrep-only mode."""
                pass
            engine = DummyEngine()

        self._lookup = LSPDefinitionLookup(engine)

    def _run(self, function_name: str, language: str = None) -> str:
        """
        Execute the function definition lookup with fallback.

        Args:
            function_name: Name of the function to look up
            language: Target language (default: java)

        Returns:
            Formatted string with file path, line range, and code definition
        """
        if not function_name or not isinstance(function_name, str):
            return "Error: function_name must be a non-empty string"

        # Use default language if not specified
        if language is None:
            language = self.default_language

        # Print query information
        print(f"\n{'='*60}")
        print("󰍉 [LSP函数查询] 正在查询函数定义...")
        print(f"📝 查询函数名: {function_name.strip()}")
        print(f"🌐 目标语言: {language}")
        print(f"{'='*60}\n")

        try:
            # Use the new method with fallback
            result = self._lookup.get_function_definition_with_fallback(
                function_name=function_name.strip(),
                language=language,
                timeout=5.0
            )

            if not result:
                print(f"󰅙 [函数查询] 未找到函数 '{function_name}' 的定义\n")
                return (
                    f"Could not find definition for '{function_name}'. "
                    "The symbol was not found in the current query file or the CodeQL standard library."
                )

            # Print success message
            print("󰄬 [函数查询] 查询成功!")
            print(f"󰉋 文件位置: {result['file_path']}")
            print(f"📍 代码行数: {result['start_line']}-{result['end_line']}")
            print(f"{'='*60}\n")

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
            print(f"󰅙 [函数查询] 查询失败: {type(e).__name__}: {e}\n")
            return f"Error looking up function definition: {type(e).__name__}: {e}"

    async def _arun(self, function_name: str, language: str = None) -> str:
        """
        Async version of _run.

        Currently not implemented as LSP operations are synchronous.
        Falls back to synchronous implementation.
        """
        return self._run(function_name, language)
