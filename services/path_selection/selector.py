"""路径选择总控

负责将 CodeQL dataFlowPath 结合 CVE 背景，完成特征提取、确定性打分、
LLM 精排、验证与报告输出。
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List

from .context_extractor import CVEContextExtractor
from .path_feature_extractor import PathFeatureExtractor
from .path_ranker import PathRanker
from .selection_formatter import score_to_payload
from .llm_analyzer import LLMPathAnalyzer
from .path_verifier import PathVerifier

logger = logging.getLogger(__name__)


@dataclass
class PathSelectionResult:
    """路径选择结果."""

    selected_paths: List[Dict[str, Any]]
    selection_reasoning: str
    verification_summary: Dict[str, Any]
    coverage_analysis: Dict[str, Any]
    all_paths_count: int
    language: str

    def to_dict(self) -> Dict[str, Any]:
        return {
            "selected_paths": self.selected_paths,
            "selection_reasoning": self.selection_reasoning,
            "verification_summary": self.verification_summary,
            "coverage_analysis": self.coverage_analysis,
            "all_paths_count": self.all_paths_count,
            "language": self.language,
        }

    def to_markdown(self) -> str:
        lines = [
            "# 路径选择报告\n",
            "## 概述",
            f"- 语言: {self.language}",
            f"- 总路径数: {self.all_paths_count}",
            f"- 选中路径数: {len(self.selected_paths)}\n",
            "## 选择原因",
            f"{self.selection_reasoning}\n",
            "## 选中的路径\n",
        ]

        for idx, path in enumerate(self.selected_paths, 1):
            selection_info = path.get("selection_info", {})
            confidence = selection_info.get("confidence") or selection_info.get(
                "deterministic_score", "N/A"
            )
            lines.append(f"### 路径 {idx} (置信度: {confidence})")
            lines.append(f"**选择原因**: {selection_info.get('reason', 'N/A')}\n")

            source = path.get("source_analysis", {})
            sink = path.get("sink_analysis", {})

            lines.append("**Source端**:")
            lines.append(f"- 位置: {source.get('location', 'N/A')}")
            lines.append(f"- 描述: {source.get('description', 'N/A')}\n")

            lines.append("**Sink端**:")
            lines.append(f"- 位置: {sink.get('location', 'N/A')}")
            lines.append(f"- 描述: {sink.get('description', 'N/A')}\n")

        lines.append("## 验证摘要")
        summary = self.verification_summary
        lines.append(f"- all_valid: {summary.get('all_valid')}")
        if summary.get("issues"):
            lines.append("- 存在的问题:")
            for issue in summary["issues"]:
                lines.append(f"  - {issue}")

        coverage = self.coverage_analysis or {}
        lines.append("\n## 覆盖分析")
        if coverage.get("sink_files"):
            lines.append(f"- Sink文件: {', '.join(coverage['sink_files'])}")
        if coverage.get("source_types"):
            lines.append(f"- Source类型: {', '.join(coverage['source_types'])}")
        if coverage.get("dangerous_apis"):
            lines.append(f"- 危险API: {', '.join(coverage['dangerous_apis'])}")

        return "\n".join(lines)


class PathSelectionService:
    """路径选择服务."""

    def __init__(self, llm_client, language: str):
        self.language = (language or "").lower()
        self.context_extractor = CVEContextExtractor()
        self.feature_extractor = PathFeatureExtractor(language=self.language)
        self.path_ranker = PathRanker(language=self.language)
        self.llm_analyzer = LLMPathAnalyzer(llm_client, language=self.language)
        self.path_verifier = PathVerifier(language=self.language)

        logger.info("PathSelectionService initialized, language=%s", self.language)

    async def select_best_paths(
        self,
        output_md_path: str | Path,
        result_json_path: str | Path,
        source_root: str | Path,
        top_k: int = 3,
        enable_clustering: bool = True,  # 兼容旧接口，当前已由 ranker 处理
    ) -> PathSelectionResult:
        del enable_clustering

        logger.info("=" * 60)
        logger.info("开始路径选择处理")
        logger.info("=" * 60)

        cve_context = await self._extract_cve_context(output_md_path)
        logger.info("  · 漏洞类型=%s", cve_context.get("vulnerability_type", "N/A"))

        all_paths = self._load_paths(result_json_path)
        logger.info("  · 总路径数=%s", len(all_paths))
        if not all_paths:
            logger.warning("未找到任何路径, 返回空结果")
            return self._empty_result()

        path_features = await self.feature_extractor.build_features(
            all_paths, source_root, cve_context
        )
        logger.info("  · PathFeatures=%s", len(path_features))
        if not path_features:
            logger.warning("未能创建PathFeatures, 返回空结果")
            return self._empty_result()

        rank_result = self.path_ranker.rank(path_features, cve_context, top_k=top_k)
        candidate_pool_size = max(top_k * 2, 6)
        candidate_scores = rank_result.ordered[:candidate_pool_size]
        logger.info(
            "  · 确定性筛选=%s coverage=%s",
            len(candidate_scores),
            rank_result.coverage,
        )

        llm_result = await self.llm_analyzer.analyze_and_select(
            cve_context=cve_context,
            candidate_scores=candidate_scores,
            top_k=top_k,
        )
        selected_paths = llm_result.get("selected_paths", []) or []
        selection_reasoning = llm_result.get("reasoning", "")
        coverage_analysis = llm_result.get("coverage_analysis") or rank_result.coverage

        if not selected_paths:
            logger.warning("LLM未返回有效结果, 回退到确定性打分排序")
            selected_paths = [
                score_to_payload(score, self.language)
                for score in rank_result.selected[:top_k]
            ]
            selection_reasoning = "LLM fallback: 使用确定性打分排序的结果"
            coverage_analysis = rank_result.coverage

        verification = self.path_verifier.verify_paths(
            selected_paths,
            cve_context,
            all_paths,
        )

        result = PathSelectionResult(
            selected_paths=verification["paths"],
            selection_reasoning=selection_reasoning,
            verification_summary=verification["summary"],
            coverage_analysis=coverage_analysis,
            all_paths_count=len(all_paths),
            language=self.language,
        )

        logger.info("=" * 60)
        logger.info("路径选择处理完成")
        logger.info("=" * 60)
        return result

    async def _extract_cve_context(self, output_md_path: str | Path) -> Dict[str, Any]:
        output_md_path = Path(output_md_path)
        if not output_md_path.exists():
            logger.warning("output.md文件不存在: %s", output_md_path)
            return {}
        with open(output_md_path, "r", encoding="utf-8") as handle:
            content = handle.read()
        return self.context_extractor.extract(content)

    def _load_paths(self, result_json_path: str | Path) -> List[Dict[str, Any]]:
        result_json_path = Path(result_json_path)
        if not result_json_path.exists():
            logger.error("result.json文件不存在: %s", result_json_path)
            return []
        with open(result_json_path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        return data.get("dataFlowPath", [])

    def _empty_result(self) -> PathSelectionResult:
        return PathSelectionResult(
            selected_paths=[],
            selection_reasoning="未找到任何路径",
            verification_summary={"all_valid": True, "issues": []},
            coverage_analysis={},
            all_paths_count=0,
            language=self.language,
        )


__all__ = ["PathSelectionService", "PathSelectionResult"]
