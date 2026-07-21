"""Compatibility wrapper for project import policy checks."""

from pure_auto_codeql.application.project_import_policy import (
    ProjectImportPolicy,
    ProjectImportPolicyError,
    validate_project_import_policy,
)

__all__ = [
    "ProjectImportPolicy",
    "ProjectImportPolicyError",
    "validate_project_import_policy",
]
