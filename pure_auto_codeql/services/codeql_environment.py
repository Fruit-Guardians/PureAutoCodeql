"""Cross-platform discovery for the CodeQL CLI."""

from __future__ import annotations

import json
import os
import platform
import shutil
import subprocess
from pathlib import Path

CODEQL_EXECUTABLE_ENV = "PURE_AUTO_CODEQL_CODEQL"


def find_codeql(override: str | Path | None = None) -> str | None:
    """Resolve CodeQL from an explicit override, environment, or ``PATH``.

    Resolution never assumes a user-specific installation directory.  The
    returned path is absolute so child processes see the same executable even
    when their working directory or ``PATH`` differs.
    """

    names = ["codeql.exe", "codeql"] if platform.system() == "Windows" else ["codeql"]
    candidates = [
        str(override or ""),
        os.getenv(CODEQL_EXECUTABLE_ENV, ""),
    ]
    candidates.extend(shutil.which(name) or "" for name in names)

    for candidate in candidates:
        if not candidate:
            continue
        path = Path(candidate).expanduser()
        if path.is_file():
            return str(path.resolve())
    return None


def find_codeql_distribution_root(executable: str | Path) -> Path | None:
    """Return the unpacked CodeQL bundle root containing QL packs."""

    executable_path = Path(executable).expanduser().resolve()
    try:
        completed = subprocess.run(
            [str(executable_path), "version", "--format=json"],
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
        if completed.returncode == 0:
            location = json.loads(completed.stdout).get("unpackedLocation")
            if location:
                root = Path(location).expanduser().resolve()
                if (root / ".codeqlmanifest.json").is_file():
                    return root
    except (OSError, subprocess.TimeoutExpired, ValueError, json.JSONDecodeError):
        pass

    for candidate in (executable_path.parent, executable_path.parent.parent):
        if (candidate / ".codeqlmanifest.json").is_file():
            return candidate
    return None


def missing_required_language_packs(executable: str | Path) -> list[str]:
    """Return required core language packs absent from the CodeQL bundle."""

    root = find_codeql_distribution_root(executable)
    required = ("python-all", "java-all", "cpp-all")
    if root is None:
        return list(required)

    codeql_packs = root / "qlpacks" / "codeql"
    return [name for name in required if not any((codeql_packs / name).glob("*/qlpack.yml"))]


__all__ = [
    "CODEQL_EXECUTABLE_ENV",
    "find_codeql",
    "find_codeql_distribution_root",
    "missing_required_language_packs",
]
