from pathlib import Path
from types import SimpleNamespace

import pytest

from pure_auto_codeql.cli.app import (
    _normalize_cli_args,
    dispatch_command,
    parse_arguments,
)
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


def test_parse_arguments_accepts_legacy_and_subcommand_forms():
    legacy_args = parse_arguments(["--case", "CVE-1", "--no-stream"])
    subcommand_args = parse_arguments(["analyze", "CVE-1", "--no-stream"])

    assert legacy_args.case == "CVE-1"
    assert subcommand_args.case == "CVE-1"
    assert legacy_args.stream is False
    assert subcommand_args.stream is False


@pytest.mark.asyncio
async def test_dispatch_command_routes_import_handler(monkeypatch):
    observed = {}

    def fake_import_project_for_workflow(request, *, policy=None):
        observed.update(
            source_path=request.source_path,
            case_id=request.case_id,
            overwrite=request.overwrite,
            language=request.language,
            skip_codeql=request.skip_codeql,
            build_command=request.build_command,
            build_script=request.build_script,
            build_workdir=request.build_workdir,
            policy=policy,
        )
        return SimpleNamespace(
            case_id=request.case_id,
            target_path="/tmp/projects/CASE-1",
            language=request.language,
            metadata_files=[],
            codeql_created=False,
            codeql_error=None,
            build_command=request.build_command,
            build_workdir=request.build_workdir,
        )

    monkeypatch.setattr(
        "pure_auto_codeql.cli.app.import_project_for_workflow",
        fake_import_project_for_workflow,
    )

    args = parse_arguments(
        [
            "import",
            "/tmp/case",
            "--import-case-id",
            "CASE-1",
            "--import-overwrite",
            "--import-skip-codeql",
        ]
    )

    await dispatch_command(args)

    assert observed == {
        "source_path": "/tmp/case",
        "case_id": "CASE-1",
        "overwrite": True,
        "language": None,
        "skip_codeql": True,
        "build_command": None,
        "build_script": None,
        "build_workdir": None,
        "policy": None,
    }


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
