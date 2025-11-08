"""LangChain 工具：根据自然语言需求生成并验证 CodeQL 查询。"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, Optional, Type, Callable

# Service
from services.lsp_service import CodeQLLSPService
from services.knowledge_base.base import LanguageKnowledgeBase


from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent
from agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent
from config import get_sarif2json_config
from services import (
    KnowledgeBaseFactory,
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
    validate_codeql_database,
    is_database_error,
)
from utils.sarif_utils import write_paths_json


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

        self._kb_cache: Dict[str, LanguageKnowledgeBase] = {}

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

    def _get_kb_context(self, requirement: str, project_root: Path, language: str) -> Dict[str, str]:
        """Return structured KB context for the given language, if available."""
        lang = (language or "").lower()
        if not lang:
            return {}

        cached = self._kb_cache.get(lang)
        if cached is None or cached.repo_root != project_root:
            kb = KnowledgeBaseFactory.get(lang, project_root)
            if kb is None:
                return {}
            self._kb_cache[lang] = kb
            cached = kb

        try:
            return cached.build_context(requirement)
        except Exception:
            return {}

    @staticmethod
    def _preflight_validate_query(query: str, language: str) -> Optional[str]:
        """Lightweight structural checks before invoking the LSP."""
        if not query:
            return "Query body is empty."

        issues: List[str] = []
        normalized = query.lower()

        if "@kind path-problem" not in normalized:
            issues.append("Missing '@kind path-problem' metadata block.")
        if "module flow =" not in normalized:
            issues.append("Missing 'module Flow = TaintTracking::Global<...>' definition.")
        if "select " not in normalized:
            issues.append("Missing 'select' result clause.")
        if "import flow::pathgraph" not in normalized:
            issues.append("Missing 'import Flow::PathGraph'.")

        target_lang = (language or "").lower()
        if target_lang == "python":
            required_imports = [
                "import python",
                "import semmle.python.dataflow.new.dataflow",
                "import semmle.python.dataflow.new.tainttracking",
            ]
            for token in required_imports:
                if token not in normalized:
                    issues.append(f"Missing required Python import '{token}'.")

            has_parameter_node = "dataflow::parameternode" in normalized
            has_remote_source = (
                "import semmle.python.dataflow.new.remoteflowsources" in normalized
                or "remoteflowsource" in normalized
            )
            if not (has_parameter_node or has_remote_source):
                issues.append(
                    "Python sources must use either DataFlow::ParameterNode or RemoteFlowSource."
                )

            other_required_tokens = [
                "dataflow::callcfgnode",
                "dataflow::attrread",
                "getlocation().getfile()",
            ]
            for token in other_required_tokens:
                if token not in normalized:
                    issues.append(f"Missing required Python dataflow token '{token}'.")

            blacklist_patterns = {
                r"(?<!DataFlow::)ParameterNode": "Use 'DataFlow::ParameterNode' with explicit namespace and '.()' casting.",
                r"\bMethodCall\b": "Python new dataflow API does not expose 'MethodCall'; use 'DataFlow::CallCfgNode' + AttrRead instead.",
                r"\bgetFile\s*\(": "Call 'node.getLocation().getFile()' instead of 'getFile()'.",
            }
            for pattern, message in blacklist_patterns.items():
                if re.search(pattern, query):
                    issues.append(message)

        if not issues:
            return None
        return "Preflight validation failed:\n" + "\n".join(f"- {msg}" for msg in issues)

    def _lsp_and_execute(
        self,
        current_ql: str,
        target_language: str,
        query_file: Path,
        lsp_service: CodeQLLSPService,
    ) -> Dict[str, Any]:
        """Run LSP diagnostics and execute the query when syntax passes."""
        print(f"🔍 [CodeQLComposeTool] 开始使用LSP进行语法检查并执行...")

        try:
            print(f"🔍 [CodeQLComposeTool] 执行CodeQL语法检查...")
            syntax_result = lsp_service.check_syntax(current_ql)
            print(syntax_result)

            if "error" in syntax_result:
                print(f"❌ [CodeQLComposeTool] LSP语法检查失败: {syntax_result['error']}")
                return {"success": False, "output": syntax_result["error"]}

            diagnostics = syntax_result.get("diagnostics", [])
            errors = [d for d in diagnostics if d.get("severity", 1) == 1]
            warnings = [d for d in diagnostics if d.get("severity", 2) == 2]
            infos = [d for d in diagnostics if d.get("severity", 3) == 3]
            hints = [d for d in diagnostics if d.get("severity", 4) == 4]

            print("📊 [CodeQLComposeTool] 语法检查摘要:")
            print(f"   - 错误: {len(errors)} 个")
            print(f"   - 警告: {len(warnings)} 个")
            print(f"   - 信息: {len(infos)} 个")
            print(f"   - 提示: {len(hints)} 个")

            if diagnostics:
                print("\n📋 [CodeQLComposeTool] 详细诊断信息:")
                for i, diag in enumerate(diagnostics, 1):
                    severity = diag.get("severity", 1)
                    severity_label = {1: "错误", 2: "警告", 3: "信息", 4: "提示"}.get(severity, "未知")
                    message = diag.get("message", "Unknown message")
                    range_info = diag.get("range", {})
                    start = range_info.get("start", {})
                    line = start.get("line", 0) + 1
                    character = start.get("character", 0) + 1
                    print(f"   {i}. [{severity_label}] 第{line}行第{character}列: {message}")
            else:
                print("✅ [CodeQLComposeTool] 未发现任何诊断信息")

            if errors:
                error_messages = []
                for error in errors:
                    message = error.get("message", "Unknown error")
                    range_info = error.get("range", {})
                    start = range_info.get("start", {})
                    line = start.get("line", 0) + 1
                    character = start.get("character", 0) + 1
                    error_messages.append(f"Line {line}, Column {character}: {message}")

                print(f"❌ [CodeQLComposeTool] 发现 {len(errors)} 个语法错误")
                return {"success": False, "output": "\n".join(error_messages)}

            print(f"✅ [CodeQLComposeTool] 语法检查通过")
            print(f"🚀 [CodeQLComposeTool] 开始实际执行CodeQL查询...")

            exec_result = execute_codeql_query(
                current_ql,
                self.default_database_path,
                target_language,
                query_file,
            )

            if not exec_result.get("success", False):
                error_output = exec_result.get("output", "")
                if is_database_error(error_output):
                    print(f"⚠️ [CodeQLComposeTool] 检测到数据库错误")

            print(f"🏁 [CodeQLComposeTool] CodeQL查询执行完成")
            return exec_result

        except Exception as e:
            error_str = str(e)
            if is_database_error(error_str):
                enhanced_error = (
                    f"数据库错误: {error_str}\n\n"
                    f"建议:\n"
                    f"1. 检查数据库路径: {self.default_database_path}\n"
                    f"2. 使用 'codeql database info {self.default_database_path}' 验证数据库\n"
                    f"3. 如果数据库不存在或损坏，请使用 'codeql database create' 重新创建"
                )
                return {"success": False, "output": enhanced_error}

            print(f"❌ [CodeQLComposeTool] 语法检查过程中发生异常: {error_str}")
            return {"success": False, "output": f"Syntax check failed: {error_str}"}

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
        exec_mode: str = "analyze",
        show_thinking: bool = False,
        event_callback = None,
    ) -> str:
        if not self.analyzer:
            return "Error: No analyzer configured. Tool needs to be initialized with a MultiAgentAnalyzer instance."

        if not self.default_database_path:
            return "Error: No database path configured. Tool needs to be initialized with a valid database_path."

        # 在执行前验证数据库
        print(f"🔍 [CodeQLComposeTool] 验证数据库: {self.default_database_path}")
        is_valid, validation_error = validate_codeql_database(self.default_database_path)
        if not is_valid:
            error_msg = (
                f"❌ [CodeQLComposeTool] 数据库验证失败\n\n"
                f"{validation_error}\n\n"
                f"请在继续之前修复数据库问题。"
            )
            print(error_msg)
            return error_msg

        if validation_error:  # 有警告但数据库有效
            print(f"⚠️  [CodeQLComposeTool] {validation_error}")

        target_language = self.default_language
        max_iterations = self.default_max_rounds

        project_root = Path(__file__).parent.parent
        kb_context: Dict[str, str] = self._get_kb_context(requirement, project_root, target_language)

        gen_agent = self._gen_agent_cls(self.analyzer)
        error_agent = self._error_agent_cls(self.analyzer)

        ql_template = self._load_ql_template(target_language)
        round_index = 1
        prev_original_ql = None
        prev_fix_suggestions = None

        query_file = create_temporary_qlpack("", language=target_language)
        pack_root = query_file.parent

        # 启动LSP服务
        print(f"📁 [CodeQLComposeTool] 临时目录: {pack_root}")
        print(f"   [CodeQLComposeTool] 启动LSP服务进行语法检查...")
        print(f"   [CodeQLComposeTool] 正在初始化LSP服务，请稍候...")
        lsp_service = CodeQLLSPService(pack_root, query_file)
        
        # 添加详细的进度指示
        import time
        start_time = time.time()
        print(f"   [CodeQLComposeTool] 开始启动LSP服务进程...")
        

        # 调用start_server方法，它内部已经有30秒的重试机制
        if lsp_service.start():
            elapsed_time = time.time() - start_time
            print(f"✅ [CodeQLComposeTool] LSP服务启动成功 (耗时: {elapsed_time:.1f}秒)")
        else:
            print("❌ [CodeQLComposeTool] LSP服务启动失败")
            return f"Error: Failed to start LSP service for syntax checking"

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
                        kb_structured_context=kb_context.get("kb_structured_context"),
                        kb_reference_snippets=kb_context.get("kb_reference_snippets"),
                        relevant_tags=kb_context.get("relevant_tags"),
                    )
                    gen_prompt = apply_placeholders(gen_prompt_base, gen_values)
                    gen_result = await self.analyzer.run_agent(gen_prompt, show_thinking=show_thinking, event_callback=event_callback)

                    if not gen_result.success:
                        return f"Error in CodeQL generation (Round {round_index}): {gen_result.error or 'Unknown error'}"

                    current_ql = self._extract_codeql_from_response(gen_result.content)
                    if not current_ql:
                        return f"Error: Could not extract CodeQL code from generation result (Round {round_index})"
                    

                    preflight_msg = self._preflight_validate_query(current_ql, target_language)
                    if preflight_msg:
                        exec_result = {"success": False, "output": preflight_msg}
                    else:
                        exec_result = self._lsp_and_execute(
                            current_ql=current_ql,
                            target_language=target_language,
                            query_file=query_file,
                            lsp_service=lsp_service,
                        )

                    # Check execution result
                    if exec_result.get('success', False):
                        mode_now = (exec_mode or 'analyze').lower()
                        
                        # 创建CodeQLExecutionResult对象
                        from services.codeql_execution import CodeQLExecutionResult
                        execution_result = CodeQLExecutionResult(
                            success=exec_result.get('success', False),
                            output=exec_result.get('output', ''),
                            sarif_path=exec_result.get('sarif_path'),
                            json_path=exec_result.get('json_path'),
                            paths_count=exec_result.get('paths_count'),
                            result_file=exec_result.get('result_file'),
                            preview=exec_result.get('preview')
                        )
                        
                        # 正常结果处理
                        if mode_now == 'run' and run_query_and_decode_to_text:
                            result_file = exec_result.get('result_file')
                            full_text = exec_result.get('output', '') or ''
                            lines = (full_text.splitlines() if isinstance(full_text, str) else [])
                            preview = "\n".join(lines[:40])
                            if len(lines) > 40:
                                preview += "\n..."
                            result = (
                                f"CodeQL query successfully generated and executed after {round_index} round(s):\n\n"
                                f"```ql\n{current_ql}\n```\n\n"
                                f"Text results saved to: {result_file or '(unknown)'}\n"
                            )
                            if preview.strip():
                                result += "Preview:\n\n```\n" + preview + "\n```"
                            return result
                        else:
                            print("codeql query:", current_ql)
                            return self._format_success_result(
                                query=current_ql,
                                round_index=round_index,
                                execution=execution_result,
                            )

                    # 如果语法检查通过但执行失败，继续纠错循环
                    if round_index >= max_iterations:
                        error_info = self._format_error_info(exec_result.get('output', ''), round_index)
                        return (
                            f"Failed to generate working CodeQL query after {max_iterations} rounds.\n\n"
                            f"Final error:\n{error_info}\n\n"
                            f"Last attempted query:\n```ql\n{current_ql}\n```"
                        )

                    if round_index >= max_iterations:
                        error_info = self._format_error_info(exec_result.get('output', ''), round_index)
                        return (
                            f"Failed to generate working CodeQL query after {max_iterations} rounds.\n\n"
                            f"Final error:\n{error_info}\n\n"
                            f"Last attempted query:\n```ql\n{current_ql}\n```"
                        )
                    error_prompt_base = error_agent.build_prompt(
                        error_log=exec_result.get('output', ''),
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
                        error_log=exec_result.get('output', ''),
                        curr_ql_content=current_ql,
                        kb_directory_index=kb_context.get("kb_directory_index"),
                        kb_suggestions=kb_context.get("kb_suggestions"),
                        kb_structured_context=kb_context.get("kb_structured_context"),
                        kb_reference_snippets=kb_context.get("kb_reference_snippets"),
                        relevant_tags=kb_context.get("relevant_tags"),
                    )
                    error_prompt = apply_placeholders(error_prompt_base, error_values)
                    error_analysis = await self.analyzer.run_agent(error_prompt, show_thinking=show_thinking, event_callback=event_callback)

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
        finally:
            # 在整个多轮查询过程结束后停止LSP服务
            print("🛑 [CodeQLComposeTool] 停止LSP服务...")
            lsp_service.stop()

        return f"Unexpected end of iteration loop after {max_iterations} rounds"


