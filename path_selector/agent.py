"""Agent-style wrapper that orchestrates path selection with LLM support."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Tuple, Union

try:
    from Analyze import MultiAgentAnalyzer  # type: ignore
except Exception:  # pragma: no cover - type checking fallback
    MultiAgentAnalyzer = None  # type: ignore

from path_selector.prompts import build_path_review_prompt
from path_selector.review import PathReviewResult, build_review_result
from path_selector.selector import (
    SelectionSummary,
    select_paths,
)


class PathSelectionAgent:
    """High-level API for running two-stage path selection with LLM review."""

    def __init__(
        self,
        analyzer,
        *,
        repo_root: Optional[Union[str, Path]] = None,
        source_root: Optional[Union[str, Path]] = None,
        context_radius: int = 5,
    ) -> None:
        if MultiAgentAnalyzer is not None and not isinstance(analyzer, MultiAgentAnalyzer):
            raise TypeError("analyzer must be an instance of Analyze.MultiAgentAnalyzer")

        self.analyzer = analyzer
        self.repo_root = Path(repo_root) if repo_root else Path.cwd()
        self.source_root = Path(source_root) if source_root else self.repo_root
        self.context_radius = max(1, int(context_radius))
        self._file_cache: Dict[str, Optional[str]] = {}

    async def select_from_file(
        self,
        sarif_path: Union[str, Path],
        cve_summary: str,
        **options,
    ) -> SelectionSummary:
        sarif_path = Path(sarif_path)
        sarif = json.loads(sarif_path.read_text(encoding="utf-8"))
        options.setdefault("make_relative_to", str(self.source_root))
        summary = await self.select_from_data(sarif, cve_summary, **options)
        return summary

    async def select_from_data(
        self,
        sarif: Dict[str, any],
        cve_summary: str,
        *,
        top_k: int = 3,
        max_candidates: Optional[int] = None,
        threadflow_index: int = -1,
        rule_filter: Optional[str] = None,
        make_relative_to: Optional[str] = None,
        allowed_sink_files: Optional[Iterable[str]] = None,
        require_same_file: bool = False,
        use_llm: bool = True,
        show_thinking: bool = False,
    ) -> SelectionSummary:
        """Run the full selection pipeline and return structured summary."""

        self._file_cache.clear()

        async def review_callback(candidate: Dict[str, any]) -> PathReviewResult:
            if not use_llm:
                return PathReviewResult(
                    status="skipped",
                    reasoning="LLM review disabled.",
                    missing_checks=[],
                    confidence=0.0,
                    raw_text="",
                )

            contexts = await self._build_contexts(candidate)
            prompt = build_path_review_prompt(
                cve_summary=cve_summary,
                candidate=candidate,
                contexts=contexts,
            )
            result = await self.analyzer.run_agent(prompt, show_thinking=show_thinking)
            if not result.success:
                return PathReviewResult(
                    status="error",
                    reasoning=result.error or "LLM agent failed",
                    missing_checks=[],
                    confidence=0.0,
                    raw_text=result.content or "",
                )

            return build_review_result(result.content)

        summary = await select_paths(
            sarif,
            cve_summary,
            top_k=top_k,
            max_candidates=max_candidates,
            threadflow_index=threadflow_index,
            rule_filter=rule_filter,
            make_relative_to=make_relative_to,
            allowed_sink_files=allowed_sink_files,
            require_same_file=require_same_file,
            review_callback=review_callback,
        )
        return summary

    async def _build_contexts(self, candidate: Dict[str, any]) -> List[Dict[str, any]]:
        contexts: List[Dict[str, any]] = []
        for step in candidate.get("steps", []):
            location = step.get("location", {}) or {}
            file_rel = location.get("file")
            start_line = int(location.get("startLine") or 0)
            if not file_rel or start_line <= 0:
                continue
            snippet, line_span = await self._read_snippet(file_rel, start_line)
            if not snippet:
                continue
            contexts.append(
                {
                    "file": file_rel,
                    "line_span": line_span,
                    "snippet": snippet,
                }
            )
        return contexts

    async def _read_snippet(self, relative_path: str, center_line: int) -> Tuple[str, str]:
        text = await self._get_file_text(relative_path)
        if not text:
            return "", ""

        lines = text.splitlines()
        if not lines:
            return "", ""

        center_idx = max(center_line - 1, 0)
        radius = self.context_radius
        start_idx = max(center_idx - radius, 0)
        end_idx = min(center_idx + radius, len(lines) - 1)

        snippet_lines = []
        for idx in range(start_idx, end_idx + 1):
            snippet_lines.append(f"{idx + 1:04d}: {lines[idx]}")
        line_span = f"{start_idx + 1}-{end_idx + 1}"
        return "\n".join(snippet_lines), line_span

    async def _get_file_text(self, relative_path: str) -> Optional[str]:
        key = relative_path.replace("\\", "/")
        if key in self._file_cache:
            return self._file_cache[key]

        text = await self._read_file_via_mcp(relative_path)
        if text is None:
            text = self._read_file_local(relative_path)

        self._file_cache[key] = text
        return text

    async def _read_file_via_mcp(self, relative_path: str) -> Optional[str]:
        client = getattr(self.analyzer, "mcp_client", None)
        call_tool = getattr(client, "call_tool", None)
        if client is None or not callable(call_tool):
            return None

        candidate_paths = [
            relative_path,
            str((Path.cwd() / "projects" / relative_path).resolve()),
            str((self.repo_root / relative_path).resolve()),
        ]

        for target in candidate_paths:
            try:
                response = await call_tool("filesystem", "read_file", {"path": target, "encoding": "utf-8"})
                text = _extract_text(response)
                if text:
                    return text
            except Exception:
                continue
        return None

    def _read_file_local(self, relative_path: str) -> Optional[str]:
        candidate = Path(relative_path)
        if not candidate.is_absolute():
            candidate = (self.source_root / relative_path).resolve()
        if not candidate.exists():
            return None
        try:
            return candidate.read_text(encoding="utf-8")
        except Exception:
            return candidate.read_text(encoding="utf-8", errors="ignore")


def _extract_text(response: object) -> Optional[str]:
    if response is None:
        return None

    if isinstance(response, str):
        return response

    if isinstance(response, dict):
        for key in ("content", "data", "result", "text"):
            if key not in response:
                continue
            value = response[key]
            if isinstance(value, str):
                return value
            if isinstance(value, list):
                parts = []
                for item in value:
                    if isinstance(item, dict) and "text" in item:
                        parts.append(str(item["text"]))
                    else:
                        parts.append(str(item))
                if parts:
                    return "".join(parts)

    content = getattr(response, "content", None)
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict) and "text" in item:
                parts.append(str(item["text"]))
            elif isinstance(item, str):
                parts.append(item)
        if parts:
            return "".join(parts)
    elif isinstance(content, str):
        return content

    return None
