"""结合过滤、LLM 审查和评分的选择管道。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from utils.sarif_utils import collect_path_candidates, filter_paths_by_sink_scope

from .review import PathReviewResult
from .scoring import score_candidate


@dataclass
class SelectionResult:
    candidate: Dict[str, any]
    review: PathReviewResult
    score: float


@dataclass
class SelectionSummary:
    selected: List[SelectionResult]
    evaluated: List[SelectionResult]


def _sink_key(candidate: Dict[str, any]) -> Tuple[str, int]:
    steps = candidate.get("steps", [])
    if not steps:
        return ("", 0)
    sink = steps[-1].get("location", {}) or {}
    return (sink.get("file") or "", sink.get("startLine") or 0)


def build_dataflow_json(results: Sequence[SelectionResult]) -> Dict[str, any]:
    payload = []
    for item in results:
        payload.append(
            {
                "threadFlows": [{"steps": item.candidate.get("steps", [])}],
                "analysis": {
                    "score": item.score,
                    "status": item.review.status,
                    "reason": item.review.reasoning,
                    "confidence": item.review.confidence,
                },
            }
        )
    return {"dataFlowPath": payload}


def build_report(summary: SelectionSummary) -> Dict[str, any]:
    return {
        "selected": [
            {
                "score": item.score,
                "status": item.review.status,
                "reason": item.review.reasoning,
                "confidence": item.review.confidence,
                "missing_checks": item.review.missing_checks,
                "sink": _sink_key(item.candidate),
            }
            for item in summary.selected
        ],
        "evaluated": [
            {
                "score": item.score,
                "status": item.review.status,
                "reason": item.review.reasoning,
                "confidence": item.review.confidence,
                "sink": _sink_key(item.candidate),
            }
            for item in summary.evaluated
        ],
    }


async def _evaluate_candidates(
    candidates: Sequence[Dict[str, any]],
    review_callback,
) -> List[SelectionResult]:
    results: List[SelectionResult] = []
    for candidate in candidates:
        review: PathReviewResult = await review_callback(candidate)
        score = score_candidate(candidate, review)
        results.append(SelectionResult(candidate=candidate, review=review, score=score))
    return results


def _deduplicate(results: Sequence[SelectionResult], top_k: int) -> List[SelectionResult]:
    seen: Dict[Tuple[str, int], SelectionResult] = {}
    ordered: List[SelectionResult] = []

    for item in results:
        key = _sink_key(item.candidate)
        existing = seen.get(key)
        if existing is None or item.score > existing.score:
            seen[key] = item

    for item in sorted(seen.values(), key=lambda r: r.score, reverse=True):
        ordered.append(item)
        if len(ordered) >= top_k:
            break
    return ordered


async def select_paths(
    sarif: Dict[str, any],
    cve_summary: str,
    *,
    top_k: int = 3,
    max_candidates: Optional[int] = None,
    threadflow_index: int = -1,
    rule_filter: Optional[str] = None,
    make_relative_to: Optional[str] = None,
    allowed_sink_files: Optional[Iterable[str]] = None,
    require_same_file: bool = False,
    review_callback=None,
) -> SelectionSummary:
    candidates = collect_path_candidates(
        sarif,
        threadflow_index=threadflow_index,
        rule_filter=rule_filter,
        make_relative_to=make_relative_to,
    )

    filtered = filter_paths_by_sink_scope(
        candidates,
        allowed_sink_files=allowed_sink_files,
        require_same_file=require_same_file,
    )

    if max_candidates is not None:
        filtered = filtered[:max_candidates]

    cb = review_callback

    if cb is None:
        async def cb(candidate: Dict[str, any]) -> PathReviewResult:  # type: ignore[no-redef]
            return PathReviewResult(
                status="skipped",
                reasoning="No review callback provided.",
                missing_checks=[],
                confidence=0.0,
                raw_text="",
            )

    evaluated = await _evaluate_candidates(filtered, cb)
    evaluated.sort(key=lambda item: item.score, reverse=True)

    selected = _deduplicate(evaluated, top_k)
    return SelectionSummary(selected=selected, evaluated=evaluated)