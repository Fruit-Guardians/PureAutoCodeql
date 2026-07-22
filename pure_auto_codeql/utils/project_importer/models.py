"""Result and plan dataclasses for project import."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path  # noqa: F401  (kept for annotations parity)
from typing import List, Optional


@dataclass
class ProjectImportResult:
    """Information returned after importing a project."""

    case_id: str
    target_path: str
    language: Optional[str] = None
    metadata_files: List[str] = field(default_factory=list)
    codeql_created: bool = False
    codeql_error: Optional[str] = None
    build_command: Optional[str] = None
    build_workdir: Optional[str] = None

@dataclass
class BuildPlan:
    """Resolved build instructions for CodeQL database creation."""

    command: Optional[str]
    working_dir: Path
    description: str = ""
    mode: str = "manual"
