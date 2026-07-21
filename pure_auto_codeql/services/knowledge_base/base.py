"""Knowledge base abstractions for language-specific CodeQL context retrieval."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional, Protocol, Tuple, Type


class LanguageKnowledgeBase(Protocol):
    """Protocol describing the minimal interface for a language knowledge base."""

    language: str
    repo_root: Path

    def build_context(self, requirement: str) -> Dict[str, str]:
        """Return placeholder-ready context data for the given natural-language requirement."""


@dataclass(frozen=True)
class _FactoryKey:
    language: str
    repo_root: Path

    def __hash__(self) -> int:  # pragma: no cover - trivial
        return hash((self.language, self.repo_root.resolve()))


class KnowledgeBaseFactory:
    """Registry + cache for language knowledge bases."""

    _registry: Dict[str, Type[LanguageKnowledgeBase]] = {}
    _instances: Dict[_FactoryKey, LanguageKnowledgeBase] = {}

    @classmethod
    def register(cls, language: str, kb_cls: Type[LanguageKnowledgeBase]) -> None:
        """Register a provider class for a language."""
        if not language:
            raise ValueError("language must be non-empty")
        cls._registry[language.lower()] = kb_cls

    @classmethod
    def get(cls, language: Optional[str], repo_root: Path) -> Optional[LanguageKnowledgeBase]:
        """Return (and cache) a knowledge base instance for the given language."""
        if not language:
            return None

        kb_cls = cls._registry.get(language.lower())
        if kb_cls is None:
            return None

        key = _FactoryKey(language.lower(), repo_root)
        kb = cls._instances.get(key)
        if kb is None:
            kb = kb_cls(repo_root)
            cls._instances[key] = kb
        return kb


__all__ = ["LanguageKnowledgeBase", "KnowledgeBaseFactory"]
