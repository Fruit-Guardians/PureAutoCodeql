"""Single source of truth for per-language analysis capabilities."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class LanguageCapabilities:
    language: str
    source_analysis: bool
    sink_analysis: bool
    path_analysis: bool
    codeql_generation: bool
    breakpoint_recovery: bool
    lsp: bool


LANGUAGE_CAPABILITIES = {
    "java": LanguageCapabilities("java", True, True, True, True, True, True),
    "python": LanguageCapabilities("python", True, True, True, True, True, True),
    "cpp": LanguageCapabilities("cpp", True, True, True, True, True, True),
}


def normalize_language(language: str) -> str:
    normalized = (language or "").strip().lower()
    return {"c": "cpp", "c++": "cpp"}.get(normalized, normalized)


def get_language_capabilities(language: str) -> LanguageCapabilities:
    normalized = normalize_language(language)
    try:
        return LANGUAGE_CAPABILITIES[normalized]
    except KeyError as exc:
        raise ValueError(f"Unsupported analysis language: {language}") from exc


__all__ = [
    "LANGUAGE_CAPABILITIES",
    "LanguageCapabilities",
    "get_language_capabilities",
    "normalize_language",
]
