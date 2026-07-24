#!/usr/bin/env python3
"""Cross-platform, idempotent PureAutoCodeQL environment bootstrap."""

from __future__ import annotations

import argparse
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS_DIR = ROOT / ".tools"
CODEQL_VERSION = "2.26.1"
MCP_LANGUAGE_SERVER_MODULE = "github.com/isaacphi/mcp-language-server"
MCP_LANGUAGE_SERVER_VERSION = "v0.1.1"
JDTLS_VERSION = "1.60.0"
JDTLS_BUILD = "202606262232"


def log(message: str) -> None:
    print(message, flush=True)


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    log(f"  $ {' '.join(command)}")
    return subprocess.run(command, cwd=ROOT, text=True, check=check)


def require(command: str, hint: str) -> str:
    executable = shutil.which(command)
    if executable:
        return executable
    raise RuntimeError(f"{command} is required. {hint}")


def venv_bin_dir() -> Path:
    return ROOT / ".venv" / ("Scripts" if platform.system() == "Windows" else "bin")


def install_wrapper(name: str, target: Path, arguments: list[str] | None = None) -> None:
    bin_dir = venv_bin_dir()
    bin_dir.mkdir(parents=True, exist_ok=True)
    arguments = arguments or []
    wrapper = bin_dir / (f"{name}.cmd" if platform.system() == "Windows" else name)
    if target.resolve() == wrapper.resolve():
        raise RuntimeError(
            f"Refusing to create a recursive wrapper for {name}: {wrapper}"
        )

    if platform.system() == "Windows":
        rendered_args = " ".join(f'"{value}"' for value in arguments)
        wrapper.write_text(
            f'@echo off\r\n"{target}" {rendered_args} %*\r\n',
            encoding="utf-8",
        )
        return

    rendered_args = " ".join(_shell_quote(value) for value in arguments)
    wrapper.write_text(
        f"#!/usr/bin/env sh\nexec {_shell_quote(str(target))} {rendered_args} \"$@\"\n",
        encoding="utf-8",
    )
    wrapper.chmod(0o755)


def _shell_quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "PureAutoCodeQL-bootstrap"},
    )
    log(f"  downloading {url}")
    with urllib.request.urlopen(request, timeout=60) as response:
        with destination.open("wb") as output:
            shutil.copyfileobj(response, output)


def codeql_asset() -> str:
    system = platform.system()
    return {
        "Linux": "codeql-linux64.zip",
        "Darwin": "codeql-osx64.zip",
        "Windows": "codeql-win64.zip",
    }.get(system, "")


def ensure_codeql(allow_download: bool) -> None:
    if shutil.which("codeql"):
        log(f"[ok] CodeQL: {shutil.which('codeql')}")
        return
    if not allow_download:
        raise RuntimeError("CodeQL is missing and automatic download was disabled.")

    asset = codeql_asset()
    if not asset:
        raise RuntimeError(f"Automatic CodeQL installation is unsupported on {platform.system()}.")

    install_root = TOOLS_DIR / f"codeql-{CODEQL_VERSION}"
    executable_name = "codeql.exe" if platform.system() == "Windows" else "codeql"
    executable = install_root / "codeql" / executable_name
    if not executable.exists():
        url = (
            "https://github.com/github/codeql-cli-binaries/releases/download/"
            f"v{CODEQL_VERSION}/{asset}"
        )
        with tempfile.TemporaryDirectory(prefix="pure-auto-codeql-") as temp_dir:
            archive = Path(temp_dir) / asset
            download(url, archive)
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(install_root)
    install_wrapper("codeql", executable)
    log(f"[ok] CodeQL {CODEQL_VERSION}: {executable}")


def ensure_jdtls(allow_download: bool) -> None:
    if shutil.which("jdtls"):
        log(f"[ok] Java language server: {shutil.which('jdtls')}")
        return
    if not allow_download:
        log("[warn] jdtls is missing; Java source analysis will use fallback tools.")
        return

    install_root = TOOLS_DIR / f"jdtls-{JDTLS_VERSION}"
    launcher = install_root / "bin" / ("jdtls.py" if platform.system() == "Windows" else "jdtls")
    if not launcher.exists():
        archive_name = f"jdt-language-server-{JDTLS_VERSION}-{JDTLS_BUILD}.tar.gz"
        url = f"https://download.eclipse.org/jdtls/milestones/{JDTLS_VERSION}/{archive_name}"
        with tempfile.TemporaryDirectory(prefix="pure-auto-codeql-") as temp_dir:
            archive = Path(temp_dir) / archive_name
            download(url, archive)
            install_root.mkdir(parents=True, exist_ok=True)
            with tarfile.open(archive, "r:gz") as bundle:
                bundle.extractall(install_root, filter="data")
    install_wrapper("jdtls", launcher)
    log(f"[ok] Eclipse JDT LS {JDTLS_VERSION}: {launcher}")


def ensure_lsp_mcp(allow_build: bool) -> None:
    existing = shutil.which("mcp-language-server")
    wrapper = venv_bin_dir() / (
        "mcp-language-server.cmd"
        if platform.system() == "Windows"
        else "mcp-language-server"
    )
    if existing and Path(existing).resolve() != wrapper.resolve():
        install_wrapper("mcp-language-server", Path(existing))
        log(f"[ok] LSP MCP bridge: {existing}")
        return

    require("go", "Install Go from https://go.dev/doc/install.")
    go_path = subprocess.run(
        ["go", "env", "GOPATH"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    executable = Path(go_path) / "bin" / (
        "mcp-language-server.exe"
        if platform.system() == "Windows"
        else "mcp-language-server"
    )
    if not executable.exists():
        if not allow_build:
            raise RuntimeError(
                "mcp-language-server is missing and automatic build was disabled."
            )
        run(
            [
                "go",
                "install",
                f"{MCP_LANGUAGE_SERVER_MODULE}@{MCP_LANGUAGE_SERVER_VERSION}",
            ]
        )
    if not executable.exists():
        raise RuntimeError(f"Go installed bridge was not found at {executable}")
    install_wrapper("mcp-language-server", executable)
    log(f"[ok] LSP MCP bridge: {executable}")


def ensure_mcp_runtime() -> None:
    require("node", "Install Node.js 18 or newer.")
    require("npm", "npm is installed together with Node.js.")
    run(["npm", "--prefix", "tools/mcp_runtime", "ci"])
    run(["npm", "--prefix", "tools/mcp_runtime", "audit", "--audit-level=moderate"])
    run(["npm", "--prefix", "tools/mcp_ripgrep", "ci"])
    run(["npm", "--prefix", "tools/mcp_ripgrep", "run", "build"])
    run(["npm", "--prefix", "tools/mcp_ripgrep", "test"])


def ensure_configuration() -> None:
    destination = ROOT / "config" / "keys.toml"
    if destination.exists():
        log(f"[ok] Configuration preserved: {destination}")
        return
    shutil.copy2(ROOT / "config" / "keys.example.toml", destination)
    log(f"[ok] Created configuration template: {destination}")


def run_doctor() -> int:
    return run(
        ["uv", "run", "pure-auto-codeql", "doctor"],
        check=False,
    ).returncode


def run_lsp_smoke() -> None:
    run(["uv", "run", "python", "scripts/smoke_source_lsp.py"])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Only run diagnostics.")
    parser.add_argument(
        "--no-codeql-download",
        action="store_true",
        help="Require an existing CodeQL installation.",
    )
    parser.add_argument(
        "--no-jdtls-download",
        action="store_true",
        help="Do not download Eclipse JDT LS when missing.",
    )
    parser.add_argument(
        "--no-lsp-mcp-build",
        action="store_true",
        help="Do not build the pinned LSP-MCP bridge when missing.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    os.chdir(ROOT)
    require("uv", "Install uv from https://docs.astral.sh/uv/.")

    if args.check:
        return run_doctor()

    log("PureAutoCodeQL cross-platform bootstrap")
    run(["uv", "sync", "--frozen"])
    ensure_codeql(not args.no_codeql_download)
    ensure_mcp_runtime()
    ensure_lsp_mcp(not args.no_lsp_mcp_build)
    ensure_jdtls(not args.no_jdtls_download)

    if not shutil.which("clangd"):
        log("[warn] clangd is missing; install LLVM for C/C++ semantic analysis.")
    if not shutil.which("pyright-langserver"):
        raise RuntimeError("pyright-langserver is missing after uv sync.")

    ensure_configuration()
    status = run_doctor()
    run_lsp_smoke()
    log("Bootstrap finished. Add an LLM API key to config/keys.toml if needed.")
    return status


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"[error] {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
