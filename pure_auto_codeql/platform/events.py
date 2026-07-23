"""Canonical event schema shared by the API, worker, Redis and PostgreSQL."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


class RunEvent(BaseModel):
    event_id: str | None = None
    run_id: str
    step: str = "analysis"
    type: str
    severity: Literal["debug", "info", "warning", "error"] = "info"
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))
    message: str
    data: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def from_legacy(cls, run_id: str, event: dict[str, Any]) -> "RunEvent":
        event_type = str(event.get("type", "progress"))
        severity = event.get("severity")
        if severity not in {"debug", "info", "warning", "error"}:
            severity = "error" if event_type == "error" else "info"
        return cls(
            event_id=event.get("event_id"),
            run_id=run_id,
            step=event.get("step") or event.get("step_name") or event.get("agent_type") or "analysis",
            type=event_type,
            severity=severity,
            timestamp=event.get("timestamp") or datetime.now(UTC),
            message=str(event.get("message", "")),
            data=dict(event.get("data") or {}),
        )
