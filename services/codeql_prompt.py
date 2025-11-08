"""CodeQL 生成提示上下文服务。

负责处理 Python 知识库镜像、标签匹配，以及提示模板所需的占位符数据。
"""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path
from typing import Any, Dict, List, Optional, Set


class PythonKnowledgeBase:
    """管理 Python CodeQL 知识库的镜像和检索。"""

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

    def ensure_mirror(self) -> bool:
        """确保知识库在 projects 目录下可用，以便通过 MCP 文件系统访问。"""
        if not self.source_dir.is_dir():
            return False

        try:
            self.mirror_dir.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(self.source_dir, self.mirror_dir, dirs_exist_ok=True)
        except Exception:
            # 即使镜像失败也允许继续使用 source 目录
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

    def build_directory_index(self) -> str:
        """生成 projects 目录下常用文件的索引列表。"""
        if not (self.mirror_dir.is_dir() or self.ensure_mirror()):
            return ""

        entries = []
        for relative_path, description in self.DIRECTORY_DESCRIPTIONS.items():
            entries.append(f"- `projects/python_kb/{relative_path}` - {description}")
        return "\n".join(entries)

    def _collect_known_tags(self) -> Set[str]:
        self._ensure_tag_indexes()
        return set(self._known_tags_cache or ())

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
                        if variant not in tag_variants:
                            tag_variants[variant] = set()
                        tag_variants[variant].add(canonical)

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

    def build_suggestions(self, tags: Set[str]) -> str:
        """按照模块分组生成推荐列表。"""
        sections_order = ["modules", "helpers", "templates", "cases", "errors"]
        lines: List[str] = []
        if tags:
            lines.append(f"Matched tags: {', '.join(sorted(tags))}")
        else:
            lines.append("Matched tags: (none)")

        for section in sections_order:
            items = self._select_items(section, tags)
            if not items:
                continue
            lines.append(f"[{section}]")
            for item in items:
                item_id = item.get("id", "unknown")
                if section == "modules":
                    summary = item.get("summary") or item.get("usage_notes") or ""
                elif section == "helpers":
                    summary = item.get("description") or ""
                elif section == "templates":
                    summary = item.get("description") or ""
                    file_hint = item.get("file")
                    if file_hint:
                        summary = f"{summary} (file: {file_hint})"
                elif section == "cases":
                    summary = item.get("summary") or ""
                    path_hint = item.get("path")
                    if path_hint:
                        summary = f"{summary} (query: {path_hint})"
                else:
                    summary = item.get("cause") or ""
                tag_list = ", ".join(item.get("tags", []))
                tag_suffix = f" [tags: {tag_list}]" if tag_list else ""
                summary = summary.strip()
                lines.append(f"- {item_id}: {summary}{tag_suffix}")
        return "\n".join(lines)

    def build_context(self, requirement: str) -> Dict[str, str]:
        if not self.ensure_mirror():
            return {}
        matched_tags = self.derive_tags(requirement)
        directory_index = self.build_directory_index()
        suggestions = self.build_suggestions(matched_tags)
        return {
            "kb_directory_index": directory_index,
            "kb_suggestions": suggestions,
            "relevant_tags": ", ".join(sorted(matched_tags)),
        }

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
    relevant_tags: Optional[str] = None,
) -> Dict[str, str]:
    """统一生成模板占位符映射。"""
    return {
        "ROUND_INDEX": str(round_index or 1),
        "LANGUAGE": (language or "java"),
        "REQUIREMENT": (requirement or ""),
        "PREV_ORIGINAL_QL": (prev_original_ql or ""),
        "PREV_FIX_SUGGESTIONS": (prev_fix_suggestions or ""),
        "QL_TEMPLATE": (ql_template or ""),
        "ERROR_LOG": (error_log or ""),
        "CURR_QL_CONTENT": (curr_ql_content or ""),
        "KB_DIRECTORY_INDEX": (kb_directory_index or ""),
        "KB_SUGGESTED_ITEMS": (kb_suggestions or ""),
        "RELEVANT_TAGS": (relevant_tags or ""),
    }


def apply_placeholders(content: str, values: Dict[str, str]) -> str:
    """将 [[KEY]] 占位符替换为实际内容。"""
    result = content
    for key, val in (values or {}).items():
        result = result.replace(f"[[{key}]]", val or "")
    return result


__all__ = [
    "PythonKnowledgeBase",
    "build_placeholder_map",
    "apply_placeholders",
]
