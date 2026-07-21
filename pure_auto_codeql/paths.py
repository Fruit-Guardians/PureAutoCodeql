"""Repository path helpers for PureAutoCodeQL.

Use these instead of hand-counting ``Path(__file__).parent`` hops when
resolving assets that live at the repository root (``prompts/``, ``tools/``,
``resources/``, ``projects/``, ``config/``). Nested packages under
``pure_auto_codeql/`` make relative parent walks brittle.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path


def _looks_like_repo_root(path: Path) -> bool:
    """True when *path* is the PureAutoCodeQL checkout root."""
    return (path / "pyproject.toml").is_file()


@lru_cache(maxsize=1)
def get_repo_root() -> Path:
    """Return the repository root directory.

    Walks upward from this file until a directory containing
    ``pyproject.toml`` is found. Falls back to the parent of the
    ``pure_auto_codeql`` package (normal checkout layout) if no marker is
    found (e.g. unusual install layout).
    """
    start = Path(__file__).resolve().parent
    for candidate in (start, *start.parents):
        if _looks_like_repo_root(candidate):
            return candidate

    # pure_auto_codeql/paths.py → pure_auto_codeql/ → <repo>
    return start.parent.parent


def prompts_dir() -> Path:
    """Return the directory that holds prompt templates (``*.md``).

    Canonical location is ``pure_auto_codeql/prompts/`` next to the prompt
    Python package. Falls back to a top-level ``prompts/`` directory if the
    nested package layout is not present (unusual installs).
    """
    package_prompts = Path(__file__).resolve().parent / "prompts"
    if package_prompts.is_dir() and any(package_prompts.glob("*.md")):
        return package_prompts

    legacy = get_repo_root() / "prompts"
    return legacy
