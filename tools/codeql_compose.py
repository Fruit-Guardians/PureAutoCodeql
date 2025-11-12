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
from agents.codeql_gen_agents.codeql_fix_inplace_agent import CodeQLFixInplaceAgent
from services import (
    KnowledgeBaseFactory,
    build_placeholder_map,
    apply_placeholders,
    CodeQLSyntaxSession,
)
from utils.codeql import (
    create_temporary_qlpack,
    execute_codeql_query,
    run_query_and_decode_to_text,
    validate_codeql_database,
    is_database_error,
    save_query_to_persistent_dir,
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
        fix_inplace_agent_cls: Type[CodeQLFixInplaceAgent] = CodeQLFixInplaceAgent,
        syntax_session_cls: Type[CodeQLSyntaxSession] = CodeQLSyntaxSession,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.analyzer = analyzer
        self.default_database_path = database_path
        self.default_language = language
        self.default_max_rounds = max_rounds

        self._gen_agent_cls = gen_agent_cls
        self._error_agent_cls = error_agent_cls
        self._fix_inplace_agent_cls = fix_inplace_agent_cls
        self._syntax_session_cls = syntax_session_cls

        self._kb_cache: Dict[str, LanguageKnowledgeBase] = {}

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
                # 构建详细的LSP诊断信息，包含完整的JSON格式以便错误分析Agent使用
                error_details = []
                error_messages = []  # 初始化可读格式的错误消息列表
                for error in errors:
                    message = error.get("message", "Unknown error")
                    range_info = error.get("range", {})
                    start = range_info.get("start", {})
                    line = start.get("line", 0) + 1
                    character = start.get("character", 0) + 1
                    end = range_info.get("end", {})
                    end_line = end.get("line", 0) + 1
                    end_character = end.get("character", 0) + 1

                    # 构建结构化错误信息
                    error_detail = {
                        "line": line,
                        "column": character,
                        "end_line": end_line,
                        "end_column": end_character,
                        "message": message,
                        "severity": error.get("severity", 1),
                        "source": error.get("source", ""),
                    }
                    error_details.append(error_detail)

                    # 同时保留可读格式
                    error_messages.append(f"Line {line}, Column {character}: {message}")

                print(f"❌ [CodeQLComposeTool] 发现 {len(errors)} 个语法错误")

                # 返回完整的LSP诊断信息（JSON格式 + 可读格式）
                lsp_error_output = {
                    "format": "lsp_diagnostics",
                    "errors": error_details,
                    "readable": "\n".join(error_messages),
                    "diagnostics_count": len(errors),
                    "all_diagnostics": diagnostics  # 包含所有诊断信息（错误、警告等）
                }
                import json
                return {"success": False, "output": json.dumps(lsp_error_output, indent=2, ensure_ascii=False)}

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
        # 优先匹配标准格式：```ql
        match = re.search(r"```ql\s*(.*?)```", content, re.DOTALL)
        if match:
            code = match.group(1).strip()
            if code.startswith("\n"):
                code = code[1:]
            return code

        # 匹配带空格的格式：``` ql
        match = re.search(r"```\s+ql\s*(.*?)```", content, re.DOTALL)
        if match:
            code = match.group(1).strip()
            if code.startswith("\n"):
                code = code[1:]
            return code

        match = re.search(r"<codeql>(.*?)</codeql>", content, re.DOTALL)
        if match:
            return match.group(1).strip()

        # 最后匹配通用格式：```
        match = re.search(r"```\s*(.*?)```", content, re.DOTALL)
        if match:
            code = match.group(1).strip()
            if code.startswith("\n"):
                code = code[1:]
            return code

        # 兼容未使用代码块但以 `ql` 行开头的响应
        if content:
            stripped = content.strip()
            lines = stripped.splitlines()
            if lines and lines[0].strip().lower() == "ql":
                remaining = "\n".join(lines[1:]).strip()
                if remaining:
                    return remaining

        return None

    @staticmethod
    def _format_error_info(error_output: str, round_index: int) -> str:
        return f"Round {round_index} Error:\n{error_output}"

    @staticmethod
    def _format_success_result(
        query: str,
        round_index: int,
        execution: Any,  # CodeQLExecutionResult from services.codeql_execution
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
        agent_name: str = None,
        agent_type: str = None,
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

        # 生成任务ID用于工作区管理和持久化
        from datetime import datetime
        import uuid
        task_id = f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
        print(f"[CodeQLComposeTool] 任务ID: {task_id}")

        _agent_name = agent_name or "CodeQL Compose Tool"
        _agent_type = agent_type or "codeql_compose"

        if event_callback:
            from datetime import datetime
            await event_callback({
                "type": "agent_start",
                "timestamp": datetime.now().isoformat(),
                "agent_name": _agent_name,
                "agent_type": _agent_type,
                "message": f"开始CodeQL查询生成与验证（{target_language}）",
                "data": {"language": target_language, "max_iterations": max_iterations}
            })

        project_root = Path(__file__).parent.parent
        kb_context: Dict[str, str] = self._get_kb_context(requirement, project_root, target_language)

        gen_agent = self._gen_agent_cls(self.analyzer)
        error_agent = self._error_agent_cls(self.analyzer)
        fix_inplace_agent = self._fix_inplace_agent_cls(self.analyzer)

        ql_template = self._load_ql_template(target_language)
        round_index = 1
        prev_original_ql = None
        prev_fix_suggestions = None
        is_first_round = True  # Track if this is the first generation

        query_file = create_temporary_qlpack("", language=target_language, task_id=task_id)
        pack_root = query_file.parent

        # 启动LSP服务
        print(f"📁 [CodeQLComposeTool] 临时目录: {pack_root}")
        print(f"   [CodeQLComposeTool] 初始化LSP服务")
        lsp_service = CodeQLLSPService(pack_root, query_file)
        
        from tools.lsp_codeql import HotCodeQL
        from tools.lsp_lookup_tool import LSPFunctionLookupTool
        
        hot_engine = None
        lsp_lookup_tool = None

        # 添加详细的进度指示
        import time
        start_time = time.time()
        final_result = None
        is_success = False
        print(f"   [CodeQLComposeTool] 启动LSP服务")


        # 调用start_server方法，它内部已经有30秒的重试机制
        if lsp_service.start():
            elapsed_time = time.time() - start_time
            print(f"✅ [CodeQLComposeTool] LSP服务启动成功 (耗时: {elapsed_time:.1f}秒)")
            try:
                hot_engine = HotCodeQL(
                    codeql="codeql",
                    pack_root=pack_root,
                    query_file=query_file,
                    synchronous=True,
                    init_timeout=60.0,
                    quiet_logs=True
                )
                hot_engine.start()
                
                # 创建LSP函数查询工具
                lsp_lookup_tool = LSPFunctionLookupTool(engine=hot_engine)
                
                # 确保analyzer已初始化
                if self.analyzer:
                    if not hasattr(self.analyzer, 'tools') or self.analyzer.tools is None:
                        print(f"   [CodeQLComposeTool] 等待analyzer初始化...")
                        await self.analyzer.initialize(event_callback)
                    
                    # 将工具添加到analyzer的工具列表
                    if self.analyzer.tools is not None:
                        self.analyzer.tools.append(lsp_lookup_tool)
                        # print(f"✅ [CodeQLComposeTool] LSP函数查询工具已添加到analyzer (共{len(self.analyzer.tools)}个工具)")
                    else:
                        pass
                        # print(f"⚠️  [CodeQLComposeTool] analyzer.tools为None，无法添加LSP查询工具")
                else:
                    print(f"⚠️  [CodeQLComposeTool] analyzer未配置，无法添加LSP查询工具")
                
            except Exception as e:
                print(f"⚠️  [CodeQLComposeTool] HotCodeQL引擎启动失败: {e}")
                print(f"   [CodeQLComposeTool] 将继续执行，但函数查询功能不可用")
                import traceback
                traceback.print_exc()
        else:
            print("❌ [CodeQLComposeTool] LSP服务启动失败")
            return f"Error: Failed to start LSP service for syntax checking"

        try:
            with self._syntax_session_cls(pack_root):
                while round_index <= max_iterations:
                    # Only use GenAgent for the first round
                    if is_first_round:
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
                            is_success = False
                            final_result = f"Error in CodeQL generation (Round {round_index}): {gen_result.error or 'Unknown error'}"
                            return final_result

                        current_ql = self._extract_codeql_from_response(gen_result.content)
                        if not current_ql:
                            is_success = False
                            final_result = f"Error: Could not extract CodeQL code from generation result (Round {round_index})"
                            return final_result

                        # Save generated query to file immediately
                        print(f"💾 [CodeQLComposeTool] QL文件: {query_file}")
                        query_file.write_text(current_ql, encoding='utf-8')
                        
                        # Also save to persistent directory
                        metadata = {
                            "task_id": task_id,
                            "language": target_language,
                            "requirement": requirement,
                            "round": round_index,
                            "timestamp": datetime.now().isoformat(),
                        }
                        save_query_to_persistent_dir(
                            query_content=current_ql,
                            task_id=task_id,
                            language=target_language,
                            metadata=metadata
                        )
                        
                        is_first_round = False
                    else:
                        # For subsequent rounds, read from file (modified by FixInplaceAgent)
                        try:
                            current_ql = query_file.read_text(encoding='utf-8')
                            print(f"📖 [CodeQLComposeTool] 从文件读取修改后的QL: {query_file}")
                        except Exception as e:
                            is_success = False
                            final_result = f"Error reading query file: {e}"
                            return final_result

                    # 直接使用 LSP 进行语法检查和执行
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
                            json_path=exec_result.get('json_path'),  # execute_codeql_query 不返回此字段，为 None
                            paths_count=exec_result.get('paths_count'),  # execute_codeql_query 不返回此字段，为 None
                            result_file=exec_result.get('result_file'),
                            preview=exec_result.get('preview')
                        )

                        # 正常结果处理
                        if mode_now == 'run' and run_query_and_decode_to_text is not None:
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
                            is_success = True
                            final_result = result
                            return result
                        else:
                            print("codeql query:", current_ql)
                            is_success = True
                            final_result = self._format_success_result(
                                query=current_ql,
                                round_index=round_index,
                                execution=execution_result,
                            )
                            return final_result

                    # 如果语法检查通过但执行失败，继续纠错循环
                    if round_index >= max_iterations:
                        error_info = self._format_error_info(exec_result.get('output', ''), round_index)
                        is_success = False
                        final_result = (
                            f"Failed to generate working CodeQL query after {max_iterations} rounds.\n\n"
                            f"Final error:\n{error_info}\n\n"
                            f"Last attempted query:\n```ql\n{current_ql}\n```"
                        )
                        return final_result
                    
                    # Step 1: Use ErrorAgent to analyze errors
                    print(f"🔍 [CodeQLComposeTool] 分析错误（第{round_index}轮）")
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
                        is_success = False
                        final_result = (
                            f"Error in error analysis (Round {round_index}): "
                            f"{error_analysis.error or 'Unknown error'}"
                        )
                        return final_result

                    # Step 2: Use FixInplaceAgent to modify the file
                    print(f"🔧 [CodeQLComposeTool] 开始修复QL语句")
                    ql_file_path_abs = str(query_file.resolve())
                    
                    fix_result = await fix_inplace_agent.fix(
                        ql_file_path=ql_file_path_abs,
                        curr_ql_content=current_ql,
                        prev_fix_suggestions=error_analysis.content,
                        prev_original_ql=prev_original_ql,
                        round_index=round_index,
                        show_thinking=show_thinking,
                        event_callback=event_callback,
                    )
                    
                    if not fix_result.success:
                        is_success = False
                        final_result = (
                            f"Error in in-place fixing (Round {round_index}): "
                            f"{fix_result.error or 'Unknown error'}"
                        )
                        return final_result
                    
                    print(f"✅ [CodeQLComposeTool] 文件修改完成，准备下一轮验证")

                    prev_original_ql = current_ql
                    round_index += 1

        except RuntimeError as runtime_error:
            final_result = f"Error: {runtime_error}"
            is_success = False
        except Exception as exc:
            final_result = f"Error during CodeQL composition: {exc}"
            is_success = False
        finally:
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL查询生成与验证{'成功' if is_success else '失败'}",
                    "data": {"success": is_success, "language": target_language}
                })

            # 在整个多轮查询过程结束后停止LSP服务
            print("🛑 [CodeQLComposeTool] 停止LSP服务...")
            lsp_service.stop()
            
            # 停止HotCodeQL引擎
            if hot_engine:
                try:
                    hot_engine.shutdown()
                except Exception as e:
                    print(f"⚠️  [CodeQLComposeTool] 停止HotCodeQL引擎时出错: {e}")
            
            # 从analyzer工具列表中移除LSP查询工具
            if lsp_lookup_tool and self.analyzer and hasattr(self.analyzer, 'tools') and self.analyzer.tools:
                try:
                    self.analyzer.tools.remove(lsp_lookup_tool)
                except ValueError:
                    pass

        if final_result is not None:
            return final_result
        return f"Unexpected end of iteration loop after {max_iterations} rounds"
