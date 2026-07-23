"""Append-only terminal presentation helpers for CLI analysis runs."""

from __future__ import annotations

import os
import sys
from pathlib import Path
from typing import Any

from pure_auto_codeql.analysis_models import StepStatus

STEP_LABELS = {
    "cve_analysis": "CVE Intelligence",
    "sink_analysis": "Sink Analysis",
    "source_analysis": "Source Analysis",
    "path_analysis": "Path Verification",
    "codeql_generation": "CodeQL Generation",
    "path_selection": "Path Selection",
}

STATUS_ICONS = {
    StepStatus.SUCCEEDED: "󰄬",
    StepStatus.SKIPPED: "󰒭",
    StepStatus.FAILED: "󰅙",
    StepStatus.CANCELLED: "󰜺",
    StepStatus.TIMED_OUT: "󰥔",
}


def display_path(value: str | Path) -> str:
    """Prefer a repository-relative path in terminal output."""
    path = Path(value)
    try:
        return str(path.resolve().relative_to(Path.cwd().resolve()))
    except (OSError, ValueError):
        return str(path)


def print_stage_start(index: int, total: int, name: str) -> None:
    label = STEP_LABELS.get(name, name.replace("_", " ").title())
    prefix = f"{index:02d}/{total:02d} · {label}"
    print(f"\n── {prefix} {'─' * max(8, 56 - len(prefix))}")


def print_stage_end(name: str, status: StepStatus, elapsed_seconds: float) -> None:
    label = STEP_LABELS.get(name, name.replace("_", " ").title())
    icon = STATUS_ICONS.get(status, "󰋼")
    print(f"{icon}  {label:<22} {status.value:<10} {elapsed_seconds:>6.1f}s")


class StreamBlock:
    """Prefix arbitrary streamed chunks with a quiet reasoning guide."""

    def __init__(self) -> None:
        self._line_start = True

    def start(self, agent_name: str | None = None) -> None:
        label = agent_name or "Agent reasoning"
        print(f"\n  {label}")
        print("  " + "┄" * min(48, max(20, len(label) + 10)))

    def write(self, text: str) -> None:
        for part in text.splitlines(keepends=True):
            if self._line_start:
                sys.stdout.write("  │ ")
            sys.stdout.write(part)
            self._line_start = part.endswith(("\n", "\r"))
        sys.stdout.flush()

    def finish(self) -> None:
        if not self._line_start:
            print()
        print("  └─ reasoning complete")
        self._line_start = True


def summarize_tool_input(tool_input: Any, limit: int = 72) -> str:
    if isinstance(tool_input, dict):
        preferred = ("query", "pattern", "path", "file_path", "symbol", "url")
        values = [
            f"{key}={tool_input[key]!s}"
            for key in preferred
            if tool_input.get(key) not in (None, "")
        ]
        summary = " · ".join(values) or ", ".join(sorted(tool_input))
    else:
        summary = str(tool_input)
    summary = " ".join(summary.split())
    return summary if len(summary) <= limit else f"{summary[: limit - 1]}…"


def print_tool_start(tool_name: str, tool_input: Any) -> None:
    summary = summarize_tool_input(tool_input)
    suffix = f"  {summary}" if summary else ""
    print(f"  󰢛  {tool_name}{suffix}", flush=True)


def print_tool_end(tool_name: str, status: str, elapsed_seconds: float) -> None:
    icon = "󰅙" if status == "error" else "󰀪" if status == "empty" else "󰄬"
    print(f"  {icon}  {tool_name:<24} {elapsed_seconds:>6.2f}s")


def verbose_tool_output_enabled() -> bool:
    return os.getenv("PURE_AUTO_CODEQL_VERBOSE_TOOLS", "").lower() in {
        "1",
        "true",
        "yes",
    }


def print_result_card(result: Any) -> None:
    outcome = getattr(getattr(result, "outcome", None), "value", "unknown")
    steps = getattr(result, "step_results", {})
    completed = sum(
        step.status == StepStatus.SUCCEEDED for step in steps.values()
    )
    total = len(steps)
    duration = getattr(result, "execution_time", None) or 0
    output = getattr(result, "output_directory", None)

    rows = [
        ("Outcome", outcome),
        ("Case", getattr(result, "case_id", "unknown")),
        ("Language", getattr(result, "language", "unknown")),
        ("Steps", f"{completed}/{total} succeeded"),
        ("Duration", f"{duration:.1f}s"),
    ]
    if output:
        rows.append(("Artifacts", display_path(output)))

    width = max(len(f"{label}  {value}") for label, value in rows) + 4
    width = max(48, min(width, 100))
    print("\n┌─ Analysis complete " + "─" * max(1, width - 21) + "┐")
    for label, value in rows:
        content = f"{label:<10} {value}"
        clipped = content if len(content) <= width - 4 else f"{content[: width - 5]}…"
        print(f"│  {clipped:<{width - 4}}  │")
    print("└" + "─" * width + "┘")
