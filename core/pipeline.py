"""分析流水线模块
提供分析步骤的定义和流水线执行功能。
"""

import logging
import time
import json
import re
import shutil
from datetime import datetime
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, List, Optional

from core.context import AnalysisContext, AnalysisConfig, AnalysisResult
from services.llm_service import AgentResult, MultiAgentAnalyzer
from services.path_selection import PathSelectionService, PathSelectionResult


def _get_llm_config_from_context(context: AnalysisContext, role) -> Any:
    """从上下文中获取LLM配置，支持配置中的模型、API Key和Base URL"""
    config = getattr(context, '_config', None)
    if not config:
        from config import get_resilient_llm_config, LLMRole
        return get_resilient_llm_config(role)

    from config import get_llm_config, LLMRole
    # 从配置中获取参数
    provider = config.llm_provider
    model_name = config.think_model if role == LLMRole.THINK else config.chat_model
    api_key = config.api_key
    base_url = config.base_url

    return get_llm_config(
        role,
        provider_name=provider,
        model_name=model_name,
        api_key=api_key,
        base_url=base_url
    )

# 配置日志
logger = logging.getLogger(__name__)

# 导入现有的Agent和工具
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.unified_sink_path_agent import UnifiedSinkPathAgent
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from agents.path_analysis_agent import PathAnalysisAgent
from tools.codeql_compose import CodeQLComposeTool


class AnalysisStep(ABC):
    """分析步骤抽象基类。"""

    def __init__(self, name: str, agent_name: str = None):
        self.name = name
        self.agent_name = agent_name or name

    @abstractmethod
    async def execute(self, context: AnalysisContext) -> Any:
        """执行分析步骤。"""
        pass


class CVEAnalysisStep(AnalysisStep):
    """CVE分析步骤。"""

    def __init__(self):
        super().__init__("cve_analysis", agent_name="CVE Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CVE分析。"""
        from config import LLMRole
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
                print(f"CVE analysis failed: {result.error}")
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
        from config import LLMRole
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
                print(f"{context.language.title()} sink analysis failed: {result.error}")
            elif not context.show_thinking:
                # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
                print(result.content)

            return result
        finally:
            await analyzer.aclose()


class SourceAnalysisStep(AnalysisStep):
    """Source分析步骤。"""

    def __init__(self):
        super().__init__("source_analysis", agent_name="Source Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Source分析。"""
        from config import LLMRole
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
                print(f"{context.language.title()} source analysis failed: {result.error}")
            elif not context.show_thinking:
                # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
                print(result.content)

            return result
        finally:
            await analyzer.aclose()


class PathAnalysisStep(AnalysisStep):
    """Path分析步骤。"""

    def __init__(self):
        super().__init__("path_analysis", agent_name="Path Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Path分析。"""
        from config import LLMRole
        
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
        from config import LLMRole
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
            )
            print(compose_output)

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


class AnalysisPipeline:
    """分析流水线，管理分析步骤的执行。"""

    def __init__(self, steps: List[AnalysisStep]):
        self.steps = steps

    @classmethod
    def create_default_pipeline(cls) -> "AnalysisPipeline":
        """创建默认的分析流水线。"""
        steps = [
            CVEAnalysisStep(),
            SinkAnalysisStep(),
            SourceAnalysisStep(),
            PathAnalysisStep(),  # 添加路径分析步骤
            CodeQLGenerationStep(),
        ]
        return cls(steps)

    async def execute(self, context: AnalysisContext, config: Optional[AnalysisConfig] = None) -> AnalysisResult:
        """执行分析流水线。"""
        start_time = time.time()
        result = AnalysisResult(
            case_id=context.case_id,
            language=context.language
        )
        config = config or AnalysisConfig()

        # 将配置存储到上下文中，供步骤使用
        context._config = config

        try:
            for step in self.steps:
                logger.info(f"开始执行步骤: {step.name}")
                step_result = await step.execute(context)
                context.add_result(step.name, step_result)

                # 将结果映射到AnalysisResult
                if step.name == "cve_analysis":
                    result.cve_result = step_result
                elif step.name == "sink_analysis":
                    result.sink_result = step_result
                elif step.name == "source_analysis":
                    result.source_result = step_result
                elif step.name == "path_analysis":
                    result.path_analysis_result = step_result
                elif step.name == "codeql_generation":
                    result.codeql_result = step_result

                # 检查步骤是否成功
                if hasattr(step_result, 'success') and not step_result.success:
                    result.success = False
                    result.error_message = f"步骤 {step.name} 失败: {step_result.error}"
                    logger.error(f"步骤 {step.name} 执行失败: {step_result.error}")
                    break

        except Exception as e:
            result.success = False
            result.error_message = str(e)
            logger.exception(f"分析流水线执行异常: {e}")

        finally:
            result.execution_time = time.time() - start_time

            # 整合输出文件到统一文件夹
            await self._consolidate_output_files(context, result, config)

        return result

    async def _consolidate_output_files(
        self,
        context: AnalysisContext,
        result: AnalysisResult,
        config: AnalysisConfig
    ) -> None:
        """整合所有输出文件到统一的文件夹结构。"""
        try:
            from utils.io import write_analysis_output

            cve_id = getattr(context.cve_assets, "cve_id", None) if context.cve_assets else None
            case_tag = sanitize_tag(cve_id or context.case_id or "UNKNOWN")
            output_base = Path(config.output_base_dir)
            timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')

            run_dir = output_base / case_tag / timestamp
            summary_path = run_dir / "summary.md"
            sarif_dir = run_dir / "sarif"
            codeql_dir = run_dir / "codeql"
            path_selection_dir = run_dir / "path-selection"

            run_dir.mkdir(parents=True, exist_ok=True)
            sarif_dir.mkdir(parents=True, exist_ok=True)
            codeql_dir.mkdir(parents=True, exist_ok=True)
            path_selection_dir.mkdir(parents=True, exist_ok=True)

            logger.info("创建输出目录结构: %s", run_dir)

            write_analysis_output(
                result.cve_result,
                result.sink_result,
                result.source_result,
                output_path=summary_path,
                path_analysis_result=result.path_analysis_result,  # 传递路径分析结果
                codeql_result=result.codeql_result,
                codeql_execution_result=result.codeql_execution_result,
                language=result.language,
                intel_bundle=context.intel_bundle,
                encoding=config.output_encoding,
            )

            # 若用户额外指定了输出文件，复制一份总结文件供兼容
            if config.output_file:
                custom_path = Path(config.output_file)
                custom_path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(summary_path, custom_path)
                logger.info("额外写入自定义输出文件: %s", custom_path)

            # 清理旧的运行目录
            if config.keep_output_dirs > 0:
                self._cleanup_old_output_dirs(output_base, config.keep_output_dirs)

            result.output_directory = str(run_dir)
            logger.info("✅ 分析结果已整合至: %s", run_dir)

            json_result_path = await self._process_sarif_files(
                output_base=output_base,
                sarif_dir=sarif_dir,
                codeql_dir=codeql_dir,
                config=config,
            )

            if json_result_path:
                path_selection_output = await self._run_path_selection(
                    context=context,
                    run_dir=run_dir,
                    summary_path=summary_path,
                    result_json_path=json_result_path,
                    path_selection_dir=path_selection_dir,
                    config=config,
                )
                if path_selection_output:
                    context.add_result("path_selection", path_selection_output)
                    result.path_selection_result = path_selection_output
            else:
                logger.warning("路径选择模块跳过：未生成 dataFlowPath JSON")

        except PermissionError as e:
            error_msg = f"文件权限错误，无法写入输出文件: {e}"
            logger.error(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"
        except OSError as e:
            error_msg = f"文件系统错误，无法写入输出文件: {e}"
            logger.error(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"
        except Exception as e:
            error_msg = f"输出文件整合失败: {e}"
            logger.exception(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"

    async def _process_sarif_files(
        self,
        *,
        output_base: Path,
        sarif_dir: Path,
        codeql_dir: Path,
        config: AnalysisConfig,
    ) -> Optional[Path]:
        """处理SARIF文件：复制、转换、删除，并返回生成的JSON路径。"""
        try:
            sarif_files = list(output_base.glob('result_*.sarif')) if output_base.exists() else []
            if not sarif_files:
                logger.debug("未找到SARIF文件")
                return None

            latest_sarif = max(sarif_files, key=lambda x: x.stat().st_mtime)
            target_sarif = sarif_dir / "codeql-run.sarif"

            # 先复制文件
            shutil.copy2(latest_sarif, target_sarif)
            logger.info("已复制SARIF文件至: %s", target_sarif)

            # 转换SARIF为JSON（在复制成功后）
            json_path: Optional[Path] = None
            try:
                from utils.sarif_utils import sarif_to_all_paths

                with open(target_sarif, 'r', encoding='utf-8') as f:
                    sarif_data = json.load(f)

                json_data = sarif_to_all_paths(sarif_data)
                json_path = codeql_dir / "all-paths-raw.json"

                with open(json_path, 'w', encoding=config.output_encoding) as f:
                    json.dump(json_data, f, ensure_ascii=False, indent=2)

                # 验证JSON文件写入成功
                if json_path.exists() and json_path.stat().st_size > 0:
                    logger.info("✅ SARIF文件已转换为JSON: %s", json_path.name)
                else:
                    logger.warning("⚠️ JSON文件写入可能失败: %s", json_path)

            except Exception as e:
                logger.warning(f"SARIF转JSON失败: {e}，但SARIF文件已保存")
                json_path = None

            # 所有处理完成后再删除原文件
            try:
                latest_sarif.unlink()
                logger.debug(f"已删除原始SARIF文件: {latest_sarif}")
            except Exception as e:
                logger.warning(f"删除原始SARIF文件失败: {e}，文件已保留")

            return json_path

        except Exception as e:
            logger.exception(f"处理SARIF文件时出错: {e}")
        return None

    def _cleanup_old_output_dirs(self, output_base: Path, keep_count: int) -> None:
        """清理旧的输出目录，只保留最近的N个运行记录。"""
        try:
            if not output_base.exists():
                return

            run_dirs: List[Path] = []
            for case_dir in output_base.iterdir():
                if not case_dir.is_dir():
                    continue
                for run_dir in case_dir.iterdir():
                    if run_dir.is_dir():
                        run_dirs.append(run_dir)

            if len(run_dirs) <= keep_count:
                return

            # 按修改时间排序，删除最旧的
            sorted_dirs = sorted(run_dirs, key=lambda x: x.stat().st_mtime, reverse=True)
            dirs_to_remove = sorted_dirs[keep_count:]

            for old_dir in dirs_to_remove:
                try:
                    shutil.rmtree(old_dir)
                    logger.info("已清理旧输出目录: %s", old_dir)
                    parent = old_dir.parent
                    if parent != output_base and not any(parent.iterdir()):
                        parent.rmdir()
                except Exception as e:
                    logger.warning("清理目录失败 %s: %s", old_dir, e)

        except Exception as e:
            logger.warning(f"清理旧输出目录时出错: {e}")

    async def _run_path_selection(
        self,
        *,
        context: AnalysisContext,
        run_dir: Path,
        summary_path: Path,
        result_json_path: Path,
        path_selection_dir: Path,
        config: AnalysisConfig,
    ) -> Optional[PathSelectionResult]:
        """执行路径选择并输出报告。"""
        if not summary_path.exists():
            logger.warning("路径选择跳过：summary.md 不存在 (%s)", summary_path)
            return None
        if not result_json_path.exists():
            logger.warning("路径选择跳过：dataFlowPath JSON 不存在 (%s)", result_json_path)
            return None

        try:
            from config import LLMRole

            llm_config = _get_llm_config_from_context(context, LLMRole.CHAT)
            service = PathSelectionService(llm_config, language=context.language)

            # 诊断日志：打印源代码根目录信息
            source_root = context.case_paths.source_code
            logger.info("📍 路径选择诊断信息:")
            logger.info("   - source_root: %s", source_root)
            logger.info("   - source_root 存在: %s", source_root.exists())
            if source_root.exists():
                # 列出源根目录的前几个文件/目录
                try:
                    entries = list(source_root.iterdir())[:10]
                    logger.info("   - source_root 内容样本: %s", [e.name for e in entries])
                except Exception:
                    pass

            selection = await service.select_best_paths(
                output_md_path=summary_path,
                result_json_path=result_json_path,
                source_root=source_root,
                top_k=3,
                enable_clustering=True,
                event_callback=context.event_callback,
                debug=context.show_thinking,
            )

            report_path = path_selection_dir / "report.md"
            detail_path = path_selection_dir / "selection.json"
            dataflow_path = path_selection_dir / "dataflow.json"

            # 生成三个文件：
            # 1. 可读报告（Markdown）
            report_path.write_text(selection.to_markdown(), encoding=config.output_encoding)
            # 2. 详细数据（包含所有元数据）
            with open(detail_path, "w", encoding=config.output_encoding) as handler:
                json.dump(selection.to_dict(), handler, ensure_ascii=False, indent=2)
            # 3. 最终简洁结果（只包含选择的路径）
            with open(dataflow_path, "w", encoding=config.output_encoding) as handler:
                json.dump(selection.to_dataflow_json(), handler, ensure_ascii=False, indent=2)

            logger.info("✅ 路径选择结果已输出:")
            logger.info("   📄 报告: %s", report_path.relative_to(run_dir))
            logger.info("   📊 详细数据: %s", detail_path.relative_to(run_dir))
            logger.info("   ✅ 最终结果: %s", dataflow_path.relative_to(run_dir))

            # ---------------------------------------------------------
            # 额外输出到根目录 results/CVE-XXXX-XXXX/
            # ---------------------------------------------------------
            try:
                cve_id = getattr(context.cve_assets, "cve_id", None) if context.cve_assets else None
                target_id = cve_id or context.case_id or "UNKNOWN"
                
                if target_id and target_id != "UNKNOWN":
                    # 确保目录名合法
                    target_id_clean = self._sanitize_tag(target_id)
                    root_results_dir = Path("results") / target_id_clean
                    root_results_dir.mkdir(parents=True, exist_ok=True)

                    # 1. 输出 CodeQL 查询文件 (.ql)
                    ql_content = ""
                    codeql_res = context.get_result("codeql_generation")
                    if codeql_res and hasattr(codeql_res, "content"):
                        raw_content = codeql_res.content
                        # 尝试提取 QL 代码块
                        match = re.search(r"```ql\s*(.*?)```", raw_content, re.DOTALL)
                        if match:
                            ql_content = match.group(1).strip()
                        else:
                            # 尝试其他格式或直接使用内容（如果看起来像代码）
                            match_generic = re.search(r"```\s*(.*?)```", raw_content, re.DOTALL)
                            if match_generic:
                                ql_content = match_generic.group(1).strip()
                            else:
                                # 如果没有代码块，且内容不包含过多文本，可能就是纯代码
                                # 或者保留原样
                                ql_content = raw_content

                    if ql_content:
                        ql_path = root_results_dir / f"{target_id_clean}_query.ql"
                        ql_path.write_text(ql_content, encoding=config.output_encoding)
                        logger.info("   ✅ [Root Export] QL Query: %s", ql_path)

                    # 2. 输出路径选择 JSON (.json)
                    path_json_path = root_results_dir / f"{target_id_clean}_path.json"
                    with open(path_json_path, "w", encoding=config.output_encoding) as handler:
                        json.dump(selection.to_dataflow_json(), handler, ensure_ascii=False, indent=2)
                    logger.info("   ✅ [Root Export] Path JSON: %s", path_json_path)
            
            except Exception as e:
                logger.warning(f"根目录 results 额外输出失败: {e}")

            return selection
        except Exception as exc:
            logger.exception("路径选择执行失败: %s", exc)
            return None

    def _sanitize_tag(self, value: str) -> str:
        return sanitize_tag(value)


def sanitize_tag(value: str) -> str:
    cleaned = (value or "UNKNOWN").strip().upper()
    cleaned = re.sub(r"[^A-Z0-9_-]+", "_", cleaned)
    return cleaned or "UNKNOWN"
