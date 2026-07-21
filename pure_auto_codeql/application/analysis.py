"""Shared analysis workflow validation."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from pure_auto_codeql.utils.case import CasePaths, resolve_case


@dataclass(frozen=True)
class AnalysisValidationError(ValueError):
    """Validation failure for an analysis workflow request."""

    message: str
    status_code: int

    def __str__(self) -> str:
        return self.message


def validate_analysis_case(
    case_id: str,
    *,
    projects_dir: Path = Path("projects"),
) -> CasePaths:
    """Validate and resolve an analysis case ID for CLI/API workflows."""

    try:
        return resolve_case(case_id, base_dir=projects_dir)
    except FileNotFoundError as exc:
        raise AnalysisValidationError(
            f"项目 '{case_id}' 不存在: {exc}",
            status_code=404,
        ) from exc
    except ValueError as exc:
        raise AnalysisValidationError(
            f"无效的项目ID: {exc}",
            status_code=400,
        ) from exc
