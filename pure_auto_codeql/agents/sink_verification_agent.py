"""Sink 验证 Agent - 生成并执行 Sink 验证查询。

该模块实现了 Sink 点的验证逻辑，通过生成简单的 CodeQL 查询来验证
LLM 识别的 Sink 点是否真实存在于代码库中。
"""

import re
from datetime import datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any, Dict, Optional, Tuple, Callable

from pure_auto_codeql.services import (
    build_placeholder_map,
    apply_placeholders,
    CodeQLLSPService,
)
from pure_auto_codeql.utils.codeql import (
    execute_codeql_query,
    is_empty_result,
    create_temporary_qlpack,
)
from pure_auto_codeql.utils.logger import get_logger
from pure_auto_codeql.prompts.verification_prompt_manager import (
    load_verification_template,
    build_verification_requirement,
)

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer

logger = get_logger(__name__)


class SinkVerificationAgent:
    """Sink 点验证 Agent，生成并执行 isSink 验证查询。"""

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        database_path: str,
        language: str,
        lsp_service: Optional[CodeQLLSPService] = None,
        workspace_path: Optional[str] = None,
    ):
        """
        初始化 Sink 验证 Agent。

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
        """
        验证整个 Sink 分析结果是否有效（通过生成查询并检查是否返回结果）。

        Args:
            analysis_result: 整个 Sink 分析结果（JSON 字符串）
            max_retries: 最大重试次数
            timeout: 超时时间（秒）
            show_thinking: 是否显示 LLM 思考过程
            event_callback: 事件回调函数
            agent_name: Agent 名称（用于事件回调）
            agent_type: Agent 类型（用于 MCP 配置）

        Returns:
            (is_valid, error_message, verification_query): 
                - is_valid: 验证是否通过
                - error_message: 错误信息（如果失败）
                - verification_query: 成功的验证查询（如果成功），否则为 None
        """
        _agent_name = agent_name or "Sink Verification Agent"
        _agent_type = agent_type or "sink_verification"
        
        logger.info(f"开始验证 Sink 分析结果（整体验证）")
        
        # 发送 agent_start 事件
        if event_callback:
            await event_callback({
                "type": "agent_start",
                "timestamp": datetime.now().isoformat(),
                "agent_name": _agent_name,
                "agent_type": _agent_type,
                "message": f"开始验证 Sink 分析结果",
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

        # 1. 构建验证需求（基于整个分析结果）
        requirement = self._build_requirement(analysis_result)
        logger.debug(f"验证需求: {requirement}")

        # 2. 加载验证模板
        try:
            template = self._load_verification_template()
        except Exception as e:
            error_msg = f"加载验证模板失败: {e}"
            logger.error(error_msg)
            return (False, error_msg)

        # 3. 重试循环
        failed_queries = []  # 记录失败的查询
        for attempt in range(max_retries):
            try:
                logger.info(f"Sink 验证尝试 {attempt + 1}/{max_retries}")

                # 4. 构建 Prompt（使用占位符系统，包含之前失败的查询）
                prompt = self._build_prompt(
                    requirement, 
                    template, 
                    analysis_result,
                    failed_queries=failed_queries if attempt > 0 else None
                )

                # 5. LLM 生成验证查询
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

                # 6. 提取 CodeQL 查询
                verification_ql = self._extract_codeql_from_response(result.content)
                if not verification_ql:
                    logger.warning(f"未能从响应中提取 CodeQL 查询 (尝试 {attempt + 1}/{max_retries})")
                    continue

                logger.debug(f"生成的验证查询:\n{verification_ql}")

                # 7. LSP 语法检查（如果可用）
                if self.lsp_service:
                    syntax_result = self.lsp_service.check_syntax(verification_ql)
                    if syntax_result.get("diagnostics"):
                        errors = [d for d in syntax_result["diagnostics"] if d.get("severity", 1) == 1]
                        if errors:
                            logger.warning(f"验证查询语法错误 (尝试 {attempt + 1}/{max_retries}): {errors}")
                            continue

                # 8. 准备查询文件（创建 qlpack.yml 等）
                task_id = f"sink_verification_{hash(analysis_result)}"
                query_file = create_temporary_qlpack(verification_ql, self.language, task_id=task_id)
                
                # 9. 执行验证查询
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

                # 10. 检查结果
                sarif_path = exec_result.get("sarif_path")
                is_empty = is_empty_result(sarif_path)

                if is_empty:
                    # 空结果 = Sink 分析无效（可能是幻觉）
                    logger.warning(f"Sink 分析验证失败：查询返回空结果 (尝试 {attempt + 1}/{max_retries})")
                    # 记录失败的查询，供下一轮参考
                    failed_queries.append({
                        "attempt": attempt + 1,
                        "query": verification_ql,
                        "reason": "查询返回空结果（未找到任何匹配的 Sink 点）"
                    })
                    continue
                else:
                    # 非空结果 = Sink 分析有效
                    logger.info(f"✅ Sink 分析验证通过：查询返回了结果")
                    
                    # 发送 agent_complete 事件
                    if event_callback:
                        await event_callback({
                            "type": "agent_complete",
                            "timestamp": datetime.now().isoformat(),
                            "agent_name": _agent_name,
                            "agent_type": _agent_type,
                            "message": "Sink 分析验证通过",
                            "data": {"success": True, "verification_query": verification_ql}
                        })
                    
                    # 返回成功的验证查询
                    return (True, "", verification_ql)

            except Exception as e:
                logger.error(f"验证过程异常 (尝试 {attempt + 1}/{max_retries}): {e}", exc_info=True)
                continue

        # 所有重试失败
        error_msg = f"Sink 分析验证失败：已尝试 {max_retries} 次，查询始终返回空结果"
        logger.error(f"❌ {error_msg}")
        logger.info(f"将直接使用原始 Sink 分析报告，不进行验证过滤")
        
        # 发送 error 事件
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
        
        # 返回失败，但不返回验证查询（将使用原始报告）
        return (False, error_msg, None)

    def _build_requirement(self, analysis_result: str) -> str:
        """
        基于整个 Sink 分析结果构建验证需求。

        Args:
            analysis_result: Sink 分析结果（JSON 字符串）

        Returns:
            自然语言需求描述
        """
        # 简化需求：验证分析结果中的 Sink 点是否真实存在
        return f"""基于以下 Sink 分析结果，生成一个 CodeQL 查询来验证这些 Sink 点是否真实存在于代码库中。

Sink 分析结果：
{analysis_result}

请生成一个简单的 CodeQL 查询，匹配分析结果中提到的 Sink 点（函数、方法等）。查询应该能够返回至少一个结果，以证明这些 Sink 点确实存在。"""

    def _load_verification_template(self) -> str:
        """
        加载语言特定的验证模板。

        Returns:
            模板内容字符串

        Raises:
            FileNotFoundError: 如果模板文件不存在
            ValueError: 如果语言不支持
        """
        template = load_verification_template(self.language, "sink")
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
        """
        构建完整的 Prompt（使用占位符系统）。

        Args:
            requirement: 自然语言需求
            template: 验证模板
            analysis_result: Sink 分析结果（JSON 字符串）
            failed_queries: 之前失败的查询列表（可选）

        Returns:
            完整的 Prompt 字符串
        """
        # 构建占位符映射
        placeholder_map = build_placeholder_map(
            language=self.language,
            requirement=requirement,
            round_index=1,
            prev_original_ql=None,
            prev_fix_suggestions=None,
            ql_template="",  # 验证查询不需要复杂模板
        )
        # 添加分析结果占位符
        placeholder_map["ANALYSIS_RESULT"] = analysis_result

        # 应用占位符替换
        prompt = apply_placeholders(template, placeholder_map)
        
        # 如果有失败的查询，添加到 prompt 末尾
        if failed_queries:
            failed_context = "\n\n## ⚠️ 之前的尝试失败\n\n"
            failed_context += "以下查询在之前的尝试中返回了空结果，请分析问题并生成新的查询：\n\n"
            for failed in failed_queries:
                failed_context += f"### 尝试 {failed['attempt']}\n"
                failed_context += f"**失败原因**: {failed['reason']}\n\n"
                failed_context += "```ql\n"
                failed_context += failed['query']
                failed_context += "\n```\n\n"
            failed_context += "**请注意**：\n"
            failed_context += "1. 检查函数名、类名是否正确\n"
            failed_context += "2. 检查文件路径匹配模式\n"
            failed_context += "3. 考虑使用更宽泛的匹配条件\n"
            failed_context += "4. 确认 Sink 点的定义是否准确（参数位置、节点类型等）\n"
            prompt += failed_context
        
        return prompt

    def _extract_codeql_from_response(self, response: str) -> Optional[str]:
        """
        从 LLM 响应中提取 CodeQL 查询代码。

        Args:
            response: LLM 响应内容

        Returns:
            提取的 CodeQL 查询，如果未找到则返回 None
        """
        # 尝试匹配 ```ql 或 ```codeql 代码块
        patterns = [
            r"```ql\s*\n(.*?)\n```",
            r"```codeql\s*\n(.*?)\n```",
            r"```\s*\n(.*?)\n```",  # 通用代码块
        ]

        for pattern in patterns:
            match = re.search(pattern, response, re.DOTALL | re.IGNORECASE)
            if match:
                return match.group(1).strip()

        # 如果没有代码块，尝试查找 import 语句开始的内容
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
