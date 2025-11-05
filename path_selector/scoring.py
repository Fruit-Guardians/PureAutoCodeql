"""SARIF 路径候选的评分启发式算法。"""

from __future__ import annotations

from typing import Dict

from .review import PathReviewResult

LEVEL_SCORE = {
    "error": 80.0,
    "warning": 50.0,
    "note": 30.0,
}


def _severity_score(candidate: Dict[str, any]) -> float:
    level = (candidate.get("level") or "").lower()
    score = LEVEL_SCORE.get(level, 20.0)

    severity = candidate.get("securitySeverity")
    try:
        score += float(severity) * 5.0
    except (TypeError, ValueError):
        pass
    return score


def _structure_score(candidate: Dict[str, any]) -> float:
    length = candidate.get("pathLength") or len(candidate.get("steps", []))
    return float(length) * 4.0


def score_candidate(candidate: Dict[str, any], review: PathReviewResult) -> float:
    """Combine model judgement and structural heuristics to get a ranking score."""

    score = _severity_score(candidate) + _structure_score(candidate)
    status = (review.status or "").lower()

    if status == "valid":
        score += 120.0
    elif status == "partial":
        score += 60.0
    elif status == "invalid":
        score -= 180.0
    elif status == "error":
        score -= 120.0

    score += max(min(review.confidence, 1.0), 0.0) * 40.0
    return score
