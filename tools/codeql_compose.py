"""LangChain 工具：根据自然语言需求生成并验证 CodeQL 查询。"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, Optional, Type, Callable

from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent
from agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent
from services import (
    PythonKnowledgeBase,
    build_placeholder_map,
    apply_placeholders,
    CodeQLSyntaxSession,
    CodeQLExecutionService,
    CodeQLExecutionResult,
)
from utils.codeql import (
    create_temporary_qlpack,
    execute_codeql_query,
    run_query_and_decode_to_text,
)


class CodeQLComposeInput(BaseModel):
    """Tool 参数：自然语言描述 CodeQL 需求。"""

    requirement: str = Field(
        description=(
            "CodeQL 查询需求的自然语言描述，例如：'查找所有用户输入源'。"
        )
    )


class CodeQLComposeTool(BaseTool):
    """生成、校验并执行 CodeQL 查询的 LangChain 工具。"""

    name: str = "codeql_compose"
    description: str = (
        "根据自然语言需求自动生成 CodeQL 查询，并通过语法校验与执行结果迭代修复直至成功。"
    )
    args_schema: Type[BaseModel] = CodeQLComposeInput

    analyzer: Optional[Any] = None
    default_language: str = "java"
    default_database_path: str = ""
    default_max_rounds: int = 5

    def __init__(
        self,
        analyzer: Optional[Any] = None,
        database_path: str = "",
        language: str = "java",
        max_rounds: int = 5,
        *,
        gen_agent_cls: Type[CodeQLGenAgent] = CodeQLGenAgent,
        error_agent_cls: Type[CodeQLErrorAgent] = CodeQLErrorAgent,
        syntax_session_cls: Type[CodeQLSyntaxSession] = CodeQLSyntaxSession,
        execution_service_factory: Optional[
            Callable[[str, str], CodeQLExecutionService]
        ] = None,
        execute_fn: Callable[[str, str, Optional[str]], Dict[str, Any]] = execute_codeql_query,
        decode_fn: Optional[
            Callable[[str, str, Optional[str]], Dict[str, Any]]
        ] = run_query_and_decode_to_text,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.analyzer = analyzer
        self.default_database_path = database_path
        self.default_language = language
        self.default_max_rounds = max_rounds

        self._gen_agent_cls = gen_agent_cls
        self._error_agent_cls = error_agent_cls
        self._syntax_session_cls = syntax_session_cls
        self._execution_service_factory = (
            execution_service_factory or self._default_execution_service_factory
        )
        self._execute_fn = execute_fn
        self._decode_fn = decode_fn

        self._python_kb: Optional[PythonKnowledgeBase] = None

    def _default_execution_service_factory(
        self,
        database_path: str,
        language: str,
    ) -> CodeQLExecutionService:
        return CodeQLExecutionService(
            database_path=database_path,
            language=language,
            execute_fn=self._execute_fn,
            decode_fn=self._decode_fn,
        )

    def _get_python_kb_context(self, requirement: str, project_root: Path) -> Dict[str, str]:
        if self._python_kb is None or self._python_kb.repo_root != project_root:
            self._python_kb = PythonKnowledgeBase(project_root)
        try:
            return self._python_kb.build_context(requirement)
        except Exception:
            return {}

    @staticmethod
    def _extract_codeql_from_response(content: str) -> Optional[str]:
        match = re.search(r"```ql\s*(.*?)```", content, re.DOTALL)
        if match:
            code = match.group(1).strip()
            if code.startswith("\n"):
                code = code[1:]
            return code

        match = re.search(r"<codeql>(.*?)</codeql>", content, re.DOTALL)
        if match:
            return match.group(1).strip()

        match = re.search(r"```\s*(.*?)```", content, re.DOTALL)
        if match:
            code = match.group(1).strip()
            if code.startswith("\n"):
                code = code[1:]
            return code

        return None

    @staticmethod
    def _format_error_info(error_output: str, round_index: int) -> str:
        return f"Round {round_index} Error:\n{error_output}"

    @staticmethod
    def _format_success_result(
        query: str,
        round_index: int,
        execution: CodeQLExecutionResult,
    ) -> str:
        result = (
            f"CodeQL query successfully generated and executed after {round_index} round(s):\n\n"
            f"```ql\n{query}\n```"
        )

        if execution.sarif_path:
            result += f"\n\nSARIF output saved to: {execution.sarif_path}"
        if execution.json_path:
            result += f"\n路径 JSON 输出: {execution.json_path}"
            if execution.paths_count is not None:
                result += f"，共 {execution.paths_count} 条路径"
        if execution.result_file:
            result += f"\nText results saved to: {execution.result_file}"
        if execution.preview:
            result += f"\nPreview:\n\n```\n{execution.preview}\n```"
        return result

    def _load_ql_template(self, lang: str) -> str:
        try:
            target = (lang or "").lower()
            prompts_dir = Path(__file__).parent.parent / "prompts"
            java_path = prompts_dir / "java_temple_ql.md"
            py_path = prompts_dir / "python_template_ql.md"
            c_path = prompts_dir / "c_template_ql.md"

            if target == "python" and py_path.exists():
                return py_path.read_text(encoding="utf-8")
            if target in {"c", "cpp"} and c_path.exists():
                return c_path.read_text(encoding="utf-8")
            return java_path.read_text(encoding="utf-8") if java_path.exists() else ""
        except Exception:
            try:
                fallback = Path(__file__).parent.parent / "prompts" / "java_temple_ql.md"
                return fallback.read_text(encoding="utf-8") if fallback.exists() else ""
            except Exception:
                return ""

    def _run(self, *args, **kwargs) -> str:  # pragma: no cover - sync 用不到
        return "Synchronous execution not supported. Please use async version (arun)."

    async def _arun(
        self,
        requirement: str,
        run_manager: Optional[Any] = None,
        exec_mode: str = "analyze",
        show_thinking: bool = False,
    ) -> str:
        if not self.analyzer:
            return "Error: No analyzer configured. Tool needs to be initialized with a MultiAgentAnalyzer instance."

        if not self.default_database_path:
            return "Error: No database path configured. Tool needs to be initialized with a valid database_path."

        target_language = self.default_language
        max_iterations = self.default_max_rounds

        project_root = Path(__file__).parent.parent
        kb_context: Dict[str, str] = {}
        if (target_language or "").lower() == "python":
            kb_context = self._get_python_kb_context(requirement, project_root)

        gen_agent = self._gen_agent_cls(self.analyzer)
        error_agent = self._error_agent_cls(self.analyzer)
        execution_service = self._execution_service_factory(
            self.default_database_path,
            target_language,
        )

        ql_template = self._load_ql_template(target_language)
        round_index = 1
        prev_original_ql = None
        prev_fix_suggestions = None

        query_file = create_temporary_qlpack("", language=target_language)
        pack_root = query_file.parent

        try:
            with self._syntax_session_cls(pack_root) as syntax_session:
                while round_index <= max_iterations:
                    gen_prompt_base = gen_agent.build_prompt(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                    )
                    gen_values = build_placeholder_map(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                        ql_template=ql_template,
                        kb_directory_index=kb_context.get("kb_directory_index"),
                        kb_suggestions=kb_context.get("kb_suggestions"),
                        relevant_tags=kb_context.get("relevant_tags"),
                    )
                    gen_prompt = apply_placeholders(gen_prompt_base, gen_values)
                    gen_result = await self.analyzer.run_agent(gen_prompt, show_thinking=show_thinking)

                    if not gen_result.success:
                        return f"Error in CodeQL generation (Round {round_index}): {gen_result.error or 'Unknown error'}"

                    current_ql = self._extract_codeql_from_response(gen_result.content)
                    if not current_ql:
                        return f"Error: Could not extract CodeQL code from generation result (Round {round_index})"

                    syntax_result = syntax_session.check(current_ql)
                    if "error" in syntax_result:
                        exec_result = CodeQLExecutionResult(
                            success=False,
                            output=syntax_result["error"],
                        )
                    else:
                        diagnostics = syntax_result.get("diagnostics", [])
                        errors = [d for d in diagnostics if d.get("severity", 1) == 1]
                        if errors:
                            error_messages = []
                            for error in errors:
                                message = error.get("message", "Unknown error")
                                range_info = error.get("range", {})
                                start = range_info.get("start", {})
                                line = start.get("line", 0) + 1
                                character = start.get("character", 0) + 1
                                error_messages.append(f"Line {line}, Column {character}: {message}")
                            exec_result = CodeQLExecutionResult(
                                success=False,
                                output="\n".join(error_messages) or "Syntax errors detected",
                            )
                        else:
                            exec_result = execution_service.execute(current_ql, exec_mode=exec_mode)

                    if exec_result.success:
                        return self._format_success_result(
                            query=current_ql,
                            round_index=round_index,
                            execution=exec_result,
                        )

                    if round_index >= max_iterations:
                        error_info = self._format_error_info(exec_result.output, round_index)
                        return (
                            f"Failed to generate working CodeQL query after {max_iterations} rounds.\n\n"
                            f"Final error:\n{error_info}\n\n"
                            f"Last attempted query:\n```ql\n{current_ql}\n```"
                        )

                    error_prompt_base = error_agent.build_prompt(
                        error_log=exec_result.output,
                        curr_ql_content=current_ql,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                    )
                    error_values = build_placeholder_map(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                        ql_template=ql_template,
                        error_log=exec_result.output,
                        curr_ql_content=current_ql,
                        kb_directory_index=kb_context.get("kb_directory_index"),
                        kb_suggestions=kb_context.get("kb_suggestions"),
                        relevant_tags=kb_context.get("relevant_tags"),
                    )
                    error_prompt = apply_placeholders(error_prompt_base, error_values)
                    error_analysis = await self.analyzer.run_agent(error_prompt, show_thinking=show_thinking)

                    if not error_analysis.success:
                        return (
                            f"Error in error analysis (Round {round_index}): "
                            f"{error_analysis.error or 'Unknown error'}"
                        )

                    prev_original_ql = current_ql
                    prev_fix_suggestions = error_analysis.content
                    round_index += 1

        except RuntimeError as runtime_error:
            return f"Error: {runtime_error}"
        except Exception as exc:
            return f"Error during CodeQL composition: {exc}"

        return f"Unexpected end of iteration loop after {max_iterations} rounds"
