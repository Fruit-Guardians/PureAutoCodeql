from pathlib import Path

import pure_auto_codeql.services.mcp_language_config as language_config_module
from pure_auto_codeql.services.lsp_environment import find_executable
from pure_auto_codeql.services.mcp_language_config import MCPLanguageConfigService


def test_find_executable_prefers_explicit_override(tmp_path: Path, monkeypatch) -> None:
    executable = tmp_path / "custom-bridge"
    executable.write_text("#!/bin/sh\n", encoding="utf-8")
    executable.chmod(0o755)
    monkeypatch.setenv("TEST_LSP_BRIDGE", str(executable))

    assert (
        find_executable(
            "missing-command",
            environment_variable="TEST_LSP_BRIDGE",
        )
        == str(executable.resolve())
    )


def test_language_configs_use_generic_bridge_and_exact_servers(
    tmp_path: Path,
    monkeypatch,
) -> None:
    bridge = tmp_path / "mcp-language-server"
    bridge.touch()
    servers = {
        "python": str(tmp_path / "pyright-langserver"),
        "java": str(tmp_path / "jdtls"),
        "cpp": str(tmp_path / "clangd"),
    }
    for path in servers.values():
        Path(path).touch()

    monkeypatch.setattr(
        language_config_module,
        "find_lsp_mcp",
        lambda: str(bridge),
    )
    monkeypatch.setattr(
        language_config_module,
        "source_language_server",
        lambda language: (Path(servers[language]).name, servers[language]),
    )
    service = MCPLanguageConfigService(config_provider={})

    python = service.get_language_server_config("python", str(tmp_path))
    java = service.get_language_server_config("java", str(tmp_path))
    cpp = service.get_language_server_config("cpp", str(tmp_path))

    assert python["command"] == str(bridge)
    assert python["args"] == [
        "--workspace",
        str(tmp_path.resolve()),
        "--lsp",
        servers["python"],
        "--",
        "--stdio",
    ]
    assert java["args"] == [
        "--workspace",
        str(tmp_path.resolve()),
        "--lsp",
        servers["java"],
    ]
    assert cpp["args"] == [
        "--workspace",
        str(tmp_path.resolve()),
        "--lsp",
        servers["cpp"],
    ]
