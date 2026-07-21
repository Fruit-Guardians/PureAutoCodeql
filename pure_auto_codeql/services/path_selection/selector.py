"""路径选择总控

负责将 CodeQL dataFlowPath 结合 CVE 背景，完成特征提取、确定性打分、
LLM 精排、验证与报告输出。
"""

from __future__ import annotations
import json
import logging
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Awaitable, Callable, Dict, List, Optional, Sequence
from langchain_openai import ChatOpenAI
from .context_extractor import CVEContextExtractor
from .path_feature_extractor import PathFeatureExtractor
from .path_ranker import PathRanker
from .selection_formatter import score_to_payload
from .llm_analyzer import LLMPathAnalyzer
from .path_verifier import PathVerifier
from .path_enricher import PathEnricher

logger = logging.getLogger(__name__)


EventCallback = Callable[[Dict[str, Any]], Awaitable[None]]


@dataclass
class PathSelectionResult:
    """路径选择结果."""

    selected_paths: List[Dict[str, Any]]
    selection_reasoning: str
    verification_summary: Dict[str, Any]
    coverage_analysis: Dict[str, Any]
    all_paths_count: int
    language: str
    cve_id: str | None = None
    debug_info: Dict[str, Any] | None = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "selected_paths": self.selected_paths,
            "selection_reasoning": self.selection_reasoning,
            "verification_summary": self.verification_summary,
            "coverage_analysis": self.coverage_analysis,
            "all_paths_count": self.all_paths_count,
            "language": self.language,
            "cve_id": self.cve_id,
            "debug_info": self.debug_info,
        }

    def to_markdown(self) -> str:
        lines = [
            "# 路径选择报告\n",
            "## 概述",
            f"- CVE: {self.cve_id or 'N/A'}",
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

            analysis_steps = selection_info.get("analysis_steps") or []
            if analysis_steps:
                lines.append("**思维链路:**")
                for step in analysis_steps:
                    lines.append(f"- {step}")
                lines.append("")

            evidence = selection_info.get("evidence") or []
            if evidence:
                lines.append("**证据引用:**")
                for item in evidence:
                    role = item.get("block_role", "N/A")
                    location = item.get("location", "N/A")
                    insight = item.get("insight", "")
                    lines.append(f"- {role} @ {location}: {insight}")
                lines.append("")

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

        if self.debug_info:
            lines.append("\n## 调试信息")
            deterministic = self.debug_info.get("deterministic_scores") or []
            if deterministic:
                lines.append("### 确定性打分明细")
                for item in deterministic:
                    lines.append(
                        f"- Path#{item['index']} total={item['total']:.4f} "
                        f"metrics={item['metrics']} weights={item['weights']}"
                    )
            llm_section = self.debug_info.get("llm_selection") or []
            if llm_section:
                lines.append("\n### LLM 选择摘要")
                for entry in llm_section:
                    lines.append(
                        f"- candidate_rank={entry['candidate_rank']} "
                        f"confidence={entry['confidence']} reason={entry['reason']}"
                    )

        return "\n".join(lines)

    def to_dataflow_json(self) -> Dict[str, Any]:
        """导出符合 CodeQL dataFlowPath 结构的 JSON."""

        def normalize_thread_flows(path: Dict[str, Any]) -> List[Dict[str, Any]]:
            original = path.get("original_path") or {}
            thread_flows = (
                original.get("threadFlows")
                or path.get("threadFlows")
                or []
            )
            normalized: List[Dict[str, Any]] = []
            for flow in thread_flows:
                steps = flow.get("steps") or []
                step_payload: List[Dict[str, Any]] = []
                for idx, step in enumerate(steps, start=1):
                    location = step.get("location") or {}
                    step_payload.append(
                        {
                            "stepNumber": step.get("stepNumber", idx),
                            "location": {
                                "file": location.get("file"),
                                "startLine": location.get("startLine"),
                                "startColumn": location.get("startColumn"),
                                "endColumn": location.get("endColumn"),
                                "description": location.get("description"),
                                "nodeType": location.get("nodeType") or step.get("nodeType"),
                            },
                        }
                    )
                normalized.append({"steps": step_payload})
            return normalized

        data_flow_paths = [
            {"threadFlows": normalize_thread_flows(path)}
            for path in self.selected_paths
        ]

        return {"dataFlowPath": data_flow_paths}


class PathSelectionService:
    """路径选择服务."""

    def __init__(self, llm_client, language: str):
        """初始化路径选择服务
        
        Args:
            llm_client: 可以是 LLMConfig 配置对象或已实例化的 LLM 客户端
            language: 编程语言
        """
        self.language = (language or "").lower()
        
        # 如果传入的是 LLMConfig，则创建 ChatOpenAI 实例
        if hasattr(llm_client, 'model') and hasattr(llm_client, 'api_key'):
            # 这是一个 LLMConfig 对象
            llm_client = ChatOpenAI(
                model=llm_client.model,
                api_key=llm_client.api_key,
                base_url=llm_client.base_url,
                temperature=getattr(llm_client, 'temperature', 0),
                max_tokens=getattr(llm_client, 'max_tokens', None),
                streaming=getattr(llm_client, 'streaming', True),
            )
        
        self.context_extractor = CVEContextExtractor()
        self.feature_extractor = PathFeatureExtractor(language=self.language)
        self.path_ranker = PathRanker(language=self.language)
        self.llm_analyzer = LLMPathAnalyzer(llm_client, language=self.language)
        self.path_verifier = PathVerifier(language=self.language)
        self.path_enricher = PathEnricher(language=self.language)

        logger.info("PathSelectionService initialized, language=%s", self.language)

    async def select_best_paths(
        self,
        output_md_path: str | Path,
        result_json_path: str | Path,
        source_root: str | Path,
        top_k: int = 3,
        enable_clustering: bool = True,  # 兼容旧接口，当前已由 ranker 处理
        event_callback: Optional[EventCallback] = None,
        debug: bool = False,
    ) -> PathSelectionResult:
        del enable_clustering

        logger.info("=" * 60)
        logger.info("开始路径选择处理")
        logger.info("=" * 60)

        cve_context = await self._extract_cve_context(output_md_path)
        logger.info("  · 漏洞类型=%s", cve_context.get("vulnerability_type", "N/A"))
        self._log_cve_context(cve_context)


        all_paths = self._load_paths(result_json_path)
        logger.info("  · 总路径数=%s", len(all_paths))
        if not all_paths:
            logger.warning("未找到任何路径, 返回空结果")
            return self._empty_result()

        path_features = await self.feature_extractor.build_features(
            all_paths, source_root, cve_context
        )
        logger.info("  · PathFeatures=%s", len(path_features))
        self._log_feature_previews(path_features)

        if not path_features:
            logger.warning("未能创建PathFeatures, 返回空结果")
            return self._empty_result()

        rank_result = self.path_ranker.rank(path_features, cve_context, top_k=top_k)
        candidate_pool_size = max(top_k * 2, 6)
        candidate_scores = rank_result.ordered[:candidate_pool_size]
        logger.info(
            "  · 确定性筛选候选=%s coverage=%s",
            len(candidate_scores),
            rank_result.coverage,
        )
        self._log_candidate_overview(candidate_scores)

        await self._emit_event(
            event_callback,
            event_type="agent_start",
            message="开始执行 LLM 点读精排",
            data={
                "total_candidates": len(candidate_scores),
                "top_k": top_k,
                "coverage": rank_result.coverage,
            },
        )

        llm_result = await self.llm_analyzer.analyze_and_select(
            cve_context=cve_context,
            candidate_scores=candidate_scores,
            top_k=top_k,
            event_callback=event_callback,
            stream_meta={
                "agent_name": "PathSelection",
                "step_name": "LLM 点读精排",
                "cve_id": cve_context.get("cve_id"),
            },
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

        # 输出 LLM 选择结果摘要
        selection_data = {
            "selection": [
                {
                    "index": path.get("index"),
                    "candidate_rank": path.get("selection_info", {}).get(
                        "candidate_rank"
                    ),
                    "confidence": path.get("selection_info", {}).get("confidence"),
                    "reason": path.get("selection_info", {}).get("reason"),
                    "analysis_steps": path.get("selection_info", {}).get(
                        "analysis_steps"
                    ),
                }
                for path in selected_paths
            ]
        }
        
        await self._emit_event(
            event_callback,
            event_type="info",
            message="LLM 精排结论",
            data=selection_data,
        )
        
        # 如果没有 callback，直接打印到控制台
        if not event_callback:
            self._print_selection_summary(selection_data["selection"])

        verification = self.path_verifier.verify_paths(
            selected_paths,
            cve_context,
            all_paths,
        )
        self._log_selected_paths(verification["paths"])

        await self._emit_event(
            event_callback,
            event_type="agent_complete",
            message="LLM 点读精排完成",
            data={
                "selected_count": len(verification["paths"]),
                "invalid_paths": verification["summary"].get("invalid_count"),
                "coverage": coverage_analysis,
            },
        )

        debug_info = None
        if debug:
            debug_info = self._build_debug_info(
                candidate_scores=candidate_scores,
                rank_result=rank_result,
                selected_paths=verification["paths"],
                verification=verification,
            )
            self._display_debug_info(debug_info)

        result = PathSelectionResult(
            selected_paths=verification["paths"],
            selection_reasoning=selection_reasoning,
            verification_summary=verification["summary"],
            coverage_analysis=coverage_analysis,
            all_paths_count=len(all_paths),
            language=self.language,
            cve_id=cve_context.get("cve_id"),
            debug_info=debug_info,
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

    def _build_debug_info(
        self,
        *,
        candidate_scores: Sequence[Any],
        rank_result: Any,
        selected_paths: Sequence[Dict[str, Any]],
        verification: Dict[str, Any],
    ) -> Dict[str, Any]:
        deterministic = []
        for score in candidate_scores:
            deterministic.append(
                {
                    "index": getattr(score.feature, "index", None),
                    "total": score.total,
                    "flow_summary": score.feature.flow_summary,
                    "metrics": {k: round(v, 4) for k, v in score.metrics.items()},
                    "weights": {k: round(v, 4) for k, v in score.weights.items()},
                    "reasons": score.reasons,
                }
            )

        llm_selection = []
        for path in selected_paths:
            info = path.get("selection_info", {})
            llm_selection.append(
                {
                    "candidate_rank": info.get("candidate_rank"),
                    "index": path.get("index"),
                    "confidence": info.get("confidence"),
                    "reason": info.get("reason"),
                    "llm_alignment_score": info.get("llm_alignment_score"),
                    "analysis_steps": info.get("analysis_steps"),
                    "evidence": info.get("evidence"),
                }
            )

        return {
            "deterministic_scores": deterministic,
            "coverage_after_ranker": rank_result.coverage,
            "llm_selection": llm_selection,
            "verification": verification,
        }

    def _display_debug_info(self, debug_info: Dict[str, Any]) -> None:
        if not debug_info:
            return
        logger.info("=== PathSelection Debug Info ===")
        for item in debug_info.get("deterministic_scores", []):
            logger.info(
                "Path#%s total=%.4f metrics=%s weights=%s flow=%s",
                item.get("index"),
                item.get("total"),
                item.get("metrics"),
                item.get("weights"),
                item.get("flow_summary"),
            )
        for entry in debug_info.get("llm_selection", []):
            logger.info(
                "Selected candidate=%s confidence=%s reason=%s",
                entry.get("candidate_rank"),
                entry.get("confidence"),
                entry.get("reason"),
            )
        logger.info("Verification summary: %s", debug_info.get("verification", {}))

    def _print_selection_summary(self, selection: List[Dict[str, Any]]) -> None:
        """在控制台输出选择结果摘要。"""
        import sys
        
        sys.stdout.write("\n")
        sys.stdout.write("=" * 60 + "\n")
        sys.stdout.write("📊 LLM 精排结论\n")
        sys.stdout.write("=" * 60 + "\n")
        
        for item in selection:
            idx = item.get("index", "?")
            rank = item.get("candidate_rank", "?")
            confidence = item.get("confidence", "N/A")
            reason = item.get("reason", "N/A")
            
            sys.stdout.write(f"\n  ✓ 路径 #{idx} [候选排名: {rank}, 置信度: {confidence}]\n")
            sys.stdout.write(f"    原因: {reason}\n")
            
            steps = item.get("analysis_steps") or []
            if steps:
                sys.stdout.write("    分析步骤:\n")
                for step in steps:
                    sys.stdout.write(f"      · {step}\n")
        
        sys.stdout.write("\n")
        sys.stdout.flush()

    async def _emit_event(
        self,
        event_callback: Optional[EventCallback],
        *,
        event_type: str,
        message: str,
        data: Optional[Dict[str, Any]] = None,
    ) -> None:
        if not event_callback:
            return
        payload = {
            "type": event_type,
            "timestamp": datetime.now().isoformat(),
            "agent_name": "PathSelection",
            "agent_type": "path_selection",
            "step_name": "path_selection_llm",
            "message": message,
            "data": data or {},
        }
        try:
            await event_callback(payload)
        except Exception:
            logger.debug("event_callback 推送失败", exc_info=True)

    def _log_cve_context(self, cve_context: Dict[str, Any]) -> None:
        if not cve_context:
            logger.debug("CVE 上下文为空")
            return
        logger.debug("CVE 关键信息:")
        for key in ("cve_id", "vulnerability_type", "expected_sink", "expected_source"):
            if cve_context.get(key):
                logger.debug("  - %s: %s", key, cve_context.get(key))

    def _log_feature_previews(self, features: Sequence[Any], limit: int = 3) -> None:
        if not logger.isEnabledFor(logging.DEBUG):
            return
        for feature in list(features)[:limit]:
            logger.debug(
                "  · Feature[%s]: %s dangerous=%s keywords=%s",
                feature.index,
                feature.flow_summary,
                feature.dangerous_apis,
                feature.matched_keywords,
            )

    def _log_candidate_overview(
        self, candidate_scores: Sequence[Any], limit: int = 6
    ) -> None:
        if not logger.isEnabledFor(logging.DEBUG):
            return
        logger.debug("候选路径预览:")
        for score in list(candidate_scores)[:limit]:
            feature = score.feature
            logger.debug(
                "  - #%s det=%.3f flow=%s danger=%s",
                getattr(feature, "index", "?"),
                score.total,
                feature.flow_summary,
                ", ".join(feature.dangerous_apis) or "无",
            )

    def _log_selected_paths(self, paths: Sequence[Dict[str, Any]], limit: int = 3) -> None:
        if not logger.isEnabledFor(logging.DEBUG):
            return
        logger.debug("最终选择路径预览:")
        for idx, path in enumerate(list(paths)[:limit], start=1):
            src = path.get("source_location", {})
            sink = path.get("sink_location", {})
            info = path.get("selection_info", {})
            logger.debug(
                "  · Path%s confidence=%s source=%s:%s sink=%s:%s reason=%s",
                idx,
                info.get("confidence"),
                src.get("file"),
                src.get("startLine"),
                sink.get("file"),
                sink.get("startLine"),
                info.get("reason"),
            )


__all__ = ["PathSelectionService", "PathSelectionResult"]

