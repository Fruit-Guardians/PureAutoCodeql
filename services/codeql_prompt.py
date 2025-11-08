"""Prompt helper utilities shared by CodeQL generators."""

from __future__ import annotations

from typing import Dict, Optional


def build_placeholder_map(
    *,
    language: str,
    requirement: Optional[str],
    round_index: int,
    prev_original_ql: Optional[str],
    prev_fix_suggestions: Optional[str],
    ql_template: str,
    error_log: Optional[str] = None,
    curr_ql_content: Optional[str] = None,
    kb_directory_index: Optional[str] = None,
    kb_suggestions: Optional[str] = None,
    kb_structured_context: Optional[str] = None,
    kb_reference_snippets: Optional[str] = None,
    relevant_tags: Optional[str] = None,
) -> Dict[str, str]:
    """Return a ready-to-use placeholder dictionary for prompt templates."""
    return {
        "ROUND_INDEX": str(round_index or 1),
        "LANGUAGE": (language or "unknown"),
        "REQUIREMENT": (requirement or ""),
        "PREV_ORIGINAL_QL": (prev_original_ql or ""),
        "PREV_FIX_SUGGESTIONS": (prev_fix_suggestions or ""),
        "QL_TEMPLATE": (ql_template or ""),
        "ERROR_LOG": (error_log or ""),
        "CURR_QL_CONTENT": (curr_ql_content or ""),
        "KB_DIRECTORY_INDEX": (kb_directory_index or ""),
        "KB_SUGGESTED_ITEMS": (kb_suggestions or ""),
        "KB_STRUCTURED_CONTEXT": (kb_structured_context or ""),
        "KB_REFERENCE_SNIPPETS": (kb_reference_snippets or ""),
        "RELEVANT_TAGS": (relevant_tags or ""),
    }


def apply_placeholders(content: str, values: Dict[str, str]) -> str:
    """Render [[KEY]] placeholders inside content."""
    result = content
    for key, val in (values or {}).items():
        result = result.replace(f"[[{key}]]", val or "")
    return result


__all__ = ["build_placeholder_map", "apply_placeholders"]
