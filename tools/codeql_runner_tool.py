"""用于执行CodeQL查询的LangChain工具。"""

import json
from typing import Optional, Type, Any
from pydantic import BaseModel, Field
from langchain_core.tools import BaseTool
from utils.codeql import execute_codeql_query


class CodeQLRunnerInput(BaseModel):
    """CodeQL运行器工具的输入模式。"""
    query_content: str = Field(
        description="要执行的完整CodeQL查询代码"
    )
    database_path: str = Field(
        description="CodeQL数据库目录的路径"
    )
    # Added: target language to select proper qlpack dependencies
    language: Optional[str] = Field(
        default=None,
        description="查询的目标语言（'java', 'python', 'c'）。如果省略，将从查询内容中自动检测。"
    )


class CodeQLRunnerTool(BaseTool):
    """用于对数据库执行CodeQL查询的工具。"""
    
    name: str = "codeql_runner"
    description: str = (
        "通过'codeql database analyze'对指定的CodeQL数据库执行CodeQL查询代码。 "
        "在/output/result_YYYYMMDD_HHMMSS.sarif生成SARIF v2.1.0报告。 "
        "输入应包括完整的CodeQL查询代码和数据库路径。 "
        "可选指定目标语言（java/python/c）；否则将自动检测语言。 "
        "返回SARIF文件路径和任何stdout/stderr详细信息。"
    )
    args_schema: Type[BaseModel] = CodeQLRunnerInput
    
    def _format_results(self, execution_result: dict) -> str:
        """
        将执行结果格式化为可读字符串。
        
        Args:
            execution_result: 包含execute_codeql_query执行结果的字典
            
        Returns:
            格式化后的结果字符串表示
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
        同步执行CodeQL查询。
        
        Args:
            query_content: 完整的CodeQL查询代码
            database_path: CodeQL数据库的路径
            language: 目标语言（java/python/cpp）。如果为None，则从查询中自动检测。
            run_manager: 工具执行的回调管理器
            
        Returns:
            格式化的执行结果或错误消息
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
        异步执行CodeQL查询。
        
        Args:
            query_content: 完整的CodeQL查询代码
            database_path: CodeQL数据库的路径
            language: 目标语言（java/python/c）。如果为None，则从查询中自动检测。
            run_manager: 异步工具执行的回调管理器
            
        Returns:
            格式化的执行结果或错误消息
        """
        # Since execute_codeql_query is synchronous, we just call the sync version
        # In a production environment, you might want to run this in an executor
        # to avoid blocking the event loop
        return self._run(query_content, database_path, language=language, run_manager=None)

