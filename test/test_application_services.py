
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
    """Canonical package surfaces and key entry points stay wired."""
    import pure_auto_codeql.config as config_impl
    import pure_auto_codeql.configuration as canonical_config
    from Analyze import cli as legacy_cli
    from pure_auto_codeql.api.config import get_config
    from pure_auto_codeql.application import ProjectImportPolicyError
    from pure_auto_codeql.cli.app import cli as canonical_cli
    from pure_auto_codeql.configuration import get_llm_config
    from pure_auto_codeql.core import AnalysisOrchestrator
    from pure_auto_codeql.information import ghsa_fetch, nvd_info_fetch
    from pure_auto_codeql.paths import get_repo_root, prompts_dir
    from pure_auto_codeql.prompts import build_sink_prompt
    from pure_auto_codeql.services import MultiAgentAnalyzer
    from pure_auto_codeql.tools import CodeQLComposeTool
    from pure_auto_codeql.utils.doctor import collect_diagnostics
    from pure_auto_codeql.utils.project_import_policy import ProjectImportPolicyError as util_policy_err

    assert canonical_cli is legacy_cli
    assert canonical_config.LLMRole is config_impl.LLMRole
    assert canonical_config.get_llm_config is get_llm_config
    assert callable(get_llm_config)
    assert util_policy_err is ProjectImportPolicyError

    assert ghsa_fetch.AdvisoryLookupError
    assert nvd_info_fetch.CveLookupError
    assert callable(build_sink_prompt)
    assert CodeQLComposeTool
    assert MultiAgentAnalyzer
    assert AnalysisOrchestrator
    assert get_config().project_root.resolve() == get_repo_root().resolve()
    assert collect_diagnostics()

    root = get_repo_root()
    assert (root / "pyproject.toml").is_file()
    assert (root / "config" / "keys.example.toml").is_file()
    assert (prompts_dir() / "codeql_generate.md").is_file()
    assert "pure_auto_codeql" in prompts_dir().parts
    assert (root / "tools" / "mcp_ripgrep" / "package.json").is_file()
    assert not (root / "projects" / "python_kb").exists() or True  # mirror may be created at runtime


def test_api_environment_settings_override_keys_toml_settings(monkeypatch):
    from pure_auto_codeql.api.config import _create_config

    monkeypatch.setenv("API_USE_DOCKER_FOR_CPP", "true")
    monkeypatch.setattr(
        "pure_auto_codeql.api.config._load_keys_toml_settings",
        lambda: {
            "use_docker_for_cpp": False,
            "docker_builder_image": "from-keys",
        },
    )

    api_config = _create_config()

    assert api_config.use_docker_for_cpp is True
    assert api_config.docker_builder_image == "from-keys"
