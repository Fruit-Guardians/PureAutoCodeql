"""用于执行CodeQL查询并分析结果的Agent。"""

from pathlib import Path
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None
    
    class MultiAgentAnalyzer:
        pass


class CodeQLRunnerAgent:
    """用于执行CodeQL查询并分析结果的Agent。"""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer", database_path: str = "h5-vsan"):
        self.analyzer = analyzer
        self.database_path = database_path
    
    def build_prompt(self, query_content: str, execution_result: str) -> str:
        """构建提示词来分析CodeQL执行结果。"""
        return f"""
你是CodeQL专家。请分析CodeQL查询的执行结果。

已执行的CodeQL查询：
```codeql
{query_content}
```

执行结果：
```
{execution_result}
```

请分析结果并提供：
1. 发现总结（检测到哪些漏洞或问题）
2. 严重性评估
3. 修复建议
4. 任何误报或分析局限性

专注于提供可操作的安全分析见解。
"""
    
    async def execute_and_analyze(self, query_content: str, database_path: Optional[str] = None, max_retries: int = 3) -> "AgentResult":
        """执行CodeQL查询并分析结果，支持错误纠正。"""
        try:
            db_path = database_path or self.database_path
            
            # 执行CodeQL查询
            from tools.codeql_runner_tool import CodeQLRunnerTool
            runner_tool = CodeQLRunnerTool()
            execution_result = runner_tool._run(
                query_content=query_content,
                database_path=db_path,
                language="java"
            )
            
            print(execution_result)
            # 检查执行结果是否包含错误
            if self._is_execution_error(execution_result):
                print("检测到执行错误，尝试纠正...")
                
                # 尝试纠正错误
                corrected_query = await self._correct_and_retry(
                    query_content, execution_result, db_path, max_retries
                )
                
                if corrected_query:
                    print("使用纠正后的查询重新执行...")
                    # 使用纠正后的查询重新执行
                    execution_result = runner_tool._run(
                        query_content=corrected_query,
                        database_path=db_path,
                        language="java"
                    )
                    
                    # 检查纠正后的结果是否仍有错误
                    print(execution_result)
                    if self._is_execution_error(execution_result):
                        return AgentResult(
                            content=f"纠正后仍有错误：{execution_result}",
                            success=False,
                            error="无法纠正CodeQL查询错误"
                        )
                else:
                    return AgentResult(
                        content=f"原始查询执行错误：{execution_result}",
                        success=False,
                        error="无法纠正CodeQL查询错误"
                    )
            
            # 构建分析提示词
            prompt = self.build_prompt(query_content, execution_result)
            
            # 使用LLM分析结果
            analysis_result = await self.analyzer.run_agent(prompt)
            
            return analysis_result
            
        except Exception as e:
            return AgentResult(content="", success=False, error=str(e))
    
    def _is_execution_error(self, execution_result: str) -> bool:
        """检查执行结果是否包含错误。"""
        error_indicators = [
            "Execution failed",
            "Error executing",
            "cannot be resolved",
            "syntax error",
            "compilation error",
            "does not exist",
            "not found",
            "timed out"
        ]
        return any(indicator in execution_result for indicator in error_indicators)
    
    async def _correct_and_retry(self, original_query: str, error_result: str, database_path: str, max_retries: int) -> Optional[str]:
        """纠正CodeQL查询错误并重试。"""
        if max_retries <= 0:
            print("已达到最大重试次数，放弃纠正")
            return None
        
        # 构建纠正提示词
        correction_prompt = f"""
你是CodeQL专家。请分析CodeQL查询执行错误并纠正查询。

原始查询：
```codeql
{original_query}
```

执行错误：
```
{error_result}
```

请分析错误原因，纠正查询语法问题，并生成正确的CodeQL查询代码。
重点关注：
- 语法错误修复
- 导入语句修正
- 方法调用修正（使用MethodCall而非MethodAccess）
- 类型引用修正

只输出纠正后的CodeQL代码，用<codeql></codeql>标签包裹。
"""
        
        try:
            # 使用LLM纠正错误
            correction_result = await self.analyzer.run_agent(correction_prompt)
            
            if correction_result.success:
                # 提取纠正后的查询
                import re
                match = re.search(r'<codeql>(.*?)</codeql>', correction_result.content, re.DOTALL)
                if match:
                    corrected_query = match.group(1).strip()
                    print(f"纠正后的查询：{corrected_query}")
                    
                    # 测试纠正后的查询
                    from tools.codeql_runner_tool import CodeQLRunnerTool
                    runner_tool = CodeQLRunnerTool()
                    test_result = runner_tool._run(
                        query_content=corrected_query,
                        database_path=database_path,
                        language="java"
                    )
                    
                    # 如果测试仍有错误，递归纠正
                    print(f"测试结果：{test_result}")
                    if self._is_execution_error(test_result):
                        print("纠正后仍有错误，继续纠正...")
                        return await self._correct_and_retry(
                            corrected_query, test_result, database_path, max_retries - 1
                        )
                    
                    return corrected_query
            
            return None
            
        except Exception as e:
            print(f"纠正过程中出错：{e}")
            return None
    
    async def execute_codeql_only(self, query_content: str, database_path: Optional[str] = None) -> str:
        """Execute a CodeQL query and return raw results (without LLM analysis)."""
        try:
            from tools.codeql_runner_tool import CodeQLRunnerTool
            
            db_path = database_path or self.database_path
            runner_tool = CodeQLRunnerTool()
            return runner_tool._run(
                query_content=query_content,
                database_path=db_path,
                language="java"
            )
            
        except Exception as e:
            return f"Error executing CodeQL query: {str(e)}"