"""Utilities for assembling compact code snippets (“点读”) for LLM prompts."""

from __future__ import annotations

from typing import Dict, List

from .features import PathFeatures, PathNode


class CodeContextBuilder:
    """Build ordered code context blocks (source → intermediates → sink) for prompts."""

    def __init__(
        self,
        max_intermediate_blocks: int = 3,
        max_snippet_length: int = 1200,
    ) -> None:
        self.max_intermediate_blocks = max_intermediate_blocks
        self.max_snippet_length = max_snippet_length

    def build_blocks(self, features: PathFeatures) -> List[Dict[str, str]]:
        """Return a list of context blocks ready for inclusion in prompts."""
        blocks: List[Dict[str, str]] = []

        blocks.append(self._block_from_node("source", features.source))

        for node in features.intermediates[: self.max_intermediate_blocks]:
            blocks.append(self._block_from_node(node.role, node))

        blocks.append(self._block_from_node("sink", features.sink))
        return blocks

    def _block_from_node(self, role: str, node: PathNode) -> Dict[str, str]:
        snippet = self._truncate_snippet(node.code_snippet or "")
        return {
            "role": role,
            "file": node.file,
            "location": node.location_label(),
            "description": node.description,
            "snippet": snippet,
        }

    def _truncate_snippet(self, snippet: str) -> str:
        if not snippet or len(snippet) <= self.max_snippet_length:
            return snippet
        truncated = snippet[: self.max_snippet_length]
        return f"{truncated}\n...(truncated)..."


__all__ = ["CodeContextBuilder"]
