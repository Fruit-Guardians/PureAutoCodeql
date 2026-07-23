"""Validated schemas for security-analysis evidence produced by agents."""

from __future__ import annotations

from enum import Enum
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class VerificationStatus(str, Enum):
    VERIFIED = "verified"
    UNVERIFIED = "unverified"
    REJECTED = "rejected"


class EvidenceLocation(BaseModel):
    model_config = ConfigDict(extra="allow")

    file_path: str
    line_number: int | None = Field(default=None, ge=1)
    symbol: str
    evidence: str = ""
    confidence: Literal["high", "medium", "low"] = "low"
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED

    @field_validator("file_path", "symbol")
    @classmethod
    def non_empty(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be empty")
        return value.strip()

    @model_validator(mode="after")
    def evidence_controls_verification(self) -> "EvidenceLocation":
        if self.line_number is None or not self.evidence.strip():
            self.verification_status = VerificationStatus.UNVERIFIED
        return self

    def verify_against(self, source_root: Path) -> "EvidenceLocation":
        candidate = (source_root / self.file_path).resolve()
        root = source_root.resolve()
        if candidate != root and root not in candidate.parents:
            self.verification_status = VerificationStatus.REJECTED
        elif not candidate.is_file() or self.line_number is None or not self.evidence.strip():
            self.verification_status = VerificationStatus.UNVERIFIED
        else:
            self.verification_status = VerificationStatus.VERIFIED
        return self


class FlowFunction(BaseModel):
    model_config = ConfigDict(extra="allow")

    name: str
    file_path: str
    line_number: int | None = Field(default=None, ge=1)
    signature: str = ""
    description: str = ""


class SourceSinkPath(BaseModel):
    model_config = ConfigDict(extra="allow")

    source_function: FlowFunction
    sink_function: FlowFunction
    call_chain: list[dict[str, Any]] = Field(default_factory=list)
    transformations: list[dict[str, Any]] = Field(default_factory=list)


class SourceAnalysisOutput(BaseModel):
    model_config = ConfigDict(extra="allow")

    cve: str = ""
    sink_info: str = ""
    candidates: list[EvidenceLocation] = Field(default_factory=list)
    source_to_sink_paths: list[SourceSinkPath] = Field(default_factory=list)


def normalize_source_candidate(candidate: dict[str, Any], source_root: Path) -> EvidenceLocation:
    normalized = {
        **candidate,
        "symbol": candidate.get("symbol") or candidate.get("function_name") or candidate.get("name") or "unknown",
        "line_number": candidate.get("line_number") or candidate.get("line"),
        "evidence": candidate.get("evidence") or candidate.get("reason") or "",
        "verification_status": candidate.get("verification_status", "unverified"),
    }
    return EvidenceLocation.model_validate(normalized).verify_against(source_root)


__all__ = [
    "EvidenceLocation",
    "FlowFunction",
    "SourceAnalysisOutput",
    "SourceSinkPath",
    "VerificationStatus",
    "normalize_source_candidate",
]
