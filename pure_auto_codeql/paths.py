"""Repository path helpers for PureAutoCodeQL.

Use these instead of hand-counting ``Path(__file__).parent`` hops when
resolving assets that live at the repository root (prompts/, tools/,
resources/, projects/, config/). Nested packages under
``pure_auto_codeql/`` make relative parent walks brittle.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

_MARKERS = ("pyproject.toml",)


def _looks_like_repo_root(path: Path) -> bool:
    if not (path / "pyproject.toml").is_file():
        return False
    # Prefer a root that still has the runtime asset layout used in-repo.
    if (path / "prompts").is_dir() or (path / "tools").is_dir():
        return True
    return True


@lru_cache(maxsize=1)
def get_repo_root() -> Path:
    """Return the repository root directory.

    Walks upward from this file until a directory containing
    ``pyproject.toml`` is found. Falls back to three parents above this
    module (``pure_auto_codeql/paths.py`` → repo root in the normal
    checkout layout) if markers are missing (e.g. unusual install).
    """
    start = Path(__file__).resolve().parent
    for candidate in (start, *start.parents):
        if _looks_like_repo_root(candidate):
            return candidate

    # pure_auto_codeql/paths.py → pure_auto_codeql/ → <repo>
    return start.parent.parent


def prompts_dir() -> Path:
    """Return the repository ``prompts/`` directory."""
    return get_repo_root() / "prompts"
