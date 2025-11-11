"""Feature extraction utilities for CodeQL data-flow paths."""

from __future__ import annotations

import asyncio
import logging
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Set

from .features import PathFeatures, PathNode
from .language_adapters import get_language_adapter, LanguageAdapter

logger = logging.getLogger(__name__)

# Fallback sanitizer keywords per language – can be extended via knowledge base later.
SANITIZER_KEYWORDS: Dict[str, Sequence[str]] = {
    "python": ("sanitize", "escape", "clean", "safe_load", "mark_safe"),
    "java": ("sanitize", "escape", "clean", "StringEscapeUtils", "ESAPI"),
    "c": ("strncpy", "snprintf", "memset", "sanitize"),
}


class PathFeatureExtractor:
    """Build `PathFeatures` objects used by scoring, LLM prompts, and verification."""

    def __init__(
        self,
        language: str,
        knowledge_base: Optional[Any] = None,
        context_lines: int = 5,
    ) -> None:
        self.language = (language or "").lower()
        self.context_lines = max(1, context_lines)
        self.adapter: LanguageAdapter = get_language_adapter(self.language)
        self.knowledge_base = knowledge_base
        self._dangerous_api_set: Set[str] = set(self.adapter.get_dangerous_apis() or [])
        self._sanitizers: Sequence[str] = SANITIZER_KEYWORDS.get(self.language, ())

    async def build_features(
        self,
        paths: List[Dict[str, Any]],
        source_root: str | Path,
        cve_context: Optional[Dict[str, Any]] = None,
    ) -> List[PathFeatures]:
        """Extract structured features for the provided CodeQL paths."""
        source_root_path = Path(source_root)
        keywords = self._collect_keywords(cve_context or {})
        features: List[PathFeatures] = []

        for idx, path in enumerate(paths):
            try:
                feat = await self._build_single_feature(
                    idx, path, source_root_path, keywords, cve_context or {}
                )
                features.append(feat)
            except Exception:
                logger.exception("Failed to build features for path %s", idx)
        return features

    async def _build_single_feature(
        self,
        index: int,
        path: Dict[str, Any],
        source_root: Path,
        keywords: Set[str],
        cve_context: Dict[str, Any],
    ) -> PathFeatures:
        thread_flows = path.get("threadFlows") or []
        steps = self._extract_steps(thread_flows)

        if not steps:
            return self._fallback_feature(index, path)

        source_step = steps[0]
        sink_step = steps[-1]

        source_node = await self._build_node(
            role="source",
            step=source_step,
            source_root=source_root,
            keywords=keywords,
        )
        sink_node = await self._build_node(
            role="sink",
            step=sink_step,
            source_root=source_root,
            keywords=keywords,
        )

        intermediate_nodes = await self._build_intermediate_nodes(
            steps[1:-1], source_root, keywords
        )

        dangerous_hits = self._collect_dangerous_apis(
            [source_node, sink_node, *intermediate_nodes]
        )
        sanitizer_hits = self._collect_sanitizers(
            [source_node, sink_node, *intermediate_nodes]
        )
        matched_keywords = sorted(
            {
                kw
                for node in (source_node, sink_node, *intermediate_nodes)
                for kw in self._match_keywords(
                    f"{node.description}\n{node.code_snippet}", keywords
                )
            }
        )

        metadata = self._build_metadata(
            steps=steps,
            source_node=source_node,
            sink_node=sink_node,
            cve_context=cve_context,
            keywords=matched_keywords,
        )

        return PathFeatures(
            index=index,
            original_path=path,
            path_length=len(steps),
            thread_flows=thread_flows,
            source=source_node,
            sink=sink_node,
            intermediates=intermediate_nodes,
            dangerous_apis=dangerous_hits,
            sanitizer_hits=sanitizer_hits,
            matched_keywords=matched_keywords,
            language=self.language,
            metadata=metadata,
        )

    async def _build_node(
        self,
        role: str,
        step: Dict[str, Any],
        source_root: Path,
        keywords: Set[str],
    ) -> PathNode:
        location = step.get("location") or {}
        file_path = location.get("file", "")
        start_line = int(location.get("startLine") or location.get("start_column") or 0)
        end_line = int(location.get("endLine") or start_line)

        code_snippet = await self._read_code_snippet(source_root, file_path, start_line)
        analysis = self._analyze_node(role, location, code_snippet)
        api_calls = self._extract_api_calls(code_snippet)

        return PathNode(
            role=role,
            file=file_path,
            line=start_line,
            end_line=end_line if end_line >= start_line else start_line,
            description=location.get("description") or step.get("message", "") or "",
            code_snippet=code_snippet,
            analysis=analysis,
            api_calls=api_calls,
        )

    async def _build_intermediate_nodes(
        self,
        steps: Sequence[Dict[str, Any]],
        source_root: Path,
        keywords: Set[str],
        limit: int = 6,
    ) -> List[PathNode]:
        nodes: List[PathNode] = []
        for idx, step in enumerate(steps):
            if limit and len(nodes) >= limit:
                break
            node = await self._build_node(
                role=f"intermediate_{idx+1}",
                step=step,
                source_root=source_root,
                keywords=keywords,
            )
            nodes.append(node)
        return nodes

    def _extract_steps(self, thread_flows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        if not thread_flows:
            return []
        steps = []
        for flow in thread_flows:
            steps.extend(flow.get("steps") or [])
        return steps

    def _collect_dangerous_apis(self, nodes: Iterable[PathNode]) -> List[str]:
        hits: Set[str] = set()
        for node in nodes:
            for api in node.api_calls:
                lowered = api.lower()
                if any(lowered == d.lower() or lowered.endswith(d.lower()) for d in self._dangerous_api_set):
                    hits.add(api)
        return sorted(hits)

    def _collect_sanitizers(self, nodes: Iterable[PathNode]) -> List[str]:
        if not self._sanitizers:
            return []
        hits: Set[str] = set()
        for node in nodes:
            text = f"{node.description}\n{node.code_snippet}".lower()
            for sanitizer in self._sanitizers:
                if sanitizer.lower() in text:
                    hits.add(sanitizer)
        return sorted(hits)

    async def _read_code_snippet(
        self,
        source_root: Path,
        relative_file: str,
        line: int,
    ) -> str:
        if not relative_file or not line:
            return ""

        return await asyncio.to_thread(
            self._sync_read_code_snippet,
            source_root,
            relative_file,
            line,
        )

    def _sync_read_code_snippet(
        self,
        source_root: Path,
        relative_file: str,
        line: int,
    ) -> str:
        full_path = (source_root / relative_file).resolve()
        if not full_path.exists():
            logger.debug("Source file not found for snippet: %s", full_path)
            return ""
        try:
            with open(full_path, "r", encoding="utf-8", errors="ignore") as handle:
                lines = handle.readlines()
        except OSError as exc:
            logger.debug("Failed reading %s: %s", full_path, exc)
            return ""

        start_idx = max(0, line - self.context_lines - 1)
        end_idx = min(len(lines), line + self.context_lines)

        snippet_parts: List[str] = []
        for idx in range(start_idx, end_idx):
            line_no = idx + 1
            prefix = ">>> " if line_no == line else "    "
            snippet_parts.append(f"{prefix}{line_no:4d} | {lines[idx].rstrip()}")
        return "\n".join(snippet_parts)

    def _analyze_node(
        self,
        role: str,
        location: Dict[str, Any],
        code_snippet: str,
    ) -> Dict[str, Any]:
        try:
            if role.startswith("source"):
                return self.adapter.analyze_source_point(location, code_snippet) or {}
            if role.startswith("sink"):
                return self.adapter.analyze_sink_point(location, code_snippet) or {}
            return {}
        except Exception:
            logger.debug("Language adapter analysis failed for role %s", role, exc_info=True)
            return {}

    def _extract_api_calls(self, code_snippet: str) -> List[str]:
        if not code_snippet:
            return []
        try:
            return list({api for api in self.adapter.extract_api_calls(code_snippet)})
        except Exception:
            logger.debug("extract_api_calls failed", exc_info=True)
            return []

    def _collect_keywords(self, cve_context: Dict[str, Any]) -> Set[str]:
        keywords: Set[str] = set()
        for key in ("vulnerability_type", "expected_sink", "expected_source", "technical_details"):
            value = cve_context.get(key)
            if not value or not isinstance(value, str):
                continue
            for token in re.findall(r"[A-Za-z0-9_./-]{3,}", value):
                keywords.add(token.lower())
        return keywords

    def _match_keywords(self, text: str, keywords: Set[str]) -> Set[str]:
        if not text or not keywords:
            return set()
        lowered = text.lower()
        return {kw for kw in keywords if kw in lowered}

    def _build_metadata(
        self,
        steps: List[Dict[str, Any]],
        source_node: PathNode,
        sink_node: PathNode,
        cve_context: Dict[str, Any],
        keywords: List[str],
    ) -> Dict[str, Any]:
        return {
            "step_count": len(steps),
            "intermediate_count": max(0, len(steps) - 2),
            "cross_file_flow": bool(
                source_node.file and sink_node.file and source_node.file != sink_node.file
            ),
            "cve_id": cve_context.get("cve_id"),
            "matched_keywords": keywords,
        }

    def _fallback_feature(self, index: int, path: Dict[str, Any]) -> PathFeatures:
        empty_node = PathNode(
            role="unknown",
            file="",
            line=0,
            end_line=None,
            description="",
            code_snippet="",
            analysis={},
            api_calls=[],
        )
        return PathFeatures(
            index=index,
            original_path=path,
            path_length=0,
            thread_flows=path.get("threadFlows") or [],
            source=empty_node,
            sink=empty_node,
            language=self.language,
        )


__all__ = ["PathFeatureExtractor"]
