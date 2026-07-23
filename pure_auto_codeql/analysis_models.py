"""Shared, structured models for analysis steps and run outcomes."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional


class StepStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    SKIPPED = "skipped"
    FAILED = "failed"
    CANCELLED = "cancelled"
    TIMED_OUT = "timed_out"


class AnalysisOutcome(str, Enum):
    RUNNING = "running"
    COMPLETED_WITH_FINDINGS = "completed_with_findings"
    COMPLETED_NO_FINDINGS = "completed_no_findings"
    PARTIAL = "partial"
    FAILED = "failed"
    CANCELLED = "cancelled"
    TIMED_OUT = "timed_out"


@dataclass(frozen=True)
class ErrorDetail:
    code: str
    message: str
    category: str = "analysis"
    retryable: bool = False
    details: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "code": self.code,
            "message": self.message,
            "category": self.category,
            "retryable": self.retryable,
            "details": self.details,
        }


@dataclass(frozen=True)
class Artifact:
    name: str
    path: str
    media_type: str = "application/octet-stream"
    sha256: Optional[str] = None
    size: Optional[int] = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "path": self.path,
            "media_type": self.media_type,
            "sha256": self.sha256,
            "size": self.size,
        }


@dataclass(init=False)
class StepResult:
    """Canonical step result with a compatibility constructor for AgentResult."""

    content: Any
    status: StepStatus
    error_detail: Optional[ErrorDetail]
    warnings: list[str]
    artifacts: list[Artifact]
    metrics: dict[str, Any]

    def __init__(
        self,
        content: Any = "",
        success: Optional[bool] = None,
        error: Optional[str] = None,
        *,
        status: Optional[StepStatus] = None,
        error_detail: Optional[ErrorDetail] = None,
        warnings: Optional[list[str]] = None,
        artifacts: Optional[list[Artifact]] = None,
        metrics: Optional[dict[str, Any]] = None,
    ) -> None:
        if status is None:
            status = StepStatus.SUCCEEDED if success is not False else StepStatus.FAILED
        if error_detail is None and error:
            error_detail = ErrorDetail(code="step_failed", message=error)
        self.content = content
        self.status = status
        self.error_detail = error_detail
        self.warnings = list(warnings or [])
        self.artifacts = list(artifacts or [])
        self.metrics = dict(metrics or {})

    @property
    def success(self) -> bool:
        return self.status in {StepStatus.SUCCEEDED, StepStatus.SKIPPED}

    @property
    def error(self) -> Optional[str]:
        return self.error_detail.message if self.error_detail else None

    @classmethod
    def skipped(cls, reason: str) -> "StepResult":
        return cls(
            content="",
            status=StepStatus.SKIPPED,
            warnings=[reason],
            metrics={"skip_reason": reason},
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "status": self.status.value,
            "content": self.content,
            "error": self.error_detail.to_dict() if self.error_detail else None,
            "warnings": self.warnings,
            "artifacts": [artifact.to_dict() for artifact in self.artifacts],
            "metrics": self.metrics,
        }


# Transitional public name. Existing extensions can keep importing AgentResult.
AgentResult = StepResult


__all__ = [
    "AgentResult",
    "AnalysisOutcome",
    "Artifact",
    "ErrorDetail",
    "StepResult",
    "StepStatus",
]
