"""分析流水线的各步骤实现。"""

import json
import logging
from pathlib import Path
from typing import Any

from pure_auto_codeql.agents.cve_analysis_agent import CVEAnalysisAgent
from pure_auto_codeql.agents.path_analysis_agent import PathAnalysisAgent
from pure_auto_codeql.agents.sink_verification_agent import SinkVerificationAgent
from pure_auto_codeql.agents.source_verification_agent import SourceVerificationAgent
from pure_auto_codeql.agents.unified_sink_path_agent import UnifiedSinkPathAgent
from pure_auto_codeql.agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from pure_auto_codeql.services.llm_service import AgentResult, MultiAgentAnalyzer
from pure_auto_codeql.tools.codeql_compose import CodeQLComposeTool

from ..context import AnalysisContext
from ._llm_config import _get_llm_config_from_context
from .base import AnalysisStep

logger = logging.getLogger(__name__)


class CVEAnalysisStep(AnalysisStep):
    """CVE分析步骤。"""

    def __init__(self):
        super().__init__("cve_analysis", agent_name="CVE Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CVE分析。"""
        from pure_auto_codeql.configuration import LLMRole
        llm_config = _get_llm_config_from_context(context, LLMRole.CHAT)
        analyzer = MultiAgentAnalyzer(llm_config)
        await analyzer.initialize(
            event_callback=context.event_callback,
            language=context.language,
            workspace_path=str(context.case_paths.source_code),
            agent_type="cve_analysis",
        )

        try:
            cve_agent = CVEAnalysisAgent(analyzer)
            intel_prompt = context.intel_bundle.prompt_block() if context.intel_bundle else None

            print("=== CVE Analysis ===")
            result = await cve_agent.analyze_cve(
                Path(context.cve_assets.json_path),
                intel_prompt=intel_prompt,
                show_thinking=context.show_thinking,
                event_callback=context.event_callback,
                agent_name=self.agent_name,
                agent_type=self.name
            )

            if not result.success:
                logger.error("CVE analysis failed: %s", result.error)
            elif not context.show_thinking:
                # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
                print(result.content)

            return result
        finally:
            await analyzer.aclose()


class SinkAnalysisStep(AnalysisStep):
    """Sink路径分析步骤。"""

    def __init__(self):
        super().__init__("sink_analysis", agent_name="Sink Path Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Sink路径分析。"""
        from pure_auto_codeql.configuration import LLMRole
        llm_config = _get_llm_config_from_context(context, LLMRole.CHAT)
        analyzer = MultiAgentAnalyzer(llm_config)
        await analyzer.initialize(
            event_callback=context.event_callback,
            language=context.language,
            workspace_path=str(context.case_paths.source_code),
            agent_type="unified_sink_path"
        )

        try:
            sink_agent = UnifiedSinkPathAgent(analyzer, context.case_paths.source_code)

            print(f"=== {context.language.title()} Sink Path Analysis ===")
            result = await sink_agent.analyze_paths(
                context.language,
                context.get_result("cve_analysis").content if context.has_result("cve_analysis") else "",
                str(context.cve_assets.diff_path) if context.cve_assets.diff_path else "",
                show_thinking=context.show_thinking,
                event_callback=context.event_callback,
                agent_name=self.agent_name,
                agent_type=self.name
            )

            if not result.success:
                logger.error("%s sink analysis failed: %s", context.language.title(), result.error)
            elif not context.show_thinking:
                # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
                print(result.content)

            # 验证 Sink 分析结果（如果启用）
            config = getattr(context, '_config', None)
            if config and getattr(config, 'enable_sink_source_verification', False) and result.success:
                logger.info("开始验证 Sink 分析结果...")

                # 初始化验证 Agent
                verification_agent = SinkVerificationAgent(
                    analyzer=analyzer,
                    database_path=str(context.case_paths.db),
                    language=context.language,
                    workspace_path=str(context.case_paths.source_code),
                )

                # 执行验证
                is_valid, error_message, verification_query = await verification_agent.verify_analysis_result(
                    analysis_result=result.content,
                    max_retries=getattr(config, 'verification_retry_max', 3),
                    timeout=getattr(config, 'verification_timeout', 30),
                    show_thinking=context.show_thinking,
                    event_callback=context.event_callback,
                    agent_name="Sink Verification Agent",
                    agent_type="sink_verification",
                )

                if not is_valid:
                    # 验证失败，记录警告并标记结果为无效
                    logger.warning(f"❌ Sink 分析验证失败: {error_message}")
                    logger.warning("将使用原始 Sink 分析报告继续流程")
                    # 可以选择将结果标记为失败或添加警告信息
                    # 这里我们保留结果但添加警告标记
                    result.content = f"[VERIFICATION_FAILED] {error_message}\n\n{result.content}"
                else:
                    logger.info("✅ Sink 分析验证通过")
                    # 保存成功的验证查询到上下文，供 CodeQL 生成使用
                    if verification_query:
                        context.data["sink_verification_query"] = verification_query
                        logger.info("已保存 Sink 验证查询到上下文")

            return result
        finally:
            await analyzer.aclose()


class SourceAnalysisStep(AnalysisStep):
    """Source分析步骤。"""

    def __init__(self):
        super().__init__("source_analysis", agent_name="Source Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Source分析。"""
        from pure_auto_codeql.configuration import LLMRole
        llm_config = _get_llm_config_from_context(context, LLMRole.CHAT)
        analyzer = MultiAgentAnalyzer(llm_config)
        await analyzer.initialize(
            event_callback=context.event_callback,
            language=context.language,
            workspace_path=str(context.case_paths.source_code),
            agent_type=self.name  # 传递 agent_type 参数
        )

        try:
            source_agent = UnifiedSourceAnalysisAgent(
                analyzer,
                context.case_paths.source_code,
                str(context.case_paths.db)
            )

            print(f"=== {context.language.title()} Source Analysis ===")
            result = await source_agent.analyze_sources(
                context.language,
                context.get_result("sink_analysis").content if context.has_result("sink_analysis") else "",
                show_thinking=context.show_thinking,
                event_callback=context.event_callback,
                agent_name=self.agent_name,
                agent_type=self.name
            )

            if not result.success:
                logger.error("%s source analysis failed: %s", context.language.title(), result.error)
            elif not context.show_thinking:
                # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
                print(result.content)

            # 验证 Source 分析结果（如果启用）
            config = getattr(context, '_config', None)
            if config and getattr(config, 'enable_sink_source_verification', False) and result.success:
                logger.info("开始验证 Source 分析结果...")

                # 初始化验证 Agent
                verification_agent = SourceVerificationAgent(
                    analyzer=analyzer,
                    database_path=str(context.case_paths.db),
                    language=context.language,
                    workspace_path=str(context.case_paths.source_code),
                )

                # 执行验证
                is_valid, error_message, verification_query = await verification_agent.verify_analysis_result(
                    analysis_result=result.content,
                    max_retries=getattr(config, 'verification_retry_max', 3),
                    timeout=getattr(config, 'verification_timeout', 30),
                    show_thinking=context.show_thinking,
                    event_callback=context.event_callback,
                    agent_name="Source Verification Agent",
                    agent_type="source_verification",
                )

                if not is_valid:
                    # 验证失败，记录警告并标记结果为无效
                    logger.warning(f"❌ Source 分析验证失败: {error_message}")
                    logger.warning("将使用原始 Source 分析报告继续流程")
                    # 可以选择将结果标记为失败或添加警告信息
                    # 这里我们保留结果但添加警告标记
                    result.content = f"[VERIFICATION_FAILED] {error_message}\n\n{result.content}"
                else:
                    logger.info("✅ Source 分析验证通过")
                    # 保存成功的验证查询到上下文，供 CodeQL 生成使用
                    if verification_query:
                        context.data["source_verification_query"] = verification_query
                        logger.info("已保存 Source 验证查询到上下文")

            return result
        finally:
            await analyzer.aclose()


class PathAnalysisStep(AnalysisStep):
    """Path分析步骤。"""

    def __init__(self):
        super().__init__("path_analysis", agent_name="Path Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Path分析。"""
        from pure_auto_codeql.configuration import LLMRole

        # 检查是否有 Source 分析结果
        if not context.has_result("source_analysis"):
            logger.warning("跳过 Path 分析：缺少 Source 分析结果")
            return AgentResult(content="", success=False, error="Missing source analysis result")

        source_result = context.get_result("source_analysis")
        if not source_result.success or not source_result.content:
            logger.warning("跳过 Path 分析：Source 分析失败或结果为空")
            return AgentResult(content="", success=False, error="Source analysis failed or empty")

        # 解析 Source 分析结果中的路径数据
        try:
            source_data = json.loads(source_result.content)
            source_to_sink_paths = source_data.get("source_to_sink_paths", [])

            if not source_to_sink_paths:
                logger.info("跳过 Path 分析：未发现 Source 到 Sink 的路径")
                return AgentResult(
                    content=json.dumps({"total_paths": 0, "flow_steps": []}),
                    success=True
                )

            logger.info(f"准备分析 {len(source_to_sink_paths)} 条路径")
        except json.JSONDecodeError:
            logger.error("Source 分析结果 JSON 解析失败")
            return AgentResult(content="", success=False, error="Invalid source analysis JSON")

        llm_config = _get_llm_config_from_context(context, LLMRole.CHAT)
        analyzer = MultiAgentAnalyzer(llm_config)

        # 使用 path_analysis 的 MCP 配置
        await analyzer.initialize(
            event_callback=context.event_callback,
            language=context.language,
            workspace_path=str(context.case_paths.source_code),
            agent_type=self.name,
        )

        try:
            path_agent = PathAnalysisAgent(
                analyzer,
                context.language,
                source_root=str(context.case_paths.source_code),
            )

            print(f"=== {context.language.title()} Path Analysis ===")

            # 批量分析路径
            analysis_result = await path_agent.identify_flow_steps(
                source_to_sink_paths,
                show_thinking=context.show_thinking,
                event_callback=context.event_callback,
            )

            # 转换为 AgentResult
            content = json.dumps(analysis_result, ensure_ascii=False, indent=2)
            success = analysis_result.get("successful_paths", 0) > 0

            if not success and analysis_result.get("failed_paths", 0) > 0:
                logger.warning("所有路径分析均失败")
            elif not context.show_thinking:
                print(
                    "Path analysis completed: "
                    f"{analysis_result.get('total_flow_steps', 0)} flow steps identified"
                )

            return AgentResult(content=content, success=success)
        finally:
            await analyzer.aclose()


class CodeQLGenerationStep(AnalysisStep):
    """CodeQL查询生成步骤。"""

    def __init__(self):
        super().__init__("codeql_generation", agent_name="CodeQL Generation Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CodeQL查询生成。"""
        # 准备各分析步骤的报告内容
        cve_report = (
            context.get_result("cve_analysis").content
            if context.has_result("cve_analysis")
            else "CVE分析失败"
        )
        sink_report = (
            context.get_result("sink_analysis").content
            if context.has_result("sink_analysis")
            else "Sink分析失败"
        )
        source_report = (
            context.get_result("source_analysis").content
            if context.has_result("source_analysis")
            else "Source分析失败"
        )

        # 构建CodeQL生成需求（保持向后兼容的自然语言描述）
        codeql_requirement = f"""
        基于以下分析结果生成CodeQL查询：
        CVE路径分析结果：
        {cve_report}

        Sink路径分析结果：
        {sink_report}

        Source分析结果：
        {source_report}

        请基于上述分析生成一个完整的CodeQL查询，用于检测相关的安全漏洞。
        """

        # 获取路径分析结果（如果存在）
        path_analysis_results = None
        if context.has_result("path_analysis"):
            path_result = context.get_result("path_analysis")
            if path_result.success and path_result.content:
                try:
                    path_analysis_results = json.loads(path_result.content)
                except json.JSONDecodeError:
                    logger.warning("Path 分析结果 JSON 解析失败")

        # 使用推理模型
        from pure_auto_codeql.configuration import LLMRole
        think_config = _get_llm_config_from_context(context, LLMRole.THINK)
        codeql_analyzer = MultiAgentAnalyzer(think_config)
        await codeql_analyzer.initialize(
            event_callback=context.event_callback,
            language=context.language,
            workspace_path=str(context.case_paths.source_code),
            agent_type=self.name,
        )

        try:
            codeql_tool = CodeQLComposeTool(
                analyzer=codeql_analyzer,
                database_path=str(context.case_paths.db),
                language=context.language,
                enable_error_tidy=getattr(context._config, "enable_error_tidy", False),
                enable_source_sink_fallback=getattr(
                    context._config, "enable_source_sink_fallback", False
                ),
                fallback_empty_retry_max=getattr(
                    context._config, "fallback_empty_retry_max", 5
                ),
            )

            # 获取验证查询（如果有）
            sink_verification_query = context.data.get("sink_verification_query")
            source_verification_query = context.data.get("source_verification_query")

            # 记录验证查询的使用情况
            if sink_verification_query:
                logger.info("✅ 使用 Sink 验证查询作为参考")
            if source_verification_query:
                logger.info("✅ 使用 Source 验证查询作为参考")

            print("=== CodeQL Query Generation ===")
            print("🔍 调用CodeQLComposeTool进行查询生成和语法检查...")
            compose_output = await codeql_tool._arun(
                codeql_requirement,
                show_thinking=context.show_thinking,
                event_callback=context.event_callback,
                agent_name=self.agent_name,
                agent_type=self.name,
                path_analysis_results=path_analysis_results,  # 传递路径分析结果
                cve_analysis_report=cve_report,
                sink_analysis_report=sink_report,
                source_analysis_report=source_report,
                sink_verification_query=sink_verification_query,  # 传递 Sink 验证查询
                source_verification_query=source_verification_query,  # 传递 Source 验证查询
            )
            print(compose_output)
            context.data["codeql_execution_result"] = codeql_tool.last_execution_result

            # Normalize output into AgentResult for downstream consumers
            if isinstance(compose_output, AgentResult):
                return compose_output

            if isinstance(compose_output, str):
                normalized = compose_output.strip().lower()
                if normalized.startswith("error") or normalized.startswith("failed"):
                    return AgentResult(content="", success=False, error=compose_output)

                return AgentResult(content=compose_output, success=True)

            # Fallback: treat unexpected payload as failure with repr for debugging
            return AgentResult(
                content="",
                success=False,
                error=f"Unexpected CodeQL generation output type: {type(compose_output).__name__}"
            )
        finally:
            await codeql_analyzer.aclose()
