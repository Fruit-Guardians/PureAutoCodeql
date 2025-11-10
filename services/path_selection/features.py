"""Dataclasses representing enriched path features used for scoring and reporting."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass(slots=True)
class PathNode:
    """Normalized representation of a node (source, sink, or intermediate) in a CodeQL path."""

    role: str
    file: str
    line: int
    end_line: Optional[int]
    description: str
    code_snippet: str
    analysis: Dict[str, Any] = field(default_factory=dict)
    api_calls: List[str] = field(default_factory=list)

    def location_label(self) -> str:
        """Return a human-readable label for reporting."""
        if not self.file:
            return "unknown"
        if self.end_line and self.end_line != self.line:
            return f"{self.file}:{self.line}-{self.end_line}"
        return f"{self.file}:{self.line}" if self.line else self.file


@dataclass(slots=True)
class PathFeatures:
    """Structured features extracted from a CodeQL data-flow path."""

    index: int
    original_path: Dict[str, Any]
    path_length: int
    thread_flows: List[Dict[str, Any]]
    source: PathNode
    sink: PathNode
    intermediates: List[PathNode] = field(default_factory=list)
    dangerous_apis: List[str] = field(default_factory=list)
    sanitizer_hits: List[str] = field(default_factory=list)
    matched_keywords: List[str] = field(default_factory=list)
    language: str = ""
    metadata: Dict[str, Any] = field(default_factory=dict)

    def has_source(self) -> bool:
        return bool(self.source.file and self.source.line)

    def has_sink(self) -> bool:
        return bool(self.sink.file and self.sink.line)

    @property
    def flow_summary(self) -> str:
        """Return a short textual summary useful for logs and prompts."""
        return (
            f"{self.source.location_label()} -> "
            f"{self.sink.location_label()} "
            f"({self.path_length} steps)"
        )


__all__ = ["PathNode", "PathFeatures"]
