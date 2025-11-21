"""LangChain 工具：根据自然语言需求生成并验证 CodeQL 查询。"""

from __future__ import annotations

import re
import json
import logging
import asyncio
from pathlib import Path
from typing import Any, Dict, Optional, Type, Callable

# 配置日志记录器（仅用于调试）
logger = logging.getLogger(__name__)

# Service
from services.lsp_service import CodeQLLSPService
from services.knowledge_base.base import LanguageKnowledgeBase


from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent
from agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent
from agents.codeql_gen_agents.codeql_fix_inplace_agent import CodeQLFixInplaceAgent
from agents.codeql_gen_agents.codeql_breakpoint_detect_agent import CodeQLBreakpointAgent
from agents.codeql_gen_agents.template_refinement_agent import TemplateRefinementAgent
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
    resolve_codeql_database_root,
    is_database_error,
    save_query_to_persistent_dir,
    is_empty_result,
    count_dataflow_paths,
)
from prompts.codeql_prompts import (
    get_codeql_generation_prompt_suffix,
    get_retry_strategy_description,
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
    default_max_rounds: int = 10
    enable_error_tidy: bool = False

    def __init__(
        self,
        analyzer: Optional[Any] = None,
        database_path: str = "",
        language: str = "java",
        max_rounds: int = 5,
        enable_error_tidy: bool = False,
        *,
        gen_agent_cls: Type[CodeQLGenAgent] = CodeQLGenAgent,
        error_agent_cls: Type[CodeQLErrorAgent] = CodeQLErrorAgent,
        fix_inplace_agent_cls: Type[CodeQLFixInplaceAgent] = CodeQLFixInplaceAgent,
        syntax_session_cls: Type[CodeQLSyntaxSession] = CodeQLSyntaxSession,
        breakpoint_detect_agent_cls: Type[CodeQLBreakpointAgent] = CodeQLBreakpointAgent,
        **kwargs,
    ) -> None:
        super().__init__(**kwargs)
        self.analyzer = analyzer
        self.default_database_path = database_path
        self.default_language = language
        self.default_max_rounds = max_rounds
        self.enable_error_tidy = enable_error_tidy

        self._gen_agent_cls = gen_agent_cls
        self._error_agent_cls = error_agent_cls
        self._fix_inplace_agent_cls = fix_inplace_agent_cls
        self._syntax_session_cls = syntax_session_cls
        self._breakpoint_detect_agent_cls = breakpoint_detect_agent_cls

        self._kb_cache: Dict[str, LanguageKnowledgeBase] = {}
    
    def _extract_project_name_from_db_path(self, db_path: str) -> str:
        """从数据库路径中提取项目名称。
        
        例如：从 'projects/CVE-2021-21985/db' 提取 'CVE-2021-21985'
        """
        path = Path(db_path)
        # 如果路径是 projects/xxx/db 的格式，提取 xxx 作为项目名
        if path.parent.name == "projects":
            return path.name
        # 如果路径是 projects/xxx/db 的格式，提取 xxx 作为项目名
        elif path.parent.parent.name == "projects" and path.name == "db":
            return path.parent.name
        # 如果无法从路径提取，尝试其他方法
        else:
            # 尝试从路径中找到包含 "projects" 的部分
            parts = path.parts
            for i, part in enumerate(parts):
                if part == "projects" and i + 1 < len(parts):
                    return parts[i + 1]
            # 如果都找不到，返回空字符串
            return ""

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

    def _build_error_tidy_markdown(
        self,
        project_name: str,
        language: str,
        task_id: str,
        created_at: str,
        error_rounds: list[Dict[str, Any]],
        final_ql: str,
    ) -> str:
        """构建错误整理 Markdown 文档内容。

        文档包含：
        - 头部项目信息（项目、语言、时间戳、任务ID）
        - 按轮次组织的错误信息（错误QL、错误日志/LSP诊断、错误分析）
        - 最终正确的QL代码
        """

        lines: list[str] = []

        lines.append("# CodeQL 错误整理文档")
        lines.append("")

        # 文档头部信息
        lines.append("## 基本信息")
        lines.append(f"- 项目: {project_name or '(unknown)'}")
        lines.append(f"- 语言: {language}")
        lines.append(f"- 任务ID: {task_id}")
        lines.append(f"- 生成时间: {created_at}")
        lines.append("")

        # 按轮次组织错误信息
        lines.append("## 错误修复轮次")
        for item in error_rounds:
            round_no = item.get("round")
            error_ql = item.get("error_ql", "")
            error_log = item.get("error_log", "")
            error_analysis = item.get("error_analysis", "")

            lines.append(f"### 第 {round_no} 轮")

            if error_ql:
                lines.append("#### 错误QL")
                lines.append("```ql")
                lines.append(error_ql)
                lines.append("```")

            if error_log:
                lines.append("#### 错误日志 / LSP 诊断")
                lines.append("```")
                lines.append(error_log)
                lines.append("```")

            if error_analysis:
                lines.append("#### 错误分析")
                lines.append(error_analysis)

            lines.append("")

        # 最终正确的QL代码
        lines.append("## 最终正确的QL代码")
        lines.append("```ql")
        lines.append(final_ql)
        lines.append("```")

        return "\n".join(lines)

    def _load_ql_template(self, lang: str) -> str:
        try:
            target = (lang or "").lower()
            prompts_dir = Path(__file__).parent.parent / "prompts"
            java_path = prompts_dir / "java_temple_ql.md"
            py_path = prompts_dir / "python_template_ql.md"
            c_path = prompts_dir / "c_template_ql.md"

            if target == "python" and py_path.exists():
                # Python 特殊处理：拼接主模板 + 模式库 + 案例库
                # 这样在维护时是分离的，但在注入 Prompt 时是完整的。
                base_content = py_path.read_text(encoding="utf-8")
                
                patterns_path = prompts_dir / "python_patterns.md"
                cases_path = prompts_dir / "python_cases.md"
                
                extra_content = []
                if patterns_path.exists():
                    extra_content.append("\n\n" + patterns_path.read_text(encoding="utf-8"))
                if cases_path.exists():
                    extra_content.append("\n\n" + cases_path.read_text(encoding="utf-8"))
                
                return base_content + "".join(extra_content)

            if target in {"c", "cpp"} and c_path.exists():
                # C/CPP 特殊处理：拼接主模板 + 模式库 + 案例库
                base_content = c_path.read_text(encoding="utf-8")
                
                patterns_path = prompts_dir / "c_patterns.md"
                cases_path = prompts_dir / "c_cases.md"
                
                extra_content = []
                if patterns_path.exists():
                    extra_content.append("\n\n" + patterns_path.read_text(encoding="utf-8"))
                if cases_path.exists():
                    extra_content.append("\n\n" + cases_path.read_text(encoding="utf-8"))
                
                return base_content + "".join(extra_content)
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

        # 解析并修正数据库路径
        self.default_database_path = resolve_codeql_database_root(self.default_database_path, self.default_language)

        # 在执行前验证数据库
        print(f"🔍 [CodeQLComposeTool] 验证数据库: {self.default_database_path}")
        is_valid, validation_error = validate_codeql_database(self.default_database_path, self.default_language)
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

        # 从数据库路径提取项目名称
        project_name = self._extract_project_name_from_db_path(self.default_database_path)
        
        gen_agent = self._gen_agent_cls(self.analyzer)
        error_agent = self._error_agent_cls(self.analyzer)
        fix_inplace_agent = self._fix_inplace_agent_cls(self.analyzer)
        breakpoint_detect_agent = self._breakpoint_detect_agent_cls(self.analyzer, source_root="projects", project_name=project_name)

        ql_template = self._load_ql_template(target_language)
        round_index = 1
        prev_original_ql = None
        prev_fix_suggestions = None
        is_first_round = True  # Track if this is the first generation
        
        # 重试机制相关变量
        retry_count = 0  # 当前重试次数
        max_retries = 5  # 最大重试次数
        retry_history = []  # 记录重试历史（仅用于日志）

        # 错误整理文档所需的每轮错误信息
        error_rounds: list[Dict[str, Any]] = []

        query_file = create_temporary_qlpack("", language=target_language, task_id=task_id)
        pack_root = query_file.parent

        # 启动LSP服务（仅用于语法检查）
        print(f"📁 [CodeQLComposeTool] 临时目录: {pack_root}")
        print(f"   [CodeQLComposeTool] 初始化LSP服务")
        lsp_service = CodeQLLSPService(pack_root, query_file)

        # 添加详细的进度指示
        import time
        start_time = time.time()
        final_result = None
        is_success = False
        print(f"   [CodeQLComposeTool] 启动LSP服务")

        # 调用start_server方法，它内部已经有30秒的重试机制
        if not lsp_service.start():
            print("❌ [CodeQLComposeTool] LSP服务启动失败")
            return f"Error: Failed to start LSP service for syntax checking"
        
        elapsed_time = time.time() - start_time
        print(f"✅ [CodeQLComposeTool] LSP服务启动成功 (耗时: {elapsed_time:.1f}秒)")
    

        try:
            while round_index <= max_iterations:
                # Only use GenAgent for the first round
                if is_first_round:
                    # 根据重试次数选择prompt策略
                    strategy_suffix = get_codeql_generation_prompt_suffix(retry_count)
                    strategy_desc = get_retry_strategy_description(retry_count)
                    
                    # 记录重试策略到日志（静默）
                    if retry_count > 0:
                        logger.info(f"[重试 {retry_count}/{max_retries}] 使用策略: {strategy_desc}")
                    
                    gen_prompt_base = gen_agent.build_prompt(
                        language=target_language,
                        requirement=requirement,
                        round_index=round_index,
                        prev_original_ql=prev_original_ql,
                        prev_fix_suggestions=prev_fix_suggestions,
                    )
                    
                    # 将策略后缀添加到prompt中
                    gen_prompt_base = gen_prompt_base + "\n\n" + strategy_suffix
                    
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
                    # 检查结果是否为空
                    sarif_path = exec_result.get('sarif_path')
                    json_path = exec_result.get('json_path')
                    
                    is_result_empty = is_empty_result(sarif_path)
                    paths_count = count_dataflow_paths(sarif_path, json_path)

                    #断流点查找
                    if is_result_empty:
                        print(f"⚠️ [CodeQLComposeTool] 第{round_index}轮查询结果为空，进行断流点查找，正在执行断流查找codeql语句")
                        from .extract_ql import extract_ql_predicate, Get_Breakpoint
                        
                        # 初始化断流点添加计数器
                        breakpoint_add_count = 0
                        max_breakpoint_attempts = 3
                        original_ql = current_ql  # 保存原始查询
                        
                        while breakpoint_add_count < max_breakpoint_attempts and is_result_empty:
                            breakpoint_add_count += 1
                            print(f"🔍 [CodeQLComposeTool] 断流点条件添加尝试 {breakpoint_add_count}/{max_breakpoint_attempts}")
                            
                            #组装断流点查询语句
                            ql_predicates = extract_ql_predicate(current_ql)
                            breakpoint_current_ql = Get_Breakpoint(ql_predicates)
                            print("断流点查询语句为: "+breakpoint_current_ql)

                            #执行断流点查询
                            exec_result = execute_codeql_query(
                                    breakpoint_current_ql,
                                    self.default_database_path,
                                    target_language,
                                    query_file,
                                    alert="alert"  # 添加alert参数，执行简单查询而不进行路径分析
                                )
                            
                            #借助agent开始分析断流点并且生成断流条件
                            print(f"🔍 [CodeQLComposeTool] 开始分析断流点（第{breakpoint_add_count}次尝试）")
                            
                            # 提取CodeQL查询结果
                            codeql_output = exec_result.get('output', '')
                            
                            # 如果有解析后的结果，使用解析后的结果；否则使用原始输出
                            if exec_result.get('results') and len(exec_result.get('results')) > 0:
                                # 如果results是解析后的JSON，将其转换为字符串
                                if isinstance(exec_result.get('results'), (list, dict)):
                                    codeql_results = json.dumps(exec_result.get('results'), indent=2)
                                else:
                                    codeql_results = str(exec_result.get('results'))
                            else:
                                # 如果没有解析后的结果，使用原始输出
                                codeql_results = codeql_output
                            
                            # 使用断点检测代理分析结果 - 第一步：分析断流点基本信息
                            print(f"🔍 [CodeQLComposeTool] 第一步：分析断流点基本信息")
                            breakpoint_analysis_result = await breakpoint_detect_agent.analyze_breakpoints(
                                codeql_results=codeql_results,
                                language=target_language,
                                show_thinking=show_thinking,
                                event_callback=event_callback,
                                agent_name=f"CodeQLComposeTool_BreakpointAnalysis_{breakpoint_add_count}",
                                agent_type="codeql_compose_breakpoint_analysis"
                            )
                            
                            if not breakpoint_analysis_result.success:
                                print(f"❌ [CodeQLComposeTool] 断流点分析失败: {breakpoint_analysis_result.error}")
                                # 继续执行，不中断整个流程
                                break
                            else:
                                print(f"✅ [CodeQLComposeTool] 断流点分析完成")
                                print(f"📄 [CodeQLComposeTool] 断流点基本信息:\n{breakpoint_analysis_result.content}")
                                
                                # 第二步：生成isAdditionalFlowStep条件
                                print(f"🔧 [CodeQLComposeTool] 第二步：生成isAdditionalFlowStep条件")
                                flowstep_result = await breakpoint_detect_agent.generate_flowstep(
                                    breakpoint_analysis=breakpoint_analysis_result.content,
                                    language=target_language,
                                    show_thinking=show_thinking,
                                    event_callback=event_callback,
                                    agent_name=f"CodeQLComposeTool_FlowstepGeneration_{breakpoint_add_count}",
                                    agent_type="codeql_compose_flowstep_generation"
                                )
                                
                                if not flowstep_result.success:
                                    print(f"❌ [CodeQLComposeTool] isAdditionalFlowStep条件生成失败: {flowstep_result.error}")
                                    break
                                else:
                                    print(f"✅ [CodeQLComposeTool] isAdditionalFlowStep条件生成完成")
                                    print(f"📄 [CodeQLComposeTool] 生成的条件:\n{flowstep_result.content}")
                                    
                                    # 提取生成的isAdditionalFlowStep条件
                                    import re
                                    flowstep_pattern = r"```ql\s*\n(?:predicate\s+isAdditionalFlowStep\s*\([^)]*\)\s*\{)?(.*?)(?:\}\s*)?```"
                                    flowstep_match = re.search(flowstep_pattern, flowstep_result.content, re.DOTALL)
                                    
                                    if flowstep_match:
                                        new_flowstep_condition = flowstep_match.group(1).strip()
                                        print(f"🔧 [CodeQLComposeTool] 提取的条件: {new_flowstep_condition}")
                                        
                                        # 更新当前查询的isAdditionalFlowStep条件
                                        isadd_pattern = r"predicate\s+isAdditionalFlowStep\s*\([^)]*\)\s*\{.*?\}"
                                        if re.search(isadd_pattern, current_ql, re.DOTALL):
                                            # 如果已存在isAdditionalFlowStep，替换其内容
                                            if breakpoint_add_count == 1:
                                                # 第一次添加，使用新生成的条件
                                                current_ql = re.sub(isadd_pattern, f"predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {{{new_flowstep_condition}}}", current_ql, flags=re.DOTALL)
                                            else:
                                                # 后续添加，将新条件与现有条件合并
                                                existing_pattern = r"predicate\s+isAdditionalFlowStep\s*\([^)]*\)\s*\{(.*?)\}"
                                                existing_match = re.search(existing_pattern, current_ql, re.DOTALL)
                                                if existing_match:
                                                    existing_condition = existing_match.group(1).strip()
                                                    # 合并条件，使用or连接
                                                    merged_condition = f"({existing_condition}) or ({new_flowstep_condition})"
                                                    current_ql = re.sub(isadd_pattern, f"predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {{{merged_condition}}}", current_ql, flags=re.DOTALL)
                                        else:
                                            # 如果不存在isAdditionalFlowStep，添加它
                                            current_ql = current_ql.replace(
                                                "predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {none()}",
                                                f"predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {{{new_flowstep_condition}}}"
                                            )
                                        
                                        # 保存更新后的查询到文件
                                        query_file.write_text(current_ql, encoding='utf-8')
                                        print(f"💾 [CodeQLComposeTool] 已更新查询文件，添加断流点条件（第{breakpoint_add_count}次）")
                                        
                                        # 重新执行查询
                                        print(f"🔄 [CodeQLComposeTool] 重新执行查询，添加断流点条件后（第{breakpoint_add_count}次）")
                                        exec_result = self._lsp_and_execute(
                                            current_ql=current_ql,
                                            target_language=target_language,
                                            query_file=query_file,
                                            lsp_service=lsp_service,
                                        )
                                        
                                        # 检查执行结果
                                        if exec_result.get('success', False):
                                            # 更新结果状态
                                            is_result_empty = is_empty_result(exec_result.get('sarif_path'))
                                            paths_count = count_dataflow_paths(exec_result.get('sarif_path'), exec_result.get('json_path'))
                                            
                                            if not is_result_empty:
                                                print(f"✅ [CodeQLComposeTool] 添加断流点条件后查询成功，找到 {paths_count} 条路径")
                                                break
                                            else:
                                                print(f"⚠️ [CodeQLComposeTool] 添加断流点条件后查询结果仍为空，继续尝试")
                                        else:
                                            print(f"❌ [CodeQLComposeTool] 添加断流点条件后查询执行失败")
                                            break
                                    else:
                                        print(f"❌ [CodeQLComposeTool] 无法从生成结果中提取isAdditionalFlowStep条件")
                                        break
                                    
                                    # 保存完整的断流点分析结果到文件
                                    try:
                                        breakpoint_result_file = pack_root / f"breakpoint_analysis_{breakpoint_add_count}.md"
                                        with open(breakpoint_result_file, 'w', encoding='utf-8') as f:
                                            f.write(f"# CodeQL断流点分析结果（第{breakpoint_add_count}次）\n\n")
                                            f.write(f"## 原始查询\n```ql\n{original_ql}\n```\n\n")
                                            f.write(f"## 当前查询\n```ql\n{current_ql}\n```\n\n")
                                            f.write(f"## 断流点查询\n```ql\n{breakpoint_current_ql}\n```\n\n")
                                            f.write(f"## 断流点基本信息\n{breakpoint_analysis_result.content}\n\n")
                                            f.write(f"## isAdditionalFlowStep条件\n{flowstep_result.content}\n")
                                        print(f"💾 [CodeQLComposeTool] 断流点分析结果已保存到: {breakpoint_result_file}")
                                    except Exception as save_error:
                                        print(f"⚠️ [CodeQLComposeTool] 保存断流点分析结果时出错: {save_error}")
                        
                        # 如果达到最大尝试次数仍为空，进入CodeQL放宽流程
                        if is_result_empty and breakpoint_add_count >= max_breakpoint_attempts:
                            print(f"⚠️ [CodeQLComposeTool] 已尝试{max_breakpoint_attempts}次添加断流点条件，结果仍为空，将进入CodeQL放宽流程")
                            # 这里可以添加进入CodeQL放宽流程的逻辑
                            # 目前先继续执行原有流程
                    
                    # 记录路径数量到日志
                    logger.info(f"[Round {round_index}] 查询执行成功，找到 {paths_count} 条路径")
                    
                    # 如果结果为空且未达到最大重试次数，触发重试
                    if is_result_empty and retry_count < max_retries:
                        retry_count += 1
                        retry_history.append({
                            "retry": retry_count,
                            "round": round_index,
                            "reason": "空结果",
                            "paths_count": paths_count
                        })
                        
                        # 静默记录重试信息到日志
                        logger.info(f"[重试触发] 检测到空结果，开始第 {retry_count}/{max_retries} 次重试")
                        
                        # 重置状态，准备重新生成查询
                        round_index = 1
                        is_first_round = True
                        prev_original_ql = None
                        prev_fix_suggestions = None
                        
                        # 继续下一次迭代（重试）
                        continue
                    
                    # 如果结果为空但已达到最大重试次数
                    if is_result_empty and retry_count >= max_retries:
                        logger.warning(f"[重试结束] 已尝试 {retry_count} 次重试，仍未找到有效路径")
                        retry_summary = f"\n\n⚠️ 已尝试{retry_count}次重试，仍未找到有效路径"
                    else:
                        retry_summary = ""
                        if retry_count > 0:
                            logger.info(f"[重试成功] 第 {retry_count} 次重试找到 {paths_count} 条路径")

                    # 如果经历过至少一轮错误修复（round_index > 1 且有错误记录），且开启了错误整理功能，生成错误整理文档
                    if round_index > 1 and error_rounds and self.enable_error_tidy:
                        try:
                            error_tidy_dir = project_root / "temp" / "error_tidy_temp"
                            error_tidy_dir.mkdir(parents=True, exist_ok=True)

                            created_at = datetime.now().isoformat()
                            tidy_markdown = self._build_error_tidy_markdown(
                                project_name=project_name,
                                language=target_language,
                                task_id=task_id,
                                created_at=created_at,
                                error_rounds=error_rounds,
                                final_ql=current_ql,
                            )

                            tidy_file = error_tidy_dir / f"error_tidy_{task_id}.md"
                            tidy_file.write_text(tidy_markdown, encoding="utf-8")
                            print(f"💾 [CodeQLComposeTool] 错误整理文档已保存到: {tidy_file}")

                            # 在不阻塞主流程的前提下异步触发 Template Refinement Agent
                            if self.analyzer is not None:

                                async def _run_template_refinement() -> None:
                                    try:
                                        agent = TemplateRefinementAgent(self.analyzer, language=target_language)
                                        await agent.refine_template(
                                            error_tidy_doc=tidy_markdown,
                                            language=target_language,
                                            show_thinking=False,
                                            event_callback=event_callback,
                                            agent_name="Template Refinement Agent",
                                            agent_type="template_refinement",
                                        )
                                    except Exception as e:
                                        # 仅记录日志，不影响主流程
                                        logger.warning(
                                            f"⚠️ [CodeQLComposeTool] Template Refinement Agent 执行失败: {e}"
                                        )

                                # 使用 asyncio.create_task 确保异步执行且不阻塞当前工具流程
                                try:
                                    asyncio.create_task(_run_template_refinement())
                                    print("🚀 [CodeQLComposeTool] 已异步触发 Template Refinement Agent")
                                except RuntimeError as e:
                                    # 在没有可用事件循环的边缘场景下，静默记录错误
                                    logger.warning(
                                        f"⚠️ [CodeQLComposeTool] 无法创建 Template Refinement 异步任务: {e}"
                                    )
                        except Exception as tidy_error:
                            print(f"⚠️ [CodeQLComposeTool] 生成错误整理文档失败: {tidy_error}")
                    
                    mode_now = (exec_mode or 'analyze').lower()

                    # 创建CodeQLExecutionResult对象
                    from services.codeql_execution import CodeQLExecutionResult
                    execution_result = CodeQLExecutionResult(
                        success=exec_result.get('success', False),
                        output=exec_result.get('output', ''),
                        sarif_path=sarif_path,
                        json_path=json_path,
                        paths_count=paths_count,
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
                        result += retry_summary
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
                        ) + retry_summary
                        return final_result

                # 如果语法检查通过但执行失败，继续纠错循环
                if round_index >= max_iterations:
                    # 如果还有重试机会，则触发重试机制
                    if retry_count < max_retries:
                        retry_count += 1
                        logger.info(f"[重试触发] 达到最大修复轮次 ({max_iterations}) 仍未修复，开始第 {retry_count}/{max_retries} 次重试")
                        print(f"🔄 [CodeQLComposeTool] 达到最大修复轮次，开始第 {retry_count}/{max_retries} 次重新生成")
                        
                        retry_history.append({
                            "retry": retry_count,
                            "round": round_index,
                            "reason": "修复失败",
                            "paths_count": 0
                        })
                        
                        # 重置状态，准备重新生成查询
                        round_index = 1
                        is_first_round = True
                        prev_original_ql = None
                        prev_fix_suggestions = None
                        
                        continue

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

                # 记录当前轮次的错误信息，用于后续生成错误整理文档
                error_rounds.append({
                    "round": round_index,
                    "error_ql": current_ql,
                    "error_log": exec_result.get('output', ''),
                    "error_analysis": error_analysis.content,
                })

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

        if final_result is not None:
            return final_result
        return f"Unexpected end of iteration loop after {max_iterations} rounds"
