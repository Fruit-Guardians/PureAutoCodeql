"""分析流水线编排器。"""

import json
import logging
import re
import shutil
import subprocess
import time
from dataclasses import asdict
from datetime import datetime
from pathlib import Path
from typing import Any, List, Optional

from pure_auto_codeql.analysis_models import ErrorDetail, StepResult
from pure_auto_codeql.observability import step_duration, step_span
from pure_auto_codeql.services.artifacts import ArtifactRegistry
from pure_auto_codeql.services.path_selection import PathSelectionResult, PathSelectionService
from pure_auto_codeql.utils.terminal_ui import print_stage_end, print_stage_start

from ..context import AnalysisConfig, AnalysisContext, AnalysisResult
from ._llm_config import _get_llm_config_from_context
from .base import AnalysisStep, SkippedAnalysisStep
from .steps import (
    CodeQLGenerationStep,
    CVEAnalysisStep,
    PathAnalysisStep,
    SinkAnalysisStep,
    SourceAnalysisStep,
)
from .tags import sanitize_tag

logger = logging.getLogger(__name__)


class AnalysisPipeline:
    """分析流水线，管理分析步骤的执行。"""

    def __init__(self, steps: List[AnalysisStep]):
        self.steps = steps

    @classmethod
    def create_default_pipeline(
        cls,
        config: Optional[AnalysisConfig] = None,
    ) -> "AnalysisPipeline":
        """创建默认的分析流水线。"""
        config = config or AnalysisConfig()
        config.validate()
        configured_steps = [
            ("cve_analysis", config.enable_cve_analysis, CVEAnalysisStep),
            ("sink_analysis", config.enable_sink_analysis, SinkAnalysisStep),
            ("source_analysis", config.enable_source_analysis, SourceAnalysisStep),
            ("path_analysis", config.enable_path_analysis, PathAnalysisStep),
            ("codeql_generation", config.enable_codeql_generation, CodeQLGenerationStep),
        ]
        steps = [
            step_cls() if enabled else SkippedAnalysisStep(name, "disabled by analysis configuration")
            for name, enabled, step_cls in configured_steps
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
        config.validate()

        # 将配置存储到上下文中，供步骤使用
        context._config = config

        try:
            total_steps = len(self.steps)
            for step_index, step in enumerate(self.steps, start=1):
                print_stage_start(step_index, total_steps, step.name)
                logger.debug("开始执行步骤: %s", step.name)
                step_started = time.monotonic()
                with step_span(context.case_id, step.name):
                    step_result = await step.execute(context)
                step_duration.record(
                    time.monotonic() - step_started,
                    {"analysis.step": step.name, "analysis.language": context.language},
                )
                if not isinstance(step_result, StepResult):
                    step_result = StepResult(
                        content=getattr(step_result, "content", step_result),
                        success=getattr(step_result, "success", True),
                        error=getattr(step_result, "error", None),
                    )
                context.add_result(step.name, step_result)
                result.step_results[step.name] = step_result
                print_stage_end(
                    step.name,
                    step_result.status,
                    time.monotonic() - step_started,
                )

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
                    result.codeql_execution_result = context.data.get("codeql_execution_result")

                # 检查步骤是否成功
                if hasattr(step_result, 'success') and not step_result.success:
                    result.success = False
                    result.error_message = f"步骤 {step.name} 失败: {step_result.error}"
                    result.error_detail = step_result.error_detail or ErrorDetail(
                        code="pipeline_step_failed",
                        message=result.error_message,
                        details={"step": step.name},
                    )
                    logger.error(f"步骤 {step.name} 执行失败: {step_result.error}")
                    break

        except Exception as e:
            result.success = False
            result.error_message = str(e)
            result.error_detail = ErrorDetail(
                code="pipeline_exception",
                message=str(e),
                category="pipeline",
            )
            logger.exception(f"分析流水线执行异常: {e}")

        finally:
            result.execution_time = time.time() - start_time

            # 整合输出文件到统一文件夹
            await self._consolidate_output_files(context, result, config)
            result.finalize_outcome()

        return result

    async def _consolidate_output_files(
        self,
        context: AnalysisContext,
        result: AnalysisResult,
        config: AnalysisConfig
    ) -> None:
        """整合所有输出文件到统一的文件夹结构。"""
        try:
            from pure_auto_codeql.utils.io import write_analysis_output

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
            logger.info("󰄬 分析结果已整合至: %s", run_dir)

            json_result_path = await self._process_sarif_files(
                output_base=output_base,
                sarif_dir=sarif_dir,
                codeql_dir=codeql_dir,
                config=config,
                execution_result=result.codeql_execution_result,
            )

            if json_result_path and config.enable_path_selection:
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
                    result.step_results["path_selection"] = StepResult(
                        content={
                            "selected_paths": len(
                                getattr(path_selection_output, "selected_paths", []) or []
                            )
                        },
                        metrics={
                            "selected_paths": len(
                                getattr(path_selection_output, "selected_paths", []) or []
                            )
                        },
                    )
                else:
                    result.step_results["path_selection"] = StepResult.skipped(
                        "path selection produced no result"
                    )
            else:
                reason = (
                    "disabled by analysis configuration"
                    if not config.enable_path_selection
                    else "未生成 dataFlowPath JSON"
                )
                logger.warning("路径选择模块跳过：%s", reason)
                result.step_results["path_selection"] = StepResult.skipped(reason)

            result.finalize_outcome()
            manifest_path = self._write_run_manifest(
                context=context,
                result=result,
                config=config,
                run_dir=run_dir,
            )
            if manifest_path:
                registry = ArtifactRegistry(run_dir)
                result.artifacts = registry.scan(exclude={"manifest.json"})
                result.artifacts.append(registry.register(manifest_path))
                logger.info("运行清单已写入: %s", manifest_path)

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

    def _write_run_manifest(
        self,
        *,
        context: AnalysisContext,
        result: AnalysisResult,
        config: AnalysisConfig,
        run_dir: Path,
    ) -> Optional[Path]:
        """Write a reproducibility manifest after all run artifacts are finalized."""

        try:
            registry = ArtifactRegistry(run_dir)
            artifacts = [artifact.to_dict() for artifact in registry.scan(exclude={"manifest.json"})]

            effective_config = asdict(config)
            effective_config["api_key"] = "***" if effective_config.get("api_key") else None
            effective_config["event_callback"] = bool(config.event_callback)

            manifest = {
                "schema_version": 1,
                "case_id": result.case_id,
                "cve_id": getattr(context.cve_assets, "cve_id", None),
                "language": result.language,
                "outcome": result.outcome.value,
                "success": result.success,
                "execution_time_seconds": result.execution_time,
                "git_commit": self._command_version(["git", "rev-parse", "HEAD"]),
                "codeql_version": self._command_version(["codeql", "version", "--format=terse"]),
                "effective_config": effective_config,
                "steps": {
                    name: step_result.to_dict()
                    for name, step_result in result.step_results.items()
                },
                "warnings": result.warnings,
                "error": result.error_detail.to_dict() if result.error_detail else None,
                "artifacts": artifacts,
            }
            manifest_path = run_dir / "manifest.json"
            manifest_path.write_text(
                json.dumps(manifest, ensure_ascii=False, indent=2, default=str),
                encoding=config.output_encoding,
            )
            return manifest_path
        except Exception:
            logger.warning("写入运行清单失败", exc_info=True)
            return None

    @staticmethod
    def _command_version(command: list[str]) -> Optional[str]:
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=5,
                check=False,
            )
            if completed.returncode == 0:
                return completed.stdout.strip() or completed.stderr.strip() or None
        except (OSError, subprocess.SubprocessError):
            return None
        return None

    async def _process_sarif_files(
        self,
        *,
        output_base: Path,
        sarif_dir: Path,
        codeql_dir: Path,
        config: AnalysisConfig,
        execution_result: Optional[Any] = None,
    ) -> Optional[Path]:
        """处理SARIF文件：复制、转换、删除，并返回生成的JSON路径。"""
        try:
            explicit_sarif = None
            if isinstance(execution_result, dict):
                explicit_sarif = execution_result.get("sarif_path")
            latest_sarif = Path(explicit_sarif) if explicit_sarif else None

            if not latest_sarif or not latest_sarif.exists():
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
                from pure_auto_codeql.utils.sarif_utils import sarif_to_all_paths

                with open(target_sarif, 'r', encoding='utf-8') as f:
                    sarif_data = json.load(f)

                json_data = sarif_to_all_paths(sarif_data)
                json_path = codeql_dir / "all-paths-raw.json"

                with open(json_path, 'w', encoding=config.output_encoding) as f:
                    json.dump(json_data, f, ensure_ascii=False, indent=2)

                # 验证JSON文件写入成功
                if json_path.exists() and json_path.stat().st_size > 0:
                    logger.info("󰄬 SARIF文件已转换为JSON: %s", json_path.name)
                else:
                    logger.warning("󰀪 JSON文件写入可能失败: %s", json_path)

            except Exception as e:
                logger.warning(f"SARIF转JSON失败: {e}，但SARIF文件已保存")
                json_path = None

            # 只清理兼容回退的全局 SARIF；工具专属目录下的文件保留用于调试。
            if latest_sarif.parent == output_base:
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
            from pure_auto_codeql.configuration import LLMRole

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

            logger.info("󰄬 路径选择结果已输出:")
            logger.info("   󰈙 报告: %s", report_path.relative_to(run_dir))
            logger.info("   📊 详细数据: %s", detail_path.relative_to(run_dir))
            logger.info("   󰄬 最终结果: %s", dataflow_path.relative_to(run_dir))

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
                        logger.info("   󰄬 [Root Export] QL Query: %s", ql_path)

                    # 2. 输出路径选择 JSON (.json)
                    path_json_path = root_results_dir / f"{target_id_clean}_path.json"
                    with open(path_json_path, "w", encoding=config.output_encoding) as handler:
                        json.dump(selection.to_dataflow_json(), handler, ensure_ascii=False, indent=2)
                    logger.info("   󰄬 [Root Export] Path JSON: %s", path_json_path)

            except Exception as e:
                logger.warning(f"根目录 results 额外输出失败: {e}")

            return selection
        except Exception as exc:
            logger.exception("路径选择执行失败: %s", exc)
            return None

    def _sanitize_tag(self, value: str) -> str:
        return sanitize_tag(value)
