"""Parsing helpers for model-based path review outputs."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Dict, List


@dataclass
class PathReviewResult:
    status: str
    reasoning: str
    missing_checks: List[str]
    confidence: float
    raw_text: str


def parse_review_output(raw_text: str) -> Dict[str, Any]:
    """Best-effort extraction of JSON content from an LLM response."""
    if not raw_text:
        return {}

    raw_text = raw_text.strip()
    try:
        return json.loads(raw_text)
    except json.JSONDecodeError:
        pass

    match = re.search(r"\{.*\}", raw_text, re.DOTALL)
    if not match:
        return {}

    snippet = match.group(0)
    try:
        return json.loads(snippet)
    except json.JSONDecodeError:
        return {}


def build_review_result(raw_text: str) -> PathReviewResult:
    """Convert raw model output into a structured result dataclass."""
    parsed = parse_review_output(raw_text)

    status = str(parsed.get("status") or "unknown").lower()
    reasoning = str(parsed.get("reason") or "")

    missing_checks = parsed.get("missing_checks") or []
    if not isinstance(missing_checks, list):
        missing_checks = [str(missing_checks)]
    missing_checks = [str(item) for item in missing_checks]

    confidence_val = parsed.get("confidence")
    try:
        confidence = float(confidence_val)
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(min(confidence, 1.0), 0.0)

    return PathReviewResult(
        status=status,
        reasoning=reasoning,
        missing_checks=missing_checks,
        confidence=confidence,
        raw_text=raw_text,
    )
