"""Deterministic scoring + selection logic for CodeQL paths."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Dict, List, Sequence, Tuple

from .features import PathFeatures


DEFAULT_WEIGHTS: Dict[str, float] = {
    "file": 0.25,
    "sink": 0.25,
    "source": 0.2,
    "flow": 0.15,
    "coverage": 0.15,
}

PROFILE_OVERRIDES: Dict[str, Dict[str, float]] = {
    "ssrf": {"sink": 0.35, "file": 0.25, "source": 0.2, "flow": 0.1, "coverage": 0.1},
    "rce": {"sink": 0.3, "source": 0.3, "file": 0.2, "flow": 0.1, "coverage": 0.1},
    "deserialization": {"sink": 0.3, "file": 0.25},
}

USER_INPUT_KEYWORDS = ("request", "input", "param", "body", "query", "argv", "env")


@dataclass
class PathScore:
    """Score breakdown for a path."""

    feature: PathFeatures
    metrics: Dict[str, float]
    total: float
    reasons: List[str]
    weights: Dict[str, float]


@dataclass
class RankResult:
    """Full ranking outcome."""

    ordered: List[PathScore]
    selected: List[PathScore]
    coverage: Dict[str, List[str]]


class PathRanker:
    """Score and pick the most representative paths before invoking LLMs."""

    def __init__(self, language: str):
        self.language = (language or "").lower()

    def rank(
        self,
        features: List[PathFeatures],
        cve_context: Dict[str, str],
        top_k: int = 3,
    ) -> RankResult:
        scores = [self._score_path(feat, cve_context) for feat in features]
        ordered = sorted(scores, key=lambda item: item.total, reverse=True)

        selected: List[PathScore] = []
        used_indices = set()
        used_sinks = set()
        used_source_types = set()

        # Phase 1: ensure sink diversity
        for score in ordered:
            sink_key = self._normalize_path(score.feature.sink.file)
            if sink_key and sink_key not in used_sinks:
                selected.append(score)
                used_indices.add(score.feature.index)
                used_sinks.add(sink_key)
            if len(selected) >= top_k:
                break

        # Phase 2: ensure source diversity
        if len(selected) < top_k:
            for score in ordered:
                if score.feature.index in used_indices:
                    continue
                src_type = (
                    score.feature.source.analysis.get("type")
                    if score.feature.source.analysis
                    else ""
                )
                if src_type and src_type not in used_source_types:
                    selected.append(score)
                    used_indices.add(score.feature.index)
                    used_source_types.add(src_type)
                if len(selected) >= top_k:
                    break

        # Phase 3: fill remaining with best scores
        if len(selected) < top_k:
            for score in ordered:
                if score.feature.index in used_indices:
                    continue
                selected.append(score)
                used_indices.add(score.feature.index)
                if len(selected) >= top_k:
                    break

        coverage = {
            "sink_files": [sc.feature.sink.file for sc in selected if sc.feature.sink.file],
            "source_types": [
                sc.feature.source.analysis.get("type", "unknown")
                for sc in selected
                if sc.feature.source.analysis
            ],
            "dangerous_apis": sorted(
                {api for sc in selected for api in sc.feature.dangerous_apis}
            ),
        }

        return RankResult(ordered=ordered, selected=selected, coverage=coverage)

    def _score_path(self, feature: PathFeatures, cve_context: Dict[str, str]) -> PathScore:
        weights = self._weights_for_context(cve_context)
        file_score, file_reason = self._score_file(feature, cve_context)
        sink_score, sink_reason = self._score_sink(feature, cve_context)
        src_score, src_reason = self._score_source(feature)
        flow_score, flow_reason = self._score_flow(feature)
        cov_score, cov_reason = self._score_coverage(feature, cve_context)

        metrics = {
            "file": file_score,
            "sink": sink_score,
            "source": src_score,
            "flow": flow_score,
            "coverage": cov_score,
        }

        total = sum(metrics[key] * weights.get(key, 0) for key in metrics)
        reasons = [file_reason, sink_reason, src_reason, flow_reason, cov_reason]

        return PathScore(
            feature=feature,
            metrics=metrics,
            total=total,
            reasons=reasons,
            weights=dict(weights),
        )

    def _weights_for_context(self, cve_context: Dict[str, str]) -> Dict[str, float]:
        vul_type = (cve_context.get("vulnerability_type") or "").lower()
        for key, overrides in PROFILE_OVERRIDES.items():
            if key in vul_type:
                merged = dict(DEFAULT_WEIGHTS)
                merged.update(overrides)
                return merged
        return DEFAULT_WEIGHTS

    def _score_file(self, feature: PathFeatures, cve_context: Dict[str, str]) -> Tuple[float, str]:
        hints = self._collect_file_hints(cve_context)
        sink_file = feature.sink.file or ""
        if not sink_file:
            return 0.0, "缺少 sink 文件"
        if not hints:
            return 1.0, "sink 文件存在"

        normalized = self._normalize_path(sink_file)
        if any(self._normalize_path(hint) == normalized for hint in hints):
            return 1.0, f"命中文件 {sink_file}"
        if any(hint.lower() in sink_file.lower() for hint in hints):
            return 0.6, f"文件包含关键词 {hints}"
        return 0.2, "sink 文件与已知列表不匹配"

    def _score_sink(self, feature: PathFeatures, cve_context: Dict[str, str]) -> Tuple[float, str]:
        score = 0.0
        reason = []
        if feature.dangerous_apis:
            score += 0.6
            reason.append("命中危险 API")
        sink_desc = feature.sink.description.lower()
        expected_sink = (cve_context.get("expected_sink") or "").lower()
        if expected_sink and expected_sink.strip():
            for token in expected_sink.split():
                if token and token in sink_desc:
                    score += 0.3
                    reason.append(f"包含 {token}")
                    break
        if feature.sink.analysis.get("type"):
            score += 0.1
            reason.append("sink 类型已识别")
        return min(score, 1.0), "；".join(reason) or "缺少 sink 证据"

    def _score_source(self, feature: PathFeatures) -> Tuple[float, str]:
        analysis = feature.source.analysis or {}
        src_type = (analysis.get("type") or "").lower()
        desc = feature.source.description.lower()

        score = 0.0
        if src_type and src_type != "unknown":
            score += 0.6
        if any(keyword in desc for keyword in USER_INPUT_KEYWORDS):
            score += 0.3
        if not feature.sanitizer_hits:
            score += 0.1
        return min(score, 1.0), f"source 类型 {src_type or 'unknown'}"

    def _score_flow(self, feature: PathFeatures) -> Tuple[float, str]:
        path_len = feature.path_length
        if path_len <= 0:
            return 0.0, "无步骤"
        if 4 <= path_len <= 8:
            length_score = 1.0
        elif 2 <= path_len <= 12:
            length_score = 0.7
        else:
            length_score = 0.3

        cross_file_bonus = 0.1 if feature.metadata.get("cross_file_flow") else 0.0

        return min(1.0, length_score + cross_file_bonus), f"步数 {path_len}"

    def _score_coverage(self, feature: PathFeatures, cve_context: Dict[str, str]) -> Tuple[float, str]:
        keywords = self._collect_keywords(cve_context)
        if not keywords:
            return 0.5, "无关键词约束"
        if not feature.matched_keywords:
            return 0.1, "未匹配关键词"
        ratio = min(1.0, len(set(feature.matched_keywords)) / len(keywords))
        return ratio, f"匹配 {len(set(feature.matched_keywords))}/{len(keywords)} 个关键词"

    def _collect_file_hints(self, cve_context: Dict[str, str]) -> List[str]:
        hints: List[str] = []
        for key in ("expected_sink", "expected_source", "technical_details"):
            value = cve_context.get(key)
            if not value:
                continue
            hints.extend([token for token in value.split() if token.endswith((".py", ".java", ".c", ".cpp"))])
        return hints

    def _collect_keywords(self, cve_context: Dict[str, str]) -> List[str]:
        keywords: List[str] = []
        for key in ("expected_sink", "expected_source", "technical_details"):
            value = cve_context.get(key)
            if not value:
                continue
            keywords.extend([token.lower() for token in value.split() if len(token) >= 4])
        return keywords

    def _normalize_path(self, path: str) -> str:
        return path.replace("\\", "/").lower()


__all__ = ["PathRanker", "PathScore", "RankResult"]
