"""分析编排器模块

提供分析过程的整体协调和管理功能。
"""

import logging
from pathlib import Path
from typing import Optional

from core.context import AnalysisConfig, AnalysisContext, AnalysisResult
from core.pipeline import AnalysisPipeline
from services.language_detector import LanguageDetector
from utils.case import CveAssets, discover_cve_assets, resolve_case
from utils.intel import IntelBundle, collect_intel

logger = logging.getLogger(__name__)


class AnalysisOrchestrator:
    """分析编排器 - 协调各个组件执行完整的分析流程。"""

    def __init__(self, config: Optional[AnalysisConfig] = None):
        """初始化分析编排器。"""
        self.config = config or AnalysisConfig()
        self.language_detector = LanguageDetector()

    async def analyze_case(self, case_id: str, language: Optional[str] = None) -> AnalysisResult:
        """执行完整的案例分析。"""
        try:
            # 解析案例结构
            case_paths = resolve_case(case_id)
            logger.info(f"正在分析案例: {case_id}")
            logger.debug(f"案例根目录: {case_paths.root}")
            if self.config.show_thinking:
                logger.debug("启用AI思考过程显示模式")

            # 发现CVE资产
            cve_assets = discover_cve_assets(case_paths)
            logger.info(f"分析CVE: {cve_assets.cve_id}")
            if cve_assets.json_path.exists():
                logger.debug(f"JSON文件: {cve_assets.json_path} (本地)")
            else:
                logger.debug(f"JSON文件: {cve_assets.json_path} (网络获取)")
            if cve_assets.diff_path:
                file_type = "Diff" if cve_assets.diff_path.suffix == ".diff" else "Patch"
                logger.debug(f"{file_type}文件: {cve_assets.diff_path} (本地)")
            else:
                logger.debug("Diff/Patch文件: 无")

            # 收集情报数据
            logger.info("正在收集漏洞情报...")
            intel_bundle = collect_intel(
                case_paths, cve_assets, use_cache=not self.config.refresh_intel
            )

            if intel_bundle.used_cache:
                logger.info("使用缓存的情报数据")
            else:
                logger.info("已获取最新情报数据")

            # 检测语言 (如果未指定)
            if not language:
                language = self.language_detector.detect_language(case_paths)
                logger.info(f"检测到语言: {language}")
            else:
                logger.info(f"使用指定语言: {language}")

            # 创建分析上下文
            context = AnalysisContext(
                case_id=case_id,
                case_paths=case_paths,
                cve_assets=cve_assets,
                language=language,
                intel_bundle=intel_bundle,
                show_thinking=self.config.show_thinking,
                event_callback=self.config.event_callback
            )

            # 创建并执行分析流水线（包含输出处理）
            pipeline = AnalysisPipeline.create_default_pipeline()
            result = await pipeline.execute(context, config=self.config)

            # 显示执行摘要
            self._print_execution_summary(result)

            return result

        except Exception as e:
            logger.exception(f"案例分析错误: {e}")
            error_result = AnalysisResult(
                case_id=case_id,
                language="unknown",
                success=False,
                error_message=str(e)
            )
            return error_result

    def _print_execution_summary(self, result: AnalysisResult) -> None:
        """打印执行摘要。"""
        logger.info("=== 分析完成 ===")
        if result.execution_time:
            logger.info(f"总耗时: {result.execution_time:.2f} 秒")

        logger.info(f"案例ID: {result.case_id}")
        logger.info(f"编程语言: {result.language}")
        logger.info(f"分析状态: {'成功' if result.success else '失败'}")

        if result.error_message:
            logger.error(f"错误信息: {result.error_message}")

        if result.is_complete():
            logger.info("所有分析步骤均已完成")
        else:
            logger.warning("部分分析步骤未完成或失败")

    @classmethod
    def create_from_args(cls, args) -> "AnalysisOrchestrator":
        """从命令行参数创建编排器。"""
        config = AnalysisConfig(
            show_thinking=args.stream,
            refresh_intel=getattr(args, 'refresh_intel', False),
            output_file=getattr(args, 'output', None)
        )
        return cls(config)
