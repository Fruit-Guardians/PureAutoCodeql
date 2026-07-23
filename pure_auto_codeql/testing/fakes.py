"""Deterministic offline replacements for all external analysis dependencies."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from pure_auto_codeql.analysis_models import StepResult


@dataclass
class FakeLLM:
    responses: list[str] = field(default_factory=lambda: ["{}"])
    calls: list[str] = field(default_factory=list)

    async def run_agent(self, prompt: str, *args, **kwargs) -> StepResult:
        del args, kwargs
        self.calls.append(prompt)
        response = self.responses[min(len(self.calls) - 1, len(self.responses) - 1)]
        return StepResult(content=response, success=True, metrics={"provider": "fake", "tokens": 0})

    async def initialize(self, **kwargs) -> None:
        del kwargs

    async def aclose(self) -> None:
        return None


@dataclass
class FakeCodeQL:
    findings: list[dict[str, Any]] = field(default_factory=list)
    executed_queries: list[str] = field(default_factory=list)

    def validate(self, query: str) -> dict[str, Any]:
        valid = query.count("{") == query.count("}") and "select" in query
        return {"success": valid, "diagnostics": [] if valid else ["invalid fake QL"]}

    def execute(self, query: str, output: Path) -> dict[str, Any]:
        self.executed_queries.append(query)
        sarif = {
            "version": "2.1.0",
            "runs": [{"tool": {"driver": {"name": "Fake CodeQL"}}, "results": self.findings}],
        }
        output.write_text(json.dumps(sarif), encoding="utf-8")
        return {
            "success": True,
            "sarif_path": str(output),
            "findings_count": len(self.findings),
        }


@dataclass
class FakeLSP:
    definitions: dict[str, list[dict[str, Any]]] = field(default_factory=dict)
    stopped: bool = False

    def start(self) -> bool:
        return True

    def definition(self, symbol: str) -> list[dict[str, Any]]:
        return self.definitions.get(symbol, [])

    def stop(self) -> None:
        self.stopped = True


@dataclass
class FakeMCP:
    responses: dict[str, Any] = field(default_factory=dict)
    closed: bool = False

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> Any:
        del arguments
        return self.responses.get(name, {})

    async def aclose(self) -> None:
        self.closed = True


@dataclass
class FakeIntelClient:
    records: dict[str, dict[str, Any]] = field(default_factory=dict)
    available: bool = True

    async def fetch(self, identifier: str) -> dict[str, Any]:
        if not self.available:
            raise ConnectionError("fake intelligence source unavailable")
        return self.records.get(identifier, {})
