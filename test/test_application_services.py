from pathlib import Path

import pytest

from pure_auto_codeql.application import (
    AnalysisValidationError,
    ProjectImportPolicyError,
    ProjectImportPolicySettings,
    ProjectImportRequest,
    import_project_for_workflow,
    validate_analysis_case,
    validate_project_import_request,
)


def test_application_import_policy_rejects_unsafe_paths_and_build_commands(tmp_path):
    allowed = tmp_path / "imports"
    outside = tmp_path / "outside"
    allowed.mkdir()
    outside.mkdir()
    policy = ProjectImportPolicySettings(import_sources_dir=allowed)

    with pytest.raises(ProjectImportPolicyError, match="API_IMPORT_SOURCES_DIR"):
        validate_project_import_request(
            ProjectImportRequest(source_path=str(outside)),
            policy=policy,
        )

    with pytest.raises(ProjectImportPolicyError, match="build commands"):
        validate_project_import_request(
            ProjectImportRequest(source_path=str(allowed), build_command="make"),
            policy=policy,
        )


def test_application_import_workflow_delegates_to_importer(monkeypatch, tmp_path):
    observed = {}

    def fake_import_project(**kwargs):
        observed.update(kwargs)
        return "result"

    monkeypatch.setattr(
        "pure_auto_codeql.application.project_import.import_project",
        fake_import_project,
    )

    result = import_project_for_workflow(
        ProjectImportRequest(
            source_path=str(tmp_path),
            case_id="CASE-1",
            overwrite=True,
            language="python",
            skip_codeql=True,
        )
    )

    assert result == "result"
    assert observed == {
        "source_path": str(tmp_path),
        "case_id": "CASE-1",
        "overwrite": True,
        "language": "python",
        "create_codeql_db": False,
        "build_command": None,
        "build_script": None,
        "build_workdir": None,
    }


def test_application_analysis_validation_maps_case_errors(tmp_path):
    projects = tmp_path / "projects"
    projects.mkdir()

    with pytest.raises(AnalysisValidationError) as escape_exc:
        validate_analysis_case("../outside", projects_dir=projects)
    assert escape_exc.value.status_code == 400
    assert "无效的项目ID" in str(escape_exc.value)

    with pytest.raises(AnalysisValidationError) as missing_exc:
        validate_analysis_case("CASE-404", projects_dir=projects)
    assert missing_exc.value.status_code == 404
    assert "不存在" in str(missing_exc.value)


def test_canonical_and_legacy_import_surfaces_remain_available():
    import config
    import pure_auto_codeql.configuration as canonical_config
    import utils.project_import_policy as legacy_policy
    from Analyze import cli as legacy_cli
    from config import get_llm_config as legacy_get_llm_config
    from pure_auto_codeql.application import ProjectImportPolicyError
    from pure_auto_codeql.cli.app import cli as canonical_cli
    from pure_auto_codeql.information import ghsa_fetch as canonical_ghsa
    from pure_auto_codeql.information import nvd_info_fetch as canonical_nvd
    from pure_auto_codeql.paths import get_repo_root, prompts_dir
    from Information import ghsa_fetch as legacy_ghsa
    from Information import nvd_info_fetch as legacy_nvd

    config_path = Path(config.__file__)
    assert config_path.name == "__init__.py"
    assert config_path.parent.name == "config"
    assert canonical_cli is legacy_cli
    assert canonical_config.LLMRole is config.LLMRole
    assert canonical_config.get_llm_config is legacy_get_llm_config
    assert callable(canonical_config.get_llm_config)
    assert legacy_policy.ProjectImportPolicyError is ProjectImportPolicyError

    # Information package migration shims
    assert legacy_ghsa is canonical_ghsa
    assert legacy_nvd is canonical_nvd
    assert canonical_ghsa.AdvisoryLookupError is legacy_ghsa.AdvisoryLookupError
    assert canonical_nvd.CveLookupError is legacy_nvd.CveLookupError

    # Repo root helper resolves real asset layout
    root = get_repo_root()
    assert (root / "pyproject.toml").is_file()
    assert (prompts_dir() / "codeql_generate.md").is_file()


def test_api_environment_settings_override_keys_toml_settings(monkeypatch):
    from api.config import _create_config

    monkeypatch.setenv("API_USE_DOCKER_FOR_CPP", "true")
    monkeypatch.setattr(
        "api.config._load_keys_toml_settings",
        lambda: {
            "use_docker_for_cpp": False,
            "docker_builder_image": "from-keys",
        },
    )

    api_config = _create_config()

    assert api_config.use_docker_for_cpp is True
    assert api_config.docker_builder_image == "from-keys"
