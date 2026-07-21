"""Shared project import workflow services."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from pure_auto_codeql.utils.project_importer import (
    ProjectImportResult,
    import_project,
)
from pure_auto_codeql.application.project_import_policy import (
    ProjectImportPolicy,
    ProjectImportPolicyError,
    validate_project_import_policy,
)


@dataclass(frozen=True)
class ProjectImportPolicySettings:
    """Caller-specific policy for project import workflows."""

    import_sources_dir: Path
    allow_external_import_paths: bool = False
    allow_build_commands: bool = False


@dataclass(frozen=True)
class ProjectImportRequest:
    """Application-level project import request shared by CLI and API callers."""

    source_path: str
    case_id: Optional[str] = None
    overwrite: bool = False
    language: Optional[str] = None
    skip_codeql: bool = False
    build_command: Optional[str] = None
    build_script: Optional[str] = None
    build_workdir: Optional[str] = None


def validate_project_import_request(
    request: ProjectImportRequest,
    *,
    policy: Optional[ProjectImportPolicySettings] = None,
) -> None:
    """Validate a project import request against optional caller policy."""

    if policy is None:
        return

    validate_project_import_policy(
        source_path=request.source_path,
        policy=ProjectImportPolicy(
            import_sources_dir=policy.import_sources_dir,
            allow_external_import_paths=policy.allow_external_import_paths,
            allow_build_commands=policy.allow_build_commands,
        ),
        build_command=request.build_command,
        build_script=request.build_script,
    )


def import_project_for_workflow(
    request: ProjectImportRequest,
    *,
    policy: Optional[ProjectImportPolicySettings] = None,
) -> ProjectImportResult:
    """Validate and import a CVE project for CLI/API workflows."""

    validate_project_import_request(request, policy=policy)
    return import_project(
        source_path=request.source_path,
        case_id=request.case_id,
        overwrite=request.overwrite,
        language=request.language,
        create_codeql_db=not request.skip_codeql,
        build_command=request.build_command,
        build_script=request.build_script,
        build_workdir=request.build_workdir,
    )
