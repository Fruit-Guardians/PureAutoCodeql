import asyncio
import importlib
import zipfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from pure_auto_codeql.api.models import ProjectImportRequest, TaskStatus
from pure_auto_codeql.api.projects_routes import _ensure_api_import_allowed
from pure_auto_codeql.api.task_manager import TaskManager
from pure_auto_codeql.core.context import AnalysisResult as CoreAnalysisResult
from pure_auto_codeql.services.llm_service import AgentResult
from pure_auto_codeql.utils.case import resolve_case
from pure_auto_codeql.utils.project_importer import import_project
from pure_auto_codeql.utils.project_import_policy import ProjectImportPolicyError


def test_resolve_case_rejects_path_escape(tmp_path):
    base = tmp_path / "projects"
    base.mkdir()

    with pytest.raises(ValueError):
        resolve_case("../outside", base_dir=base)

    with pytest.raises(ValueError):
        resolve_case(str(tmp_path / "outside"), base_dir=base)


def test_api_import_guard_restricts_paths_and_build_commands(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config

    allowed = tmp_path / "imports"
    allowed.mkdir()
    outside = tmp_path / "outside"
    outside.mkdir()

    monkeypatch.setattr(api_config.config, "import_sources_dir", allowed)
    monkeypatch.setattr(api_config.config, "allow_external_import_paths", False)
    monkeypatch.setattr(api_config.config, "allow_api_build_commands", False)

    _ensure_api_import_allowed(ProjectImportRequest(source_path=str(allowed), skip_codeql=True))

    with pytest.raises(ProjectImportPolicyError) as outside_exc:
        _ensure_api_import_allowed(ProjectImportRequest(source_path=str(outside), skip_codeql=True))
    assert outside_exc.value.status_code == 403

    with pytest.raises(ProjectImportPolicyError) as command_exc:
        _ensure_api_import_allowed(
            ProjectImportRequest(
                source_path=str(allowed),
                skip_codeql=True,
                build_command="make",
            )
        )
    assert command_exc.value.status_code == 403


def test_projects_import_endpoint_enforces_shared_policy(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config
    import api.server as server_module

    allowed = tmp_path / "imports"
    outside = tmp_path / "outside"
    projects = tmp_path / "projects"
    allowed.mkdir()
    outside.mkdir()
    projects.mkdir()

    monkeypatch.setattr(api_config.config, "auth_token", "")
    monkeypatch.setattr(api_config.config, "import_sources_dir", allowed)
    monkeypatch.setattr(api_config.config, "projects_dir", projects)
    monkeypatch.setattr(api_config.config, "allow_external_import_paths", False)
    monkeypatch.setattr(api_config.config, "allow_api_build_commands", False)

    client = TestClient(server_module.app)

    outside_response = client.post(
        "/api/projects/import",
        json={"source_path": str(outside), "skip_codeql": True},
    )
    assert outside_response.status_code == 403
    assert "API_IMPORT_SOURCES_DIR" in outside_response.json()["detail"]

    command_response = client.post(
        "/api/projects/import",
        json={
            "source_path": str(allowed),
            "skip_codeql": True,
            "build_command": "make",
        },
    )
    assert command_response.status_code == 403
    assert "build commands" in command_response.json()["detail"]


def test_analysis_start_endpoint_rejects_unsafe_case_id(monkeypatch, tmp_path):
    from pure_auto_codeql.api import config as api_config
    import api.server as server_module

    monkeypatch.setattr(api_config.config, "auth_token", "")
    monkeypatch.setattr(api_config.config, "projects_dir", tmp_path / "projects")

    client = TestClient(server_module.app)
    response = client.post("/api/analysis/start", json={"case_id": "../outside"})

    assert response.status_code == 400
    assert "无效的项目ID" in response.json()["detail"]


def test_auth_token_protects_api_routes(monkeypatch):
    from pure_auto_codeql.api import config as api_config
    import api.server as server_module

    monkeypatch.setattr(api_config.config, "auth_token", "secret-token")
    reloaded = importlib.reload(server_module)

    try:
        client = TestClient(reloaded.app)

        assert client.get("/health").status_code == 200
        assert client.get("/api/version").status_code == 401
        assert client.get(
            "/api/version",
            headers={"Authorization": "Bearer secret-token"},
        ).status_code == 200
    finally:
        monkeypatch.setattr(api_config.config, "auth_token", "")
        importlib.reload(server_module)


def test_project_files_skips_symlinks_outside_source_root(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config
    from pure_auto_codeql.api.projects_routes import get_project_files

    projects = tmp_path / "projects"
    source = projects / "CASE-1" / "source_code"
    db = projects / "CASE-1" / "db"
    inputs = projects / "CASE-1" / "inputs"
    intel = projects / "CASE-1" / "intel"
    for directory in (source, db, inputs, intel):
        directory.mkdir(parents=True)

    (source / "inside.py").write_text("print('ok')\n", encoding="utf-8")
    external = tmp_path / "external.txt"
    external.write_text("secret\n", encoding="utf-8")
    (source / "external-link.txt").symlink_to(external)

    monkeypatch.setattr(api_config.config, "projects_dir", projects)

    response = asyncio.run(get_project_files("CASE-1", directory=None, max_files=1000))
    assert {item.path for item in response.files} == {"inside.py"}


def test_import_project_skips_external_symlink(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config

    projects = tmp_path / "projects"
    source_root = tmp_path / "incoming"
    src = source_root / "src"
    src.mkdir(parents=True)
    (src / "main.py").write_text("print('ok')\n", encoding="utf-8")
    external = tmp_path / "external.txt"
    external.write_text("secret\n", encoding="utf-8")
    (src / "external-link.txt").symlink_to(external)

    monkeypatch.setattr(api_config.config, "projects_dir", projects)

    result = import_project(str(source_root), case_id="CASE-2", create_codeql_db=False)
    target_source = Path(result.target_path) / "source_code"

    assert (target_source / "main.py").exists()
    assert not (target_source / "external-link.txt").exists()


def test_import_project_rejects_zip_symlink(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config

    projects = tmp_path / "projects"
    source_root = tmp_path / "incoming"
    src = source_root / "src"
    src.mkdir(parents=True)
    zip_path = src / "src.zip"

    with zipfile.ZipFile(zip_path, "w") as archive:
        info = zipfile.ZipInfo("link")
        info.external_attr = (0o120777 << 16)
        archive.writestr(info, "../outside")

    monkeypatch.setattr(api_config.config, "projects_dir", projects)

    with pytest.raises(ValueError, match="Symlink entries"):
        import_project(str(source_root), case_id="CASE-3", create_codeql_db=False)


def test_import_project_skips_external_metadata_symlink(tmp_path, monkeypatch):
    from pure_auto_codeql.api import config as api_config

    projects = tmp_path / "projects"
    source_root = tmp_path / "incoming"
    src = source_root / "src"
    src.mkdir(parents=True)
    (src / "main.py").write_text("print('ok')\n", encoding="utf-8")

    external = tmp_path / "CVE-2024-0001.json"
    external.write_text('{"secret": true}\n', encoding="utf-8")
    (source_root / "CVE-2024-0001.json").symlink_to(external)

    monkeypatch.setattr(api_config.config, "projects_dir", projects)

    result = import_project(str(source_root), case_id="CASE-4", create_codeql_db=False)
    target_inputs = Path(result.target_path) / "inputs"

    assert result.metadata_files == []
    assert not (target_inputs / "CVE-2024-0001.json").exists()


@pytest.mark.asyncio
async def test_task_manager_rejects_duplicate_start_and_preserves_language(monkeypatch):
    observed = {}

    async def fake_analyze_case(self, case_id, language=None):
        observed["case_id"] = case_id
        observed["language"] = language
        return CoreAnalysisResult(
            case_id=case_id,
            language=language or "unknown",
            cve_result=AgentResult(content="cve", success=True),
            sink_result=AgentResult(content="sink", success=True),
            source_result=AgentResult(content="source", success=True),
            codeql_result=AgentResult(content="ql", success=True),
            codeql_execution_result={"success": True, "sarif_path": "run.sarif", "findings_count": 2},
            success=True,
            output_directory="output/run",
        )

    from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator

    monkeypatch.setattr(AnalysisOrchestrator, "analyze_case", fake_analyze_case)

    manager = TaskManager()
    task_id = manager.create_task("CASE-4")

    assert await manager.start_task(task_id, {"language": "java"}) is True
    assert await manager.start_task(task_id, {"language": "python"}) is False

    await manager._running_tasks[task_id]

    task_info = manager.get_task_status(task_id)
    result = manager.get_task_result(task_id)

    assert observed == {"case_id": "CASE-4", "language": "java"}
    assert task_info.status == TaskStatus.COMPLETED
    assert result.source_analysis["content"] == "source"
    assert result.query_results["findings_count"] == 2
