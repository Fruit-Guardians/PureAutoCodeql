"""Environment diagnostics for PureAutoCodeQL."""

from __future__ import annotations
from pure_auto_codeql.paths import get_repo_root

import os
import platform
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from pure_auto_codeql.configuration import list_available_providers


@dataclass
class CheckResult:
    name: str
    ok: bool
    detail: str
    hint: str = ""


def _run_version(command: list[str], timeout: int = 8) -> tuple[bool, str]:
    try:
        completed = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError:
        return False, "not found"
    except subprocess.TimeoutExpired:
        return False, "timeout"

    output = (completed.stdout or completed.stderr).strip().splitlines()
    detail = output[0] if output else f"exit {completed.returncode}"
    return completed.returncode == 0, detail


def _check_executable(name: str, command: list[str], hint: str) -> CheckResult:
    executable = shutil.which(command[0])
    if not executable:
        return CheckResult(name, False, f"{command[0]} not found in PATH", hint)

    ok, version = _run_version(command)
    detail = f"{version} ({executable})"
    return CheckResult(name, ok, detail, "" if ok else hint)


def _check_path(name: str, path: Path, hint: str) -> CheckResult:
    if path.exists():
        return CheckResult(name, True, str(path))
    return CheckResult(name, False, f"missing: {path}", hint)


def collect_diagnostics(project_root: Path | None = None) -> list[CheckResult]:
    root = project_root or get_repo_root()

    results = [
        CheckResult(
            "Python",
            sys.version_info >= (3, 13),
            f"{platform.python_version()} ({sys.executable})",
            "Use Python 3.13 or newer.",
        ),
        _check_executable("uv", ["uv", "--version"], "Install uv: https://github.com/astral-sh/uv"),
        _check_executable("CodeQL", ["codeql", "version"], "Install CodeQL CLI and add it to PATH."),
        _check_executable("Node.js", ["node", "--version"], "Install Node.js 18 or newer."),
        _check_executable("npm", ["npm", "--version"], "Install npm with Node.js."),
        _check_path("keys template", root / "config" / "keys.example.toml", "Restore config/keys.example.toml."),
    ]

    keys_file = root / "config" / "keys.toml"
    results.append(
        CheckResult(
            "keys.toml",
            keys_file.exists(),
            str(keys_file) if keys_file.exists() else "not configured",
            "Copy config/keys.example.toml to config/keys.toml or use environment variables.",
        )
    )

    mcp_dist = root / "tools" / "mcp_ripgrep" / "dist" / "index.js"
    results.append(
        CheckResult(
            "MCP ripgrep build",
            mcp_dist.exists(),
            str(mcp_dist) if mcp_dist.exists() else "dist/index.js missing",
            "Run ./scripts/build_mcp.sh or scripts\\build_mcp.bat.",
        )
    )

    java_home = os.environ.get("JAVA_HOME", "")
    if java_home:
        java_path = Path(java_home)
        results.append(
            CheckResult(
                "JAVA_HOME",
                java_path.exists(),
                java_home,
                "Set JAVA_HOME to a valid JDK path for Java LSP support.",
            )
        )
    else:
        results.append(
            CheckResult(
                "JAVA_HOME",
                False,
                "not set",
                "Set JAVA_HOME when analyzing Java projects with LSP support.",
            )
        )

    try:
        providers = list_available_providers()
        configured = [p["name"] for p in providers if p.get("has_api_key")]
        results.append(
            CheckResult(
                "LLM provider",
                bool(configured),
                ", ".join(configured) if configured else "no provider API key configured",
                "Configure config/keys.toml or provider environment variables.",
            )
        )
    except Exception as exc:  # pylint: disable=broad-except
        results.append(CheckResult("LLM provider", False, str(exc), "Check config/keys.toml syntax."))

    return results


def format_diagnostics(results: Iterable[CheckResult]) -> str:
    lines = ["PureAutoCodeQL environment diagnostics", ""]
    for result in results:
        marker = "OK" if result.ok else "WARN"
        lines.append(f"[{marker}] {result.name}: {result.detail}")
        if not result.ok and result.hint:
            lines.append(f"       hint: {result.hint}")
    return "\n".join(lines)


def run_doctor(project_root: Path | None = None) -> int:
    results = collect_diagnostics(project_root)
    print(format_diagnostics(results))
    return 0 if all(result.ok for result in results) else 1
