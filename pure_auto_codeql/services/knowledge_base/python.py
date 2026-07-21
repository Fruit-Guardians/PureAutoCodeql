"""Python-specific knowledge base implementation."""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

from .base import KnowledgeBaseFactory, LanguageKnowledgeBase


class PythonKnowledgeBase(LanguageKnowledgeBase):
    """Structured retrieval for Python CodeQL snippets, helpers, and templates."""

    language = "python"

    CORE_MODULE_IDS = [
        "module:dataflow",
        "module:tainttracking",
        "module:remote-flow-sources",
    ]

    DIRECTORY_DESCRIPTIONS: Dict[str, str] = {
        "README.md": "Overview of the Python CodeQL knowledge base and workflow tips.",
        "CODEQL_PATH_QUERY_GUIDE.md": "Path-problem skeleton and authoring guidance.",
        "templates/path_problem_skeleton.ql": "Baseline skeleton for Python path-problem queries.",
        "knowledge_base/modules.json": "Module imports with summaries, exports, and tags.",
        "knowledge_base/helpers.json": "Reusable helper predicates with signatures and examples.",
        "knowledge_base/templates.json": "Scenario templates referencing helpers/modules.",
        "knowledge_base/cases.json": "Successful CVE queries referencing helper ids.",
        "knowledge_base/errors.json": "Compiler error patterns with causes and fixes.",
        "tools/retrieve.py": "Tag-driven retrieval CLI for combining modules/helpers/templates.",
    }

    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
        self.source_dir = repo_root / "resources" / "codeql" / "python"
        self.mirror_dir = repo_root / "projects" / "python_kb"
        self._sections: Optional[Dict[str, List[Dict[str, Any]]]] = None
        self._tag_variant_map: Optional[Dict[str, Set[str]]] = None
        self._known_tags_cache: Optional[Set[str]] = None

    # --------------------------------------------------------------------- #
    # Core data loading helpers
    # --------------------------------------------------------------------- #

    def ensure_mirror(self) -> bool:
        """Mirror the KB under projects/python_kb for MCP access."""
        if not self.source_dir.is_dir():
            return False

        try:
            self.mirror_dir.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(self.source_dir, self.mirror_dir, dirs_exist_ok=True)
        except Exception:
            # Non-fatal: fall back to source_dir
            pass

        return self.mirror_dir.is_dir()

    def _load_section(self, name: str) -> List[Dict[str, Any]]:
        kb_root = self.source_dir / "knowledge_base"
        path = kb_root / f"{name}.json"
        if not path.is_file():
            return []
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return []

    def sections(self) -> Dict[str, List[Dict[str, Any]]]:
        if self._sections is None:
            self._sections = {
                "modules": self._load_section("modules"),
                "helpers": self._load_section("helpers"),
                "templates": self._load_section("templates"),
                "cases": self._load_section("cases"),
                "errors": self._load_section("errors"),
            }
        return self._sections

    # --------------------------------------------------------------------- #
    # Tag handling
    # --------------------------------------------------------------------- #

    @staticmethod
    def _expand_tag_variants(tag: str) -> Set[str]:
        base = (tag or "").strip().lower()
        if not base:
            return set()

        variants: Set[str] = {base}
        parts = [part for part in re.split(r"[-_/]+", base) if part]
        variants.update(parts)
        condensed = "".join(parts)
        underscore_joined = "_".join(parts)
        for candidate in (condensed, underscore_joined):
            if candidate:
                variants.add(candidate)
        return variants

    def _ensure_tag_indexes(self) -> None:
        if self._tag_variant_map is not None and self._known_tags_cache is not None:
            return

        tag_variants: Dict[str, Set[str]] = {}
        known_tags: Set[str] = set()

        for items in self.sections().values():
            for item in items:
                for raw_tag in item.get("tags", []):
                    canonical = str(raw_tag).strip().lower()
                    if not canonical:
                        continue
                    known_tags.add(canonical)
                    for variant in self._expand_tag_variants(canonical):
                        tag_variants.setdefault(variant, set()).add(canonical)

        self._tag_variant_map = tag_variants
        self._known_tags_cache = known_tags

    @staticmethod
    def _tokenize_requirement(requirement: str) -> Set[str]:
        return {
            token.lower()
            for token in re.findall(r"[a-zA-Z0-9_]+", requirement or "")
            if token
        }

    def derive_tags(self, requirement: str) -> Set[str]:
        tokens = self._tokenize_requirement(requirement)
        if not tokens:
            return set()
        self._ensure_tag_indexes()
        if not self._tag_variant_map:
            return set()

        matched: Set[str] = set()
        for token in tokens:
            matched.update(self._tag_variant_map.get(token.lower(), set()))
        return matched

    # --------------------------------------------------------------------- #
    # Selection + projection helpers
    # --------------------------------------------------------------------- #

    def _select_items(
        self,
        section: str,
        tags: Set[str],
        limit: int = 5,
    ) -> List[Dict[str, Any]]:
        items = self.sections().get(section, [])
        if not items:
            return []

        selected: List[Dict[str, Any]] = []
        tag_lower = {tag.lower() for tag in tags}

        if section == "modules":
            for module_id in self.CORE_MODULE_IDS:
                for item in items:
                    if item.get("id") == module_id and item not in selected:
                        selected.append(item)

        for item in items:
            item_tags = {str(tag).lower() for tag in item.get("tags", [])}
            if tag_lower & item_tags and item not in selected:
                selected.append(item)

        if not selected:
            selected = items[:limit]

        return selected[:limit]

    @staticmethod
    def _project_item(section: str, item: Dict[str, Any]) -> Dict[str, Any]:
        """Reduce noisy fields per section for structured output."""
        if section == "modules":
            return {
                "id": item.get("id"),
                "summary": item.get("summary") or item.get("usage_notes"),
                "exports": item.get("exports"),
                "tags": item.get("tags", []),
            }
        if section == "helpers":
            return {
                "id": item.get("id"),
                "signature": item.get("signature"),
                "description": item.get("description"),
                "example": item.get("example"),
                "tags": item.get("tags", []),
            }
        if section == "templates":
            return {
                "id": item.get("id"),
                "description": item.get("description"),
                "file": item.get("file"),
                "notes": item.get("notes"),
                "tags": item.get("tags", []),
            }
        if section == "cases":
            return {
                "id": item.get("id"),
                "cve": item.get("cve"),
                "summary": item.get("summary"),
                "path": item.get("path"),
                "key_patterns": item.get("key_patterns"),
                "helpers": item.get("helpers"),
                "tags": item.get("tags", []),
            }
        if section == "errors":
            return {
                "id": item.get("id"),
                "pattern": item.get("pattern"),
                "cause": item.get("cause"),
                "fix": item.get("fix"),
            }
        return item

    def _build_structured_context(self, requirement: str) -> Dict[str, Any]:
        tags = sorted(self.derive_tags(requirement))
        structured: Dict[str, Any] = {
            "language": self.language,
            "tags": tags,
            "resources": {},
        }

        for section in ["modules", "helpers", "templates", "cases", "errors"]:
            raw_items = self._select_items(section, set(tags))
            structured["resources"][section] = [
                self._project_item(section, item) for item in raw_items
            ]

        return structured

    def _build_human_summary(self, structured: Dict[str, Any]) -> str:
        """Generate a compact Markdown summary for legacy prompts."""
        tags = structured.get("tags") or []
        resources = structured.get("resources") or {}
        lines: List[str] = []
        if tags:
            lines.append(f"Matched tags: {', '.join(tags)}")
        else:
            lines.append("Matched tags: (none)")

        for section in ["modules", "helpers", "templates", "cases", "errors"]:
            items = resources.get(section) or []
            if not items:
                continue
            lines.append(f"[{section}]")
            for item in items:
                entry = f"- {item.get('id', 'unknown')}: "
                if section == "modules":
                    entry += item.get("summary") or ""
                elif section == "helpers":
                    entry += item.get("description") or ""
                elif section == "templates":
                    desc = item.get("description") or ""
                    file_hint = item.get("file")
                    if file_hint:
                        desc = f"{desc} (file: {file_hint})"
                    entry += desc
                elif section == "cases":
                    desc = item.get("summary") or ""
                    path_hint = item.get("path")
                    if path_hint:
                        desc = f"{desc} (query: {path_hint})"
                    entry += desc
                else:
                    entry += item.get("cause") or ""

                tags_field = item.get("tags") or []
                if tags_field:
                    entry += f" [tags: {', '.join(tags_field)}]"
                lines.append(entry.strip())
        return "\n".join(lines)

    # --------------------------------------------------------------------- #
    # Public API
    # --------------------------------------------------------------------- #

    def build_directory_index(self) -> str:
        """Return mirrored directory listing description."""
        if not (self.mirror_dir.is_dir() or self.ensure_mirror()):
            return ""

        entries = []
        for relative_path, description in self.DIRECTORY_DESCRIPTIONS.items():
            entries.append(f"- `projects/python_kb/{relative_path}` - {description}")
        return "\n".join(entries)

    def build_context(self, requirement: str) -> Dict[str, str]:
        structured = self._build_structured_context(requirement)
        directory_index = self.build_directory_index()
        suggestions = self._build_human_summary(structured)
        json_payload = json.dumps(structured, ensure_ascii=False, indent=2)
        tags = structured.get("tags") or []
        reference_snippets = self._build_reference_snippets(structured)

        return {
            "kb_directory_index": directory_index,
            "kb_suggestions": suggestions,
            "kb_structured_context": json_payload,
            "kb_reference_snippets": reference_snippets,
            "relevant_tags": ", ".join(tags),
        }

    def _build_reference_snippets(self, structured: Dict[str, Any]) -> str:
        """Return CodeQL snippets taken from referenced cases to guide the generator."""
        cases = (structured.get("resources") or {}).get("cases") or []
        snippets: List[str] = []
        max_cases = 2
        max_lines = 160

        for case in cases[:max_cases]:
            path = case.get("path")
            if not path:
                continue
            snippet_path = self.repo_root / path
            if not snippet_path.is_file():
                continue
            try:
                content_lines = snippet_path.read_text(encoding="utf-8").splitlines()
            except Exception:
                continue
            head = "\n".join(content_lines[:max_lines])
            snippet = (
                f"#### Case: {case.get('cve') or case.get('id')}\n"
                f"- Query Path: `{path}`\n"
                "```ql\n"
                f"{head}\n"
                "```\n"
            )
            snippets.append(snippet)

        return "\n".join(snippets)


# Register provider
KnowledgeBaseFactory.register(PythonKnowledgeBase.language, PythonKnowledgeBase)


__all__ = ["PythonKnowledgeBase"]
