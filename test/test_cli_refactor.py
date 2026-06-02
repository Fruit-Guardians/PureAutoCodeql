from pathlib import Path

import pytest

from Analyze import _normalize_cli_args
from utils.project_import_policy import (
    ProjectImportPolicy,
    ProjectImportPolicyError,
    validate_project_import_policy,
)


def test_cli_subcommands_translate_to_legacy_flags():
    assert _normalize_cli_args(["doctor"]) == ["--doctor"]
    assert _normalize_cli_args(["list"]) == ["--list"]
    assert _normalize_cli_args(["providers"]) == ["--list-providers"]
    assert _normalize_cli_args(["models"]) == ["--list-models"]
    assert _normalize_cli_args(["analyze", "CVE-1", "--no-stream"]) == [
        "--case",
        "CVE-1",
        "--no-stream",
    ]
    assert _normalize_cli_args(["import", "/tmp/case", "--import-overwrite"]) == [
        "--import-project",
        "/tmp/case",
        "--import-overwrite",
    ]
    assert _normalize_cli_args(["md", "vuln.md", "--language", "python"]) == [
        "--md-file",
        "vuln.md",
        "--language",
        "python",
    ]


def test_cli_legacy_flags_are_unchanged():
    assert _normalize_cli_args(["--case", "CVE-1"]) == ["--case", "CVE-1"]


def test_project_import_policy_allows_configured_import_root(tmp_path):
    allowed_root = tmp_path / "imports"
    allowed_root.mkdir()

    validate_project_import_policy(
        source_path=str(allowed_root),
        policy=ProjectImportPolicy(import_sources_dir=allowed_root),
    )


def test_project_import_policy_rejects_external_path_and_build_command(tmp_path):
    allowed_root = tmp_path / "imports"
    outside = tmp_path / "outside"
    allowed_root.mkdir()
    outside.mkdir()

    policy = ProjectImportPolicy(import_sources_dir=allowed_root)

    with pytest.raises(ProjectImportPolicyError, match="API_IMPORT_SOURCES_DIR"):
        validate_project_import_policy(source_path=str(outside), policy=policy)

    with pytest.raises(ProjectImportPolicyError, match="build commands"):
        validate_project_import_policy(
            source_path=str(allowed_root),
            policy=policy,
            build_command="make",
        )


def test_project_import_policy_can_opt_in_to_external_path_and_build_command(tmp_path):
    outside = tmp_path / "outside"
    outside.mkdir()

    validate_project_import_policy(
        source_path=str(outside),
        policy=ProjectImportPolicy(
            import_sources_dir=Path("/unused"),
            allow_external_import_paths=True,
            allow_build_commands=True,
        ),
        build_script="build.sh",
    )
