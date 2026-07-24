"""Discovery and version pins for source-language LSP tooling."""

from __future__ import annotations

import os
import platform
import shutil
from pathlib import Path

from pure_auto_codeql.paths import get_repo_root

MCP_LANGUAGE_SERVER_MODULE = "github.com/isaacphi/mcp-language-server"
MCP_LANGUAGE_SERVER_VERSION = "v0.1.1"
JDTLS_VERSION = "1.60.0"
JDTLS_BUILD = "202606262232"


def find_executable(
    name: str,
    *,
    environment_variable: str | None = None,
    legacy_relative_path: str | None = None,
) -> str | None:
    """Resolve an executable from an override, legacy location, or PATH."""
    candidates: list[str] = []
    if environment_variable:
        candidates.append(os.getenv(environment_variable, ""))
    if legacy_relative_path:
        candidates.append(str(get_repo_root() / legacy_relative_path))
    candidates.append(shutil.which(name) or "")

    if platform.system() == "Windows" and not name.lower().endswith(".exe"):
        candidates.append(shutil.which(f"{name}.exe") or "")

    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(Path(candidate).resolve())
    return None


def find_lsp_mcp() -> str | None:
    executable = (
        "mcp-language-server.exe"
        if platform.system() == "Windows"
        else "mcp-language-server"
    )
    return find_executable(
        executable,
        environment_variable="PURE_AUTO_CODEQL_LSP_MCP",
        legacy_relative_path=(
            "utils/lsp/lsp-mcp.exe"
            if platform.system() == "Windows"
            else "utils/lsp/lsp-mcp"
        ),
    )


def source_language_server(language: str) -> tuple[str, str | None]:
    normalized = language.lower()
    if normalized == "python":
        name = "pyright-langserver"
    elif normalized == "java":
        name = "jdtls"
    elif normalized in {"c", "cpp", "c++"}:
        name = "clangd"
    else:
        raise ValueError(f"Unsupported source language: {language}")
    return name, find_executable(name)
