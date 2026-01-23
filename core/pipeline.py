"""分析流水线模块
提供分析步骤的定义和流水线执行功能。

PureAuto - 纯粹的AI漏洞分析工具
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

# 导入现有的Agent
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.unified_sink_path_agent import UnifiedSinkPathAgent
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from agents.path_analysis_agent import PathAnalysisAgent


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
            agent_type=self.name
        )

        try:
            source_agent = UnifiedSourceAnalysisAgent(
                analyzer,
                context.case_paths.source_code,
                None  # 不再需要数据库路径
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
            PathAnalysisStep(),
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

            run_dir.mkdir(parents=True, exist_ok=True)

            logger.info("创建输出目录结构: %s", run_dir)

            write_analysis_output(
                result.cve_result,
                result.sink_result,
                result.source_result,
                output_path=summary_path,
                path_analysis_result=result.path_analysis_result,
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


def sanitize_tag(value: str) -> str:
    cleaned = (value or "UNKNOWN").strip().upper()
    cleaned = re.sub(r"[^A-Z0-9_-]+", "_", cleaned)
    return cleaned or "UNKNOWN"
