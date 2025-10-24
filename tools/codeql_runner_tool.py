"""LangChain tool for executing CodeQL queries."""

import json
from typing import Optional, Type, Any
from pydantic import BaseModel, Field
from langchain_core.tools import BaseTool
from utils.codeql import execute_codeql_query


class CodeQLRunnerInput(BaseModel):
    """Input schema for CodeQL Runner Tool."""
    query_content: str = Field(
        description="The complete CodeQL query code to execute"
    )
    database_path: str = Field(
        description="Path to the CodeQL database directory"
    )
    # Added: target language to select proper qlpack dependencies
    language: Optional[str] = Field(
        default=None,
        description="Target language for the query ('java', 'python', 'c'). If omitted, it will be auto-detected from the query content."
    )


class CodeQLRunnerTool(BaseTool):
    """Tool for executing CodeQL queries against a database."""
    
    name: str = "codeql_runner"
    description: str = (
        "Executes CodeQL query code against a specified CodeQL database via 'codeql database analyze'. "
        "Produces a SARIF v2.1.0 report at /output/result_YYYYMMDD_HHMMSS.sarif. "
        "Input should include the complete CodeQL query code and the path to the database. "
        "Optionally specify the target language (java/python/c); otherwise language will be auto-detected. "
        "Returns the SARIF file path and any stdout/stderr details."
    )
    args_schema: Type[BaseModel] = CodeQLRunnerInput
    
    def _format_results(self, execution_result: dict) -> str:
        """
        Format the execution results into a readable string.
        
        Args:
            execution_result: Dictionary containing execution results from execute_codeql_query
            
        Returns:
            Formatted string representation of the results
        """
        sarif_path = execution_result.get('sarif_path')
        if not execution_result['success']:
            return (
                f"Execution failed: {execution_result.get('output','')}. "
                f"SARIF: {sarif_path}" if sarif_path else f"Execution failed: {execution_result.get('output','')}"
            )

        # Report SARIF location and any stdout
        lines = [
            "CodeQL analyze completed successfully.",
            f"SARIF: {sarif_path}" if sarif_path else "SARIF: <not available>",
        ]
        if execution_result.get('output'):
            lines.extend(["", "stdout:", execution_result['output']])
        return "\n".join(lines)
    
    def _run(
        self,
        query_content: str,
        database_path: str,
        language: Optional[str] = None,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        Synchronously execute CodeQL query.
        
        Args:
            query_content: The complete CodeQL query code
            database_path: Path to the CodeQL database
            language: Target language (java/python/cpp). If None, auto-detect from query.
            run_manager: Callback manager for tool execution
            
        Returns:
            Formatted execution results or error message
        """
        try:
            result = execute_codeql_query(query_content, database_path, language=language)
            return self._format_results(result)
        except Exception as e:
            return f"Error executing CodeQL query: {str(e)}"
    
    async def _arun(
        self,
        query_content: str,
        database_path: str,
        language: Optional[str] = None,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        Asynchronously execute CodeQL query.
        
        Args:
            query_content: The complete CodeQL query code
            database_path: Path to the CodeQL database
            language: Target language (java/python/c). If None, auto-detect from query.
            run_manager: Async callback manager for tool execution
            
        Returns:
            Formatted execution results or error message
        """
        # Since execute_codeql_query is synchronous, we just call the sync version
        # In a production environment, you might want to run this in an executor
        # to avoid blocking the event loop
        return self._run(query_content, database_path, language=language, run_manager=None)

