"""LangChain 工具：根据自然语言需求生成并验证 CodeQL 查询。"""

from __future__ import annotations

import re
import json
import time
import shutil
import subprocess
from pathlib import Path
from typing import Any, Dict, Optional, Type, Callable, List, Set

import requests
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent
from agents.codeql_gen_agents.codeql_error_agent import CodeQLErrorAgent
from config import get_sarif2json_config
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
from utils.sarif_utils import write_paths_json


class LSPCodeQLService:
    """CodeQL LSP HTTP服务管理类"""
    
    def __init__(self, pack_root: str, port: int = 8766):
        self.pack_root = pack_root
        self.port = port
        self.process = None
        self.base_url = f"http://127.0.0.1:{port}"
    
    def start_server(self, pack_root: str) -> bool:
        """启动LSP HTTP服务器"""
        try:
            # 启动LSP服务器
            lsp_script_path = Path(__file__).parent / "lsp_codeql.py"
            if not lsp_script_path.exists():
                return False
                
            self.process = subprocess.Popen([
                "python", str(lsp_script_path),
                "--pack-root", pack_root,
                "--port", str(self.port),
                "--quiet-logs"
            ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            
            # 等待服务器启动
            for _ in range(30):  # 最多等待30秒
                time.sleep(1)
                try:
                    response = requests.get(f"{self.base_url}/health", timeout=2)
                    if response.status_code == 200:
                        return True
                except:
                    continue
            
            return False
        except Exception as e:
            print(f"LSP服务器启动失败: {e}")
            return False
    
    def check_codeql_syntax(self, codeql_text: str) -> Dict[str, Any]:
        """检查CodeQL语法"""
        try:
            response = requests.post(
                f"{self.base_url}/check",
                json={"code": codeql_text},
                timeout=30
            )
            if response.status_code == 200:
                return response.json()
            else:
                return {"error": f"HTTP {response.status_code}: {response.text}"}
        except Exception as e:
            return {"error": f"请求失败: {e}"}
    
    def stop_server(self):
        """停止LSP服务器"""
        if self.process:
            try:
                # 发送关闭请求
                requests.post(f"{self.base_url}/shutdown", timeout=5)
            except:
                pass
            
            # 终止进程
            try:
                self.process.terminate()
                self.process.wait(timeout=5)
            except:
                try:
                    self.process.kill()
                    self.process.wait(timeout=2)
                except:
                    pass
            
            # 确保进程被清理
            if self.process and self.process.poll() is None:
                self.process.kill()
            
            self.process = None
            
            # 强制清理可能的端口占用
            import socket
            try:
                # 尝试绑定端口来释放可能的TIME_WAIT状态
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                s.bind(('127.0.0.1', self.port))
                s.close()
            except:
                pass


class PythonKnowledgeBase:
    """处理Python CodeQL知识库的辅助类，用于提取和推荐模块、帮助器、模板等。"""

    CORE_MODULE_IDS = [
        "module:dataflow",
        "module:tainttracking",
        "module:remote-flow-sources",
    ]

    DIRECTORY_DESCRIPTIONS: Dict[str, str] = {
        "README.md": "Overview of the Python CodeQL knowledge base and workflow tips.",
        "CODEQL_PATH_QUERY_GUIDE.md": "Path-problem skeleton and authoring guidance.",
        "templates/path_problem_skeleton.ql": "Baseline skeleton for Python path-problem queries.",
        "knowledge_base/modules.json": "Module imports with summaries, exports, and tags.",
        "knowledge_base/helpers.json": "Reusable helper predicates with signatures and examples.",
        "knowledge_base/templates.json": "Scenario templates referencing helpers/modules.",
        "knowledge_base/cases.json": "Successful CVE queries referencing helper ids.",
        "knowledge_base/errors.json": "Compiler error patterns with causes and fixes.",
        "tools/retrieve.py": "Tag-driven retrieval CLI for combining modules/helpers/templates.",
    }

    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
        self.source_dir = repo_root / "QLdatabase" / "Python"
        self.mirror_dir = repo_root / "projects" / "python_kb"
        self._sections: Optional[Dict[str, List[Dict[str, Any]]]] = None

    def ensure_mirror(self) -> bool:
        """Mirror the Python KB into the projects directory for MCP filesystem access."""
        if not self.source_dir.is_dir():
            return False

        try:
            self.mirror_dir.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(self.source_dir, self.mirror_dir, dirs_exist_ok=True)
        except Exception:
            # If mirroring fails we still try to proceed with the source directory.
            pass

        return self.mirror_dir.is_dir()

    def _load_section(self, name: str) -> List[Dict[str, Any]]:
        kb_root = self.source_dir / "knowledge_base"
        path = kb_root / f"{name}.json"
        if not path.is_file():
            return []
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return []

    def sections(self) -> Dict[str, List[Dict[str, Any]]]:
        if self._sections is None:
            self._sections = {
                "modules": self._load_section("modules"),
                "helpers": self._load_section("helpers"),
                "templates": self._load_section("templates"),
                "cases": self._load_section("cases"),
                "errors": self._load_section("errors"),
            }
        return self._sections

    def build_directory_index(self) -> str:
        """返回相对于MCP根目录（projects/）的简洁目录索引。"""
        if not (self.mirror_dir.is_dir() or self.ensure_mirror()):
            return ""

        entries = []
        for relative_path, description in self.DIRECTORY_DESCRIPTIONS.items():
            entries.append(f"- `projects/python_kb/{relative_path}` - {description}")
        return "\n".join(entries)

    def _collect_known_tags(self) -> Set[str]:
        tags: Set[str] = set()
        for items in self.sections().values():
            for item in items:
                for tag in item.get("tags", []):
                    tags.add(str(tag).lower())
        return tags

    @staticmethod
    def _tokenize_requirement(requirement: str) -> Set[str]:
        return {token.lower() for token in re.findall(r"[a-zA-Z0-9_]+", requirement or "") if token}

    def derive_tags(self, requirement: str) -> Set[str]:
        tokens = self._tokenize_requirement(requirement)
        if not tokens:
            return set()
        known_tags = self._collect_known_tags()
        matched = tokens & known_tags
        return matched

    def _select_items(self, section: str, tags: Set[str], limit: int = 5) -> List[Dict[str, Any]]:
        items = self.sections().get(section, [])
        if not items:
            return []

        selected: List[Dict[str, Any]] = []
        tag_lower = {tag.lower() for tag in tags}

        if section == "modules":
            for module_id in self.CORE_MODULE_IDS:
                for item in items:
                    if item.get("id") == module_id and item not in selected:
                        selected.append(item)

        for item in items:
            item_tags = {str(tag).lower() for tag in item.get("tags", [])}
            if tag_lower & item_tags and item not in selected:
                selected.append(item)

        if not selected:
            selected = items[:limit]

        return selected[:limit]

    def build_suggestions(self, tags: Set[str]) -> str:
        """Produce a compact recommendation list grouped by knowledge base section."""
        sections_order = ["modules", "helpers", "templates", "cases", "errors"]
        lines: List[str] = []
        if tags:
            lines.append(f"Matched tags: {', '.join(sorted(tags))}")
        else:
            lines.append("Matched tags: (none)")

        for section in sections_order:
            items = self._select_items(section, tags)
            if not items:
                continue
            lines.append(f"[{section}]")
            for item in items:
                item_id = item.get("id", "unknown")
                if section == "modules":
                    summary = item.get("summary") or item.get("usage_notes") or ""
                elif section == "helpers":
                    summary = item.get("description") or ""
                elif section == "templates":
                    summary = item.get("description") or ""
                    file_hint = item.get("file")
                    if file_hint:
                        summary = f"{summary} (file: {file_hint})"
                elif section == "cases":
                    summary = item.get("summary") or ""
                    path_hint = item.get("path")
                    if path_hint:
                        summary = f"{summary} (query: {path_hint})"
                else:
                    summary = item.get("cause") or ""
                tag_list = ", ".join(item.get("tags", []))
                tag_suffix = f" [tags: {tag_list}]" if tag_list else ""
                summary = summary.strip()
                lines.append(f"- {item_id}: {summary}{tag_suffix}")
        return "\n".join(lines)

    def build_context(self, requirement: str) -> Dict[str, str]:
        if not self.ensure_mirror():
            return {}
        matched_tags = self.derive_tags(requirement)
        directory_index = self.build_directory_index()
        suggestions = self.build_suggestions(matched_tags)
        return {
            "kb_directory_index": directory_index,
            "kb_suggestions": suggestions,
            "relevant_tags": ", ".join(sorted(matched_tags)),
        }


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

        # 启动LSP服务
        print(f"📁 [CodeQLComposeTool] 临时目录: {pack_root}")
        print(f"   [CodeQLComposeTool] 启动LSP服务进行语法检查...")
        print(f"   [CodeQLComposeTool] 正在初始化LSP服务，请稍候...")
        lsp_service = LSPCodeQLService(pack_root)
        
        # 添加详细的进度指示
        import time
        start_time = time.time()
        print(f"   [CodeQLComposeTool] 开始启动LSP服务进程...")
        
        # 调用start_server方法，它内部已经有30秒的重试机制
        if lsp_service.start_server(str(pack_root)):
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
                        relevant_tags=kb_context.get("relevant_tags"),
                    )
                    gen_prompt = apply_placeholders(gen_prompt_base, gen_values)
                    gen_result = await self.analyzer.run_agent(gen_prompt, show_thinking=show_thinking)

                    if not gen_result.success:
                        return f"Error in CodeQL generation (Round {round_index}): {gen_result.error or 'Unknown error'}"

                    current_ql = self._extract_codeql_from_response(gen_result.content)
                    if not current_ql:
                        return f"Error: Could not extract CodeQL code from generation result (Round {round_index})"
                    
                    # 使用LSP服务进行语法检查而不是实际执行
                    # 复用原本执行检查方案的逻辑创建临时目录结构
                    print(f"🔍 [CodeQLComposeTool] 第{round_index}轮 - 创建临时QL包进行语法检查...")
                    
                    try:
                        # 使用LSP服务检查语法
                        print(f"🔍 [CodeQLComposeTool] 进行CodeQL语法检查...")
                        syntax_result = lsp_service.check_codeql_syntax(current_ql)
                        print(syntax_result)
                        
                        # 模拟执行结果结构
                        if "error" in syntax_result:
                            print(f"❌ [CodeQLComposeTool] LSP语法检查失败: {syntax_result['error']}")
                            exec_result = {"success": False, "output": syntax_result["error"]}
                        else:
                            # 检查诊断结果并详细输出所有信息
                            diagnostics = syntax_result.get("diagnostics", [])
                            
                            # 按严重程度分类诊断信息
                            errors = [d for d in diagnostics if d.get("severity", 1) == 1]  # 错误
                            warnings = [d for d in diagnostics if d.get("severity", 2) == 2]  # 警告
                            infos = [d for d in diagnostics if d.get("severity", 3) == 3]  # 信息
                            hints = [d for d in diagnostics if d.get("severity", 4) == 4]  # 提示
                            
                            # 输出诊断摘要
                            print(f"📊 [CodeQLComposeTool] 语法检查诊断摘要:")
                            print(f"   - 错误: {len(errors)} 个")
                            print(f"   - 警告: {len(warnings)} 个")
                            print(f"   - 信息: {len(infos)} 个")
                            print(f"   - 提示: {len(hints)} 个")
                            
                            # 详细输出所有诊断信息
                            if diagnostics:
                                print(f"\n📋 [CodeQLComposeTool] 详细诊断信息:")
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
                                print(f"✅ [CodeQLComposeTool] 未发现任何诊断问题")
                            
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
                                exec_result = {
                                    "success": False, 
                                    "output": "\n".join(error_messages)
                                }
                            else:
                                # 语法检查通过，实际执行CodeQL查询
                                print(f"✅ [CodeQLComposeTool] 语法检查通过")
                                print(f"🚀 [CodeQLComposeTool] 开始实际执行CodeQL查询...")
                                
                                # 实际执行CodeQL查询
                                try:
                                    exec_result = execute_codeql_query(
                                        current_ql,
                                        self.default_database_path,
                                        target_language,
                                        query_file
                                    )
                                    print(f"✅ [CodeQLComposeTool] CodeQL查询执行完成")
                                except Exception as e:
                                    print(f"❌ [CodeQLComposeTool] CodeQL查询执行失败: {e}")
                                    exec_result = {"success": False, "output": f"Execution failed: {str(e)}"}
                    except Exception as e:
                        print(f"❌ [CodeQLComposeTool] 语法检查过程中发生异常: {e}")
                        exec_result = {"success": False, "output": f"Syntax check failed: {str(e)}"}
                    
                    # Check execution result
                    if exec_result.get('success', False):
                        mode_now = (exec_mode or 'analyze').lower()
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
                            sarif_path = exec_result.get('sarif_path')
                            json_path: Optional[str] = None
                            paths_count: Optional[int] = None
                            
                            # 如果已经成功执行了CodeQL查询，将字典转换为CodeQLExecutionResult对象
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
        finally:
            # 在整个多轮查询过程结束后停止LSP服务
            print("🛑 [CodeQLComposeTool] 停止LSP服务...")
            lsp_service.stop_server()

        return f"Unexpected end of iteration loop after {max_iterations} rounds"
