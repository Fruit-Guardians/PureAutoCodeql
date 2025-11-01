"""LangChain tool for composing CodeQL queries with iterative generation and execution."""

import re
from pathlib import Path
from typing import Optional, Type, Any, Dict
from pydantic import BaseModel, Field
from langchain_core.tools import BaseTool

from config import get_sarif2json_config
from utils.sarif_utils import write_paths_json


class CodeQLComposeInput(BaseModel):
    """Input schema for CodeQL Compose Tool."""
    requirement: str = Field(
        description="Natural language description of the CodeQL query requirement. "
                    "For example: 'Find all user input sources' or 'Find paths from user input to SQL execution'"
    )


class CodeQLComposeTool(BaseTool):
    """Tool for composing CodeQL queries with iterative generation, execution, and error fixing."""
    
    name: str = "codeql_compose"
    description: str = (
        "Generates and validates CodeQL queries from natural language requirements. "
        "Simply provide a description of what you want to find in the code, "
        "and the tool will automatically generate, test, and refine the query until it works correctly."
    )
    args_schema: Type[BaseModel] = CodeQLComposeInput
    
    # The analyzer will be injected during initialization
    analyzer: Optional[object] = None
    # Internal configuration - not exposed to agents
    default_language: str = "java"
    default_database_path: str = ""
    default_max_rounds: int = 5
    
    def __init__(self, analyzer=None, database_path: str = "", language: str = "java", max_rounds: int = 5, **kwargs):
        """
        Initialize the tool with a MultiAgentAnalyzer instance and internal configuration.
        
        Args:
            analyzer: Instance of MultiAgentAnalyzer that provides LLM capabilities
            database_path: Default CodeQL database path for execution
            language: Default target programming language ('java', 'python', 'cpp')
            max_rounds: Default maximum number of iterative rounds
        """
        super().__init__(**kwargs)
        self.analyzer = analyzer
        self.default_database_path = database_path
        self.default_language = language
        self.default_max_rounds = max_rounds
    
    def _extract_codeql_from_response(self, content: str) -> Optional[str]:
        match = re.search(r'```ql\s*\n(.*?)\n```', content, re.DOTALL)
        if match:
            return match.group(1).strip()
        
        match = re.search(r'<codeql>(.*?)</codeql>', content, re.DOTALL)
        if match:
            return match.group(1).strip()
        
        return None
    
    def _format_error_info(self, error_output: str, round_index: int) -> str:
        """Format error information for better readability."""
        return f"Round {round_index} Error:\n{error_output}"
    
    def _format_success_result(
        self,
        query: str,
        round_index: int,
        sarif_path: Optional[str] = None,
        paths_json_path: Optional[str] = None,
        paths_count: Optional[int] = None,
    ) -> str:
        """Format successful result with query and execution info."""
        result = f"CodeQL query successfully generated and executed after {round_index} round(s):\n\n```ql\n{query}\n```"
        if sarif_path:
            result += f"\n\nSARIF output saved to: {sarif_path}"
        if paths_json_path:
            result += f"\n路径 JSON 输出: {paths_json_path}"
            if paths_count is not None:
                result += f"（包含 {paths_count} 条路径）"
        return result

    def _load_ql_template(self, lang: str) -> str:
        """根据不同语言加载对应的QL模板"""
        from pathlib import Path
        try:
            l = (lang or "").lower()
            prompts_dir = Path(__file__).parent.parent / "agents" / "prompts"
            java_path = prompts_dir / "java_temple_ql.md"
            py_path = prompts_dir / "python_template_ql.md"
            c_path = prompts_dir / "c_template_ql.md"

            if l == "java":
                return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
            if l == "python":
                if py_path.exists():
                    return py_path.read_text(encoding="utf-8")
                return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
            if l == "c":
                if c_path.exists():
                    return c_path.read_text(encoding="utf-8")
                return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
            return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
        except Exception:
            try:
                from pathlib import Path as _P
                java_path = _P(__file__).parent.parent / "agents" / "prompts" / "java_temple_ql.md"
                return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
            except Exception:
                return ""

    @staticmethod
    def _build_placeholder_map(
        language: str,
        requirement: Optional[str],
        round_index: int,
        prev_original_ql: Optional[str],
        prev_fix_suggestions: Optional[str],
        ql_template: str,
        error_log: Optional[str] = None,
        curr_ql_content: Optional[str] = None,
    ) -> Dict[str, str]:
        """Centralize placeholder values for both generation and error-analysis prompts."""
        return {
            "ROUND_INDEX": str(round_index or 1),
            "LANGUAGE": (language or "java"),
            "REQUIREMENT": (requirement or ""),
            "PREV_ORIGINAL_QL": (prev_original_ql or ""),
            "PREV_FIX_SUGGESTIONS": (prev_fix_suggestions or ""),
            "QL_TEMPLATE": (ql_template or ""),
            "ERROR_LOG": (error_log or ""),
            "CURR_QL_CONTENT": (curr_ql_content or ""),
        }

    @staticmethod
    def _apply_placeholders(content: str, values: Dict[str, str]) -> str:
        """Apply [[KEY]] placeholder replacements using a unified function."""
        result = content
        for k, v in (values or {}).items():
            result = result.replace(f"[[{k}]]", v or "")
        return result
    
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
            Error message indicating sync execution is not supported
        """
        return "Synchronous execution not supported. Please use async version (arun)."
    
    async def _arun(
        self,
        requirement: str,
        run_manager: Optional[Any] = None
    ) -> str:
        """
        Asynchronously compose CodeQL query with iterative generation and execution.
        
        Args:
            requirement: Natural language requirement for CodeQL query
            run_manager: Async callback manager for tool execution
            
        Returns:
            Final CodeQL query if successful, or error message with details
        """
        if not self.analyzer:
            return "Error: No analyzer configured. Tool needs to be initialized with a MultiAgentAnalyzer instance."
        
        if not self.default_database_path:
            return "Error: No database path configured. Tool needs to be initialized with a valid database_path."
        
        # Use internal configuration
        target_language = self.default_language
        database_path = self.default_database_path
        max_iterations = self.default_max_rounds
        
        try:
            # 开始进行ql生成循环
            import sys
            import os
            
            # 添加项目根目录到Python路径
            project_root = Path(__file__).parent.parent
            sys.path.insert(0, str(project_root))
            
            # 添加agents目录到路径
            agents_path = project_root / "agents"
            sys.path.insert(0, str(agents_path))
            
            # 直接导入模块，这里最后改成直接import CodeQLGenAgent，为了测试方便先这么写吧
            import importlib.util
            
            # 导入 CodeQLGenAgent
            gen_agent_path = project_root / "agents" / "codeql_gen_agents" / "codeql_gen_agent.py"
            spec = importlib.util.spec_from_file_location("codeql_gen_agent", gen_agent_path)
            gen_agent_module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(gen_agent_module)
            CodeQLGenAgent = gen_agent_module.CodeQLGenAgent
            
            # 导入 CodeQLErrorAgent
            error_agent_path = project_root / "agents" / "codeql_gen_agents" / "codeql_error_agent.py"
            spec = importlib.util.spec_from_file_location("codeql_error_agent", error_agent_path)
            error_agent_module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(error_agent_module)
            CodeQLErrorAgent = error_agent_module.CodeQLErrorAgent
            
            # 导入 execute_codeql_query
            utils_path = project_root / "utils" / "codeql.py"
            spec = importlib.util.spec_from_file_location("codeql_utils", utils_path)
            utils_module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(utils_module)
            execute_codeql_query = utils_module.execute_codeql_query
            
            gen_agent = CodeQLGenAgent(self.analyzer)
            error_agent = CodeQLErrorAgent(self.analyzer)
            
            ql_template = self._load_ql_template(target_language)
            print("ql_template:", ql_template)
            
            round_index = 1
            prev_original_ql = None
            prev_fix_suggestions = None
            
            while round_index <= max_iterations:
                try:
                    gen_prompt_base = gen_agent.build_prompt(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                    )
                    # Centralized placeholder map and application
                    gen_values = self._build_placeholder_map(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                        ql_template=ql_template,
                    )
                    gen_prompt = self._apply_placeholders(gen_prompt_base, gen_values)
                    gen_result = await self.analyzer.run_agent(gen_prompt)
                    
                    if not gen_result.success:
                        return f"Error in CodeQL generation (Round {round_index}): {gen_result.error or 'Unknown error'}"
                    
                    # Extract CodeQL code from response
                    current_ql = self._extract_codeql_from_response(gen_result.content)
                    if not current_ql:
                        return f"Error: Could not extract CodeQL code from generation result (Round {round_index})"
                    
                    # Execute CodeQL query
                    exec_result = execute_codeql_query(
                        query_content=current_ql,
                        database_path=database_path,
                        language=target_language
                    )
                    
                    # Check execution result
                    if exec_result.get('success', False):
                        sarif_path = exec_result.get('sarif_path')
                        json_path: Optional[str] = None
                        paths_count: Optional[int] = None

                        if sarif_path:
                            try:
                                # 成功执行后自动导出同名 JSON，方便后续可视化.
                                config = get_sarif2json_config()
                                sarif_file = Path(sarif_path)
                                json_file = sarif_file.with_suffix('.json')
                                paths_count = write_paths_json(
                                    sarif_path,
                                    str(json_file),
                                    max_results=config.max_results,
                                    threadflow_index=config.threadflow_index,
                                    rule_filter=config.rule_filter,
                                    relative_to=None,
                                )
                                json_path = str(json_file)
                            except Exception as convert_error:
                                print(f"SARIF->JSON 转换失败: {convert_error}")

                        # Success! Return the final query
                        return self._format_success_result(
                            query=current_ql,
                            round_index=round_index,
                            sarif_path=sarif_path,
                            paths_json_path=json_path,
                            paths_count=paths_count,
                        )
                    else:
                        # Execution failed, analyze error if not at max rounds
                        if round_index >= max_iterations:
                            error_info = self._format_error_info(exec_result.get('output', 'Unknown error'), round_index)
                            return f"Failed to generate working CodeQL query after {max_iterations} rounds.\n\nFinal error:\n{error_info}\n\nLast attempted query:\n```ql\n{current_ql}\n```"
                        
                        # Analyze error for next iteration
                        error_output = exec_result.get('output', 'Unknown execution error')
                        
                        err_prompt_base = error_agent.build_prompt(
                            error_log=error_output,
                            curr_ql_content=current_ql,
                            round_index=round_index,
                            prev_original_ql=prev_original_ql,
                        )
                        err_values = self._build_placeholder_map(
                            language=target_language,
                            requirement=requirement,
                            round_index=round_index,
                            prev_original_ql=prev_original_ql,
                            prev_fix_suggestions=prev_fix_suggestions,
                            ql_template=ql_template,
                            error_log=error_output,
                            curr_ql_content=current_ql,
                        )
                        err_prompt = self._apply_placeholders(err_prompt_base, err_values)
                        error_analysis_result = await self.analyzer.run_agent(err_prompt)
                        
                        if not error_analysis_result.success:
                            return f"Error in error analysis (Round {round_index}): {error_analysis_result.error or 'Unknown error'}"
                        
                        # Prepare for next iteration
                        prev_original_ql = current_ql
                        prev_fix_suggestions = error_analysis_result.content
                        round_index += 1
                
                except Exception as e:
                    return f"Error during iteration {round_index}: {str(e)}"
            
            # Should not reach here, but just in case
            return f"Unexpected end of iteration loop after {max_iterations} rounds"
        
        except Exception as e:
            return f"Error during CodeQL composition: {str(e)}"