"""Helpers for converting scored paths into dictionary payloads."""

from __future__ import annotations

from typing import Any, Dict, List

from .features import PathNode
from .path_ranker import PathScore


def score_to_payload(score: PathScore, language: str) -> Dict[str, Any]:
    """Convert a PathScore into the legacy dict consumed by LLM/verifier stages."""
    feature = score.feature
    payload = {
        "index": feature.index,
        "original_path": feature.original_path,
        "path_length": feature.path_length,
        "threadFlows": feature.thread_flows,
        "source_location": node_to_location(feature.source),
        "source_code": feature.source.code_snippet,
        "source_analysis": {
            **(feature.source.analysis or {}),
            "location": node_to_location(feature.source),
        },
        "sink_location": node_to_location(feature.sink),
        "sink_code": feature.sink.code_snippet,
        "sink_analysis": {
            **(feature.sink.analysis or {}),
            "location": node_to_location(feature.sink),
        },
        "intermediate_steps": max(0, feature.path_length - 2),
        "intermediate_nodes": [
            {
                "role": node.role,
                "location": node_to_location(node),
                "code": node.code_snippet,
            }
            for node in feature.intermediates
        ],
        "flow_summary": feature.flow_summary,
        "language": language,
        "dangerous_apis": feature.dangerous_apis,
        "sanitizer_hits": feature.sanitizer_hits,
        "matched_keywords": feature.matched_keywords,
        "selection_info": {
            "deterministic_score": round(score.total, 4),
            "deterministic_metrics": score.metrics,
            "deterministic_reasons": score.reasons,
        },
    }
    return payload


def node_to_location(node: PathNode) -> Dict[str, Any]:
    return {
        "file": node.file,
        "startLine": node.line,
        "endLine": node.end_line,
        "description": node.description,
    }


__all__ = ["score_to_payload", "node_to_location"]
