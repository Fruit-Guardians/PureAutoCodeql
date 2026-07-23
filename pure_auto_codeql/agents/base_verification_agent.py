"""验证类 Agent 的共享基类。

Sink 验证与 Source 验证 Agent 的逻辑几乎完全一致，仅在“Sink/Source”标签、
模板 key、task_id 前缀以及个别提示文案上有差异。该基类把公共流程集中到一处，
子类只需声明各自的 kind / label 等类属性。集中实现也从结构上避免了两个 Agent
返回值元数（arity）不一致这类 bug 再次出现。
"""

import hashlib
import re
from datetime import datetime
from typing import TYPE_CHECKING, Callable, Optional, Tuple

from pure_auto_codeql.prompts.verification_prompt_manager import (
    load_verification_template,
)
from pure_auto_codeql.services import (
    CodeQLLSPService,
    apply_placeholders,
    build_placeholder_map,
)
from pure_auto_codeql.utils.codeql import (
    create_temporary_qlpack,
    execute_codeql_query,
    is_empty_result,
)
from pure_auto_codeql.utils.logger import get_logger

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer

logger = get_logger(__name__)


class BaseVerificationAgent:
    """生成并执行验证查询，确认 LLM 识别的点是否真实存在于代码库中。"""

    #: 模板 key 与 task_id 前缀使用的小写标识，如 "sink" / "source"
    kind: str = "point"
    #: 展示用标签，如 "Sink" / "Source"
    label: str = "Point"
    #: failed_queries 提示中“检查函数名、X是否正确”里的 X
    name_check_hint: str = "类名"

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        database_path: str,
        language: str,
        lsp_service: Optional[CodeQLLSPService] = None,
        workspace_path: Optional[str] = None,
    ):
        """初始化验证 Agent。

        Args:
            analyzer: MultiAgentAnalyzer 实例，用于调用 LLM
            database_path: CodeQL 数据库路径
            language: 目标语言（java/python/cpp/c）
            lsp_service: CodeQL LSP 服务实例（可选）
            workspace_path: 工作空间路径（用于 MCP 配置）
        """
        self.analyzer = analyzer
        self.database_path = database_path
        self.language = language.lower()
        self.lsp_service = lsp_service
        self.workspace_path = workspace_path

    @property
    def _default_agent_name(self) -> str:
        return f"{self.label} Verification Agent"

    @property
    def _default_agent_type(self) -> str:
        return f"{self.kind}_verification"

    async def verify_analysis_result(
        self,
        analysis_result: str,
        max_retries: int = 3,
        timeout: int = 30,
        show_thinking: bool = False,
        event_callback: Optional[Callable] = None,
        agent_name: Optional[str] = None,
        agent_type: Optional[str] = None,
    ) -> Tuple[bool, str, Optional[str]]:
        """验证整个分析结果是否有效（生成查询并检查是否返回结果）。

        Returns:
            (is_valid, error_message, verification_query)
        """
        _agent_name = agent_name or self._default_agent_name
        _agent_type = agent_type or self._default_agent_type

        logger.info("开始验证 %s 分析结果（整体验证）", self.label)

        if event_callback:
            await event_callback({
                "type": "agent_start",
                "timestamp": datetime.now().isoformat(),
                "agent_name": _agent_name,
                "agent_type": _agent_type,
                "message": f"开始验证 {self.label} 分析结果",
                "data": {"analysis_result_length": len(analysis_result)}
            })

        # 重新初始化 analyzer 以使用正确的 agent_type 和 MCP 配置
        if self.workspace_path:
            await self.analyzer.initialize(
                event_callback=event_callback,
                language=self.language,
                workspace_path=self.workspace_path,
                agent_type=_agent_type,
            )

        # 1. 构建验证需求
        requirement = self._build_requirement(analysis_result)
        logger.debug(f"验证需求: {requirement}")

        # 2. 加载验证模板
        try:
            template = self._load_verification_template()
        except Exception as e:
            error_msg = f"加载验证模板失败: {e}"
            logger.error(error_msg)
            return (False, error_msg, None)

        # 3. 重试循环
        failed_queries = []
        for attempt in range(max_retries):
            try:
                logger.info(f"{self.label} 验证尝试 {attempt + 1}/{max_retries}")

                prompt = self._build_prompt(
                    requirement,
                    template,
                    analysis_result,
                    failed_queries=failed_queries if attempt > 0 else None
                )

                result = await self.analyzer.run_agent(
                    prompt,
                    show_thinking=show_thinking,
                    event_callback=event_callback,
                    agent_name=_agent_name,
                    agent_type=_agent_type,
                )

                if not result.success:
                    logger.warning(f"LLM 生成失败 (尝试 {attempt + 1}/{max_retries}): {result.content}")
                    continue

                verification_ql = self._extract_codeql_from_response(result.content)
                if not verification_ql:
                    logger.warning(f"未能从响应中提取 CodeQL 查询 (尝试 {attempt + 1}/{max_retries})")
                    continue

                logger.debug(f"生成的验证查询:\n{verification_ql}")

                if self.lsp_service:
                    syntax_result = self.lsp_service.check_syntax(verification_ql)
                    if syntax_result.get("diagnostics"):
                        errors = [d for d in syntax_result["diagnostics"] if d.get("severity", 1) == 1]
                        if errors:
                            logger.warning(f"验证查询语法错误 (尝试 {attempt + 1}/{max_retries}): {errors}")
                            continue

                # 使用稳定哈希，避免 PYTHONHASHSEED 导致 task_id 每次进程都不同
                result_digest = hashlib.md5(analysis_result.encode("utf-8")).hexdigest()[:12]
                task_id = f"{self.kind}_verification_{result_digest}"
                query_file = create_temporary_qlpack(verification_ql, self.language, task_id=task_id)

                exec_result = execute_codeql_query(
                    verification_ql,
                    self.database_path,
                    self.language,
                    query_file,
                    output_dir=query_file.parent / "results",
                )

                if not exec_result.get("success"):
                    logger.warning(f"查询执行失败 (尝试 {attempt + 1}/{max_retries}): {exec_result.get('output', '')}")
                    continue

                sarif_path = exec_result.get("sarif_path")
                is_empty = is_empty_result(sarif_path)

                if is_empty:
                    logger.warning(f"{self.label} 分析验证失败：查询返回空结果 (尝试 {attempt + 1}/{max_retries})")
                    failed_queries.append({
                        "attempt": attempt + 1,
                        "query": verification_ql,
                        "reason": f"查询返回空结果（未找到任何匹配的 {self.label} 点）"
                    })
                    continue
                else:
                    logger.info("󰄬 %s 分析验证通过：查询返回了结果", self.label)

                    if event_callback:
                        await event_callback({
                            "type": "agent_complete",
                            "timestamp": datetime.now().isoformat(),
                            "agent_name": _agent_name,
                            "agent_type": _agent_type,
                            "message": f"{self.label} 分析验证通过",
                            "data": {"success": True, "verification_query": verification_ql}
                        })

                    return (True, "", verification_ql)

            except Exception as e:
                logger.error(f"验证过程异常 (尝试 {attempt + 1}/{max_retries}): {e}", exc_info=True)
                continue

        error_msg = f"{self.label} 分析验证失败：已尝试 {max_retries} 次，查询始终返回空结果"
        logger.error(f"󰅙 {error_msg}")
        logger.info("将直接使用原始 %s 分析报告，不进行验证过滤", self.label)

        if event_callback:
            await event_callback({
                "type": "error",
                "timestamp": datetime.now().isoformat(),
                "agent_name": _agent_name,
                "agent_type": _agent_type,
                "message": error_msg,
                "data": {
                    "max_retries": max_retries,
                    "failed_queries": failed_queries
                }
            })

        return (False, error_msg, None)

    def _build_requirement(self, analysis_result: str) -> str:
        """基于整个分析结果构建验证需求。"""
        return f"""基于以下 {self.label} 分析结果，生成一个 CodeQL 查询来验证这些 {self.label} 点是否真实存在于代码库中。

{self.label} 分析结果：
{analysis_result}

请生成一个简单的 CodeQL 查询，匹配分析结果中提到的 {self.label} 点（函数、方法等）。查询应该能够返回至少一个结果，以证明这些 {self.label} 点确实存在。"""

    def _load_verification_template(self) -> str:
        """加载语言特定的验证模板。"""
        template = load_verification_template(self.language, self.kind)
        if template is None:
            raise ValueError(f"不支持的语言: {self.language}")
        return template

    def _build_prompt(
        self,
        requirement: str,
        template: str,
        analysis_result: str,
        failed_queries: Optional[list] = None,
    ) -> str:
        """构建完整的 Prompt（使用占位符系统）。"""
        placeholder_map = build_placeholder_map(
            language=self.language,
            requirement=requirement,
            round_index=1,
            prev_original_ql=None,
            prev_fix_suggestions=None,
            ql_template="",
        )
        placeholder_map["ANALYSIS_RESULT"] = analysis_result

        prompt = apply_placeholders(template, placeholder_map)

        if failed_queries:
            failed_context = "\n\n## 󰀪 之前的尝试失败\n\n"
            failed_context += "以下查询在之前的尝试中返回了空结果，请分析问题并生成新的查询：\n\n"
            for failed in failed_queries:
                failed_context += f"### 尝试 {failed['attempt']}\n"
                failed_context += f"**失败原因**: {failed['reason']}\n\n"
                failed_context += "```ql\n"
                failed_context += failed['query']
                failed_context += "\n```\n\n"
            failed_context += "**请注意**：\n"
            failed_context += f"1. 检查函数名、{self.name_check_hint}是否正确\n"
            failed_context += "2. 检查文件路径匹配模式\n"
            failed_context += "3. 考虑使用更宽泛的匹配条件\n"
            failed_context += f"4. 确认 {self.label} 点的定义是否准确（参数位置、节点类型等）\n"
            prompt += failed_context

        return prompt

    def _extract_codeql_from_response(self, response: str) -> Optional[str]:
        """从 LLM 响应中提取 CodeQL 查询代码。"""
        patterns = [
            r"```ql\s*\n(.*?)\n```",
            r"```codeql\s*\n(.*?)\n```",
            r"```\s*\n(.*?)\n```",
        ]

        for pattern in patterns:
            match = re.search(pattern, response, re.DOTALL | re.IGNORECASE)
            if match:
                return match.group(1).strip()

        if "import " in response:
            lines = response.split("\n")
            start_idx = None
            for i, line in enumerate(lines):
                if line.strip().startswith("import "):
                    start_idx = i
                    break
            if start_idx is not None:
                return "\n".join(lines[start_idx:]).strip()

        return None
