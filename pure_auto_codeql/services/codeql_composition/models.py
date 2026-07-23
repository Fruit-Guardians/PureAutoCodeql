"""Data models for deterministic CodeQL composition control."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class CompositionState(str, Enum):
    GENERATE = "generate"
    VALIDATE = "validate"
    EXECUTE = "execute"
    ANALYZE_ERROR = "analyze_error"
    REPAIR = "repair"
    REGENERATE = "regenerate"
    BREAKPOINT = "breakpoint"
    FALLBACK = "fallback"
    COMPLETE = "complete"
    FAILED = "failed"


class CodeQLErrorCategory(str, Enum):
    SYNTAX = "syntax"
    DATABASE = "database"
    EXECUTION = "execution"
    EMPTY_RESULT = "empty_result"
    TOOL_TIMEOUT = "tool_timeout"
    RESOURCE_EXHAUSTED = "resource_exhausted"
    DUPLICATE_QUERY = "duplicate_query"
    UNKNOWN = "unknown"


@dataclass(frozen=True)
class RetryBudget:
    generation_attempts: int = 6
    repair_attempts_per_generation: int = 5
    breakpoint_attempts: int = 3
    fallback_attempts: int = 5
    max_total_llm_calls: int = 20
    max_elapsed_seconds: int = 1800

    def validate(self) -> None:
        for name, value in self.__dict__.items():
            if value < 1:
                raise ValueError(f"{name} must be at least 1")


class RetryBudgetExceeded(RuntimeError):
    pass


__all__ = [
    "CodeQLErrorCategory",
    "CompositionState",
    "RetryBudget",
    "RetryBudgetExceeded",
]
