"""State, retry-budget, and duplicate-query control for CodeQL composition."""

from __future__ import annotations

import hashlib
import time
from dataclasses import asdict
from typing import Any

from pure_auto_codeql.observability import llm_calls

from .models import (
    CodeQLErrorCategory,
    CompositionState,
    RetryBudget,
    RetryBudgetExceeded,
)


class CompositionController:
    def __init__(self, budget: RetryBudget):
        budget.validate()
        self.budget = budget
        self.state = CompositionState.GENERATE
        self.started_at = time.monotonic()
        self.llm_calls = 0
        self.query_hashes: set[str] = set()
        self.transitions: list[dict[str, Any]] = []
        self.duplicate_queries = 0

    def transition(self, state: CompositionState, **metadata: Any) -> None:
        previous = self.state
        self.state = state
        self.transitions.append(
            {
                "from": previous.value,
                "to": state.value,
                "elapsed_seconds": round(time.monotonic() - self.started_at, 3),
                "metadata": metadata,
            }
        )
        self.check_time()

    def check_time(self) -> None:
        elapsed = time.monotonic() - self.started_at
        if elapsed > self.budget.max_elapsed_seconds:
            raise RetryBudgetExceeded(
                f"CodeQL composition exceeded {self.budget.max_elapsed_seconds} seconds"
            )

    def consume_llm_call(self, purpose: str) -> None:
        self.check_time()
        if self.llm_calls >= self.budget.max_total_llm_calls:
            raise RetryBudgetExceeded(
                f"CodeQL composition exceeded {self.budget.max_total_llm_calls} LLM calls"
            )
        self.llm_calls += 1
        llm_calls.add(1, {"analysis.component": "codeql_composition"})
        self.transitions.append(
            {
                "from": self.state.value,
                "to": self.state.value,
                "elapsed_seconds": round(time.monotonic() - self.started_at, 3),
                "metadata": {"llm_call": self.llm_calls, "purpose": purpose},
            }
        )

    def register_query(self, query: str) -> bool:
        digest = hashlib.sha256(query.strip().encode("utf-8")).hexdigest()
        if digest in self.query_hashes:
            self.duplicate_queries += 1
            return False
        self.query_hashes.add(digest)
        return True

    def mark_failed(self, reason: str) -> None:
        previous = self.state
        self.state = CompositionState.FAILED
        self.transitions.append(
            {
                "from": previous.value,
                "to": CompositionState.FAILED.value,
                "elapsed_seconds": round(time.monotonic() - self.started_at, 3),
                "metadata": {"reason": reason},
            }
        )

    @staticmethod
    def classify_error(output: str) -> CodeQLErrorCategory:
        normalized = (output or "").lower()
        if "timed out" in normalized or "timeout" in normalized or "超时" in normalized:
            return CodeQLErrorCategory.TOOL_TIMEOUT
        if "out of memory" in normalized or "resource exhausted" in normalized:
            return CodeQLErrorCategory.RESOURCE_EXHAUSTED
        if "database" in normalized or "数据库" in normalized:
            return CodeQLErrorCategory.DATABASE
        if "lsp_diagnostics" in normalized or "syntax" in normalized or "语法" in normalized:
            return CodeQLErrorCategory.SYNTAX
        if "no results" in normalized or "结果为空" in normalized:
            return CodeQLErrorCategory.EMPTY_RESULT
        if normalized:
            return CodeQLErrorCategory.EXECUTION
        return CodeQLErrorCategory.UNKNOWN

    def summary(self) -> dict[str, Any]:
        return {
            "state": self.state.value,
            "budget": asdict(self.budget),
            "llm_calls": self.llm_calls,
            "unique_queries": len(self.query_hashes),
            "duplicate_queries": self.duplicate_queries,
            "elapsed_seconds": round(time.monotonic() - self.started_at, 3),
            "transitions": self.transitions,
        }


class BudgetedAnalyzer:
    """Transparent analyzer proxy that enforces the shared LLM-call budget."""

    def __init__(self, analyzer, controller: CompositionController):
        self._analyzer = analyzer
        self._controller = controller

    def __getattr__(self, name: str):
        return getattr(self._analyzer, name)

    async def run_agent(self, prompt: str, *args, **kwargs):
        purpose = kwargs.pop("_budget_purpose", self._controller.state.value)
        self._controller.consume_llm_call(purpose)
        return await self._analyzer.run_agent(prompt, *args, **kwargs)


__all__ = ["BudgetedAnalyzer", "CompositionController"]
