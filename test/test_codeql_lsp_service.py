from __future__ import annotations

import io
import subprocess
from pathlib import Path

from pure_auto_codeql.services.codeql_environment import (
    find_codeql,
    find_codeql_distribution_root,
    missing_required_language_packs,
)
from pure_auto_codeql.services.lsp_service import CodeQLLSPService


class _ExitedProcess:
    def __init__(self) -> None:
        self.stderr = io.StringIO("No module named broken.module\n")
        self.returncode = 1
        self.pid = 12345

    def poll(self) -> int:
        return self.returncode


class _CompletedProcess:
    def __init__(self, returncode: int, stdout: str = "", stderr: str = "") -> None:
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        self.pid = 12346

    def communicate(self, timeout=None) -> tuple[str, str]:
        return self.stdout, self.stderr


def _pack(tmp_path: Path) -> tuple[Path, Path]:
    pack = tmp_path / "pack"
    pack.mkdir()
    (pack / "qlpack.yml").write_text("name: test/queries\nversion: 1.0.0\n")
    query = pack / "query.ql"
    query.write_text("select 1")
    return pack, query


def test_service_starts_current_package_module_and_surfaces_stderr(tmp_path: Path, monkeypatch) -> None:
    pack, query = _pack(tmp_path)
    codeql = tmp_path / "codeql"
    codeql.touch()
    captured: dict[str, list[str]] = {}

    def fake_popen(cmd, **kwargs):
        captured["cmd"] = list(cmd)
        return _ExitedProcess()

    monkeypatch.setattr(subprocess, "Popen", fake_popen)
    monkeypatch.setattr(
        "pure_auto_codeql.services.lsp_service.missing_required_language_packs",
        lambda executable: [],
    )
    service = CodeQLLSPService(
        pack,
        query,
        port=18766,
        init_timeout=0.1,
        codeql=codeql,
    )

    assert service.start() is False
    assert captured["cmd"][1:3] == ["-m", "pure_auto_codeql.tools.lsp_codeql"]
    assert captured["cmd"][captured["cmd"].index("--codeql") + 1] == service.codeql
    assert "No module named broken.module" in (service.last_error or "")
    assert service.process is None
    assert service._http.trust_env is False


def test_find_codeql_prefers_portable_environment_override(tmp_path: Path, monkeypatch) -> None:
    executable = tmp_path / "managed-codeql"
    executable.touch()
    monkeypatch.setenv("PURE_AUTO_CODEQL_CODEQL", str(executable))
    monkeypatch.setattr("shutil.which", lambda name: None)

    assert find_codeql() == str(executable.resolve())


def test_find_codeql_ignores_invalid_override_and_uses_path(tmp_path: Path, monkeypatch) -> None:
    executable = tmp_path / "codeql"
    executable.touch()
    monkeypatch.setenv("PURE_AUTO_CODEQL_CODEQL", str(tmp_path / "missing"))
    monkeypatch.setattr(
        "shutil.which",
        lambda name: str(executable) if name == "codeql" else None,
    )

    assert find_codeql() == str(executable.resolve())


def test_distribution_root_comes_from_codeql_version_json(tmp_path: Path, monkeypatch) -> None:
    executable = tmp_path / "bin" / "codeql"
    executable.parent.mkdir()
    executable.touch()
    bundle = tmp_path / "bundle"
    bundle.mkdir()
    (bundle / ".codeqlmanifest.json").write_text("{}")
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0],
            0,
            f'{{"unpackedLocation": "{bundle}"}}',
            "",
        ),
    )

    assert find_codeql_distribution_root(executable) == bundle.resolve()


def test_required_language_pack_check_accepts_complete_bundle(tmp_path: Path, monkeypatch) -> None:
    executable = tmp_path / "bundle" / "codeql"
    executable.parent.mkdir()
    executable.touch()
    (executable.parent / ".codeqlmanifest.json").write_text("{}")
    for name in ("python-all", "java-all", "cpp-all"):
        pack = executable.parent / "qlpacks" / "codeql" / name / "1.0.0"
        pack.mkdir(parents=True)
        (pack / "qlpack.yml").write_text(f"name: codeql/{name}\n")
    monkeypatch.setattr(
        subprocess,
        "run",
        lambda *args, **kwargs: subprocess.CompletedProcess(
            args[0],
            0,
            f'{{"unpackedLocation": "{executable.parent}"}}',
            "",
        ),
    )

    assert missing_required_language_packs(executable) == []


def test_cli_syntax_fallback_passes_valid_query(tmp_path: Path, monkeypatch) -> None:
    pack, query = _pack(tmp_path)
    codeql = tmp_path / "codeql"
    codeql.touch()
    monkeypatch.setattr(
        "pure_auto_codeql.services.lsp_service.missing_required_language_packs",
        lambda executable: [],
    )
    service = CodeQLLSPService(pack, query, codeql=codeql)
    monkeypatch.setattr(
        subprocess,
        "Popen",
        lambda *args, **kwargs: _CompletedProcess(0, "ok", ""),
    )

    result = service.check_syntax("import python\nselect 1")

    assert result["validator"] == "codeql-cli"
    assert result["summary"]["errors"] == 0
    assert query.read_text() == "import python\nselect 1"


def test_cli_syntax_fallback_returns_structured_diagnostic(tmp_path: Path, monkeypatch) -> None:
    pack, query = _pack(tmp_path)
    codeql = tmp_path / "codeql"
    codeql.touch()
    monkeypatch.setattr(
        "pure_auto_codeql.services.lsp_service.missing_required_language_packs",
        lambda executable: [],
    )
    service = CodeQLLSPService(pack, query, codeql=codeql)
    monkeypatch.setattr(
        subprocess,
        "Popen",
        lambda *args, **kwargs: _CompletedProcess(2, "", "ERROR: unexpected token"),
    )

    result = service.check_syntax("broken")

    assert result["validator"] == "codeql-cli"
    assert result["summary"]["errors"] == 1
    assert result["diagnostics"][0]["source"] == "codeql-cli"
    assert "unexpected token" in result["diagnostics"][0]["message"]
