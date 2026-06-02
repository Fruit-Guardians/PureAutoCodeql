"""Project import policy checks for shared workflow callers."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional


class ProjectImportPolicyError(ValueError):
    """Raised when a project import request violates caller policy."""

    def __init__(self, message: str, status_code: int = 403) -> None:
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class ProjectImportPolicy:
    import_sources_dir: Path
    allow_external_import_paths: bool = False
    allow_build_commands: bool = False


def validate_project_import_policy(
    *,
    source_path: str,
    policy: ProjectImportPolicy,
    build_command: Optional[str] = None,
    build_script: Optional[str] = None,
) -> None:
    source = Path(source_path).expanduser().resolve()
    allowed_root = policy.import_sources_dir.expanduser().resolve()

    if not policy.allow_external_import_paths and not source.is_relative_to(allowed_root):
        raise ProjectImportPolicyError(
            "Import source_path must be under API_IMPORT_SOURCES_DIR "
            "unless API_ALLOW_EXTERNAL_IMPORT_PATHS=true",
            status_code=403,
        )

    if (build_command or build_script) and not policy.allow_build_commands:
        raise ProjectImportPolicyError(
            "API-provided build commands are disabled. Set API_ALLOW_API_BUILD_COMMANDS=true to enable.",
            status_code=403,
        )
