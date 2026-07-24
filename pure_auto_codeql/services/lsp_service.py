"""CodeQL query-language syntax service.

This is separate from the source-language LSPs exposed to agents.  It manages
the small HTTP wrapper around ``codeql execute language-server`` and falls back
to ``codeql query compile --check-only`` when that optional service is
unavailable.
"""

from __future__ import annotations

import socket
import subprocess
import sys
import threading
import time
from collections import deque
from pathlib import Path
from typing import Any

import requests

from pure_auto_codeql.services.codeql_environment import (
    find_codeql,
    missing_required_language_packs,
)
from pure_auto_codeql.services.process_control import (
    process_group_popen_kwargs,
    register_process,
    terminate_process_tree,
    unregister_process,
)
from pure_auto_codeql.utils.logger import get_logger

logger = get_logger(__name__)


def _available_loopback_port() -> int:
    """Reserve an ephemeral port briefly and return it.

    The child binds immediately afterwards.  There is an unavoidable small
    race, but this avoids deterministic collisions between concurrent runs.
    """

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


class CodeQLLSPService:
    """Manage CodeQL's QL language server with a CLI validation fallback."""

    def __init__(
        self,
        pack_root: Path | str | None = None,
        query_file: Path | str | None = None,
        *,
        port: int | None = None,
        init_timeout: float = 60.0,
        codeql: str | Path | None = None,
    ) -> None:
        self.pack_root = Path(pack_root).resolve() if pack_root else None
        self.query_file = Path(query_file).resolve() if query_file else None
        self.process: subprocess.Popen[str] | None = None
        self.port = port or _available_loopback_port()
        self.base_url = f"http://127.0.0.1:{self.port}"
        self.init_timeout = init_timeout
        self.codeql = find_codeql(codeql)
        self.last_error: str | None = None
        self._stderr_tail: deque[str] = deque(maxlen=80)
        self._stderr_thread: threading.Thread | None = None
        # This traffic is strictly loopback. Ignore OS/user proxy settings:
        # on macOS a system proxy can otherwise turn local health checks into
        # opaque HTTP 502 responses.
        self._http = requests.Session()
        self._http.trust_env = False

    @property
    def available(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def _drain_stderr(self, stream: Any) -> None:
        try:
            for line in iter(stream.readline, ""):
                clean = line.rstrip()
                if clean:
                    self._stderr_tail.append(clean)
        finally:
            stream.close()

    def _failure_detail(self, headline: str) -> str:
        tail = "\n".join(self._stderr_tail)
        return f"{headline}\n{tail}".strip()

    def _cleanup_process(self) -> None:
        process = self.process
        if process is None:
            return
        if process.poll() is None:
            terminate_process_tree(process)
        unregister_process(process)
        self.process = None

    def start(self) -> bool:
        """Start the HTTP/LSP helper.

        Returning ``False`` is non-fatal for callers: :meth:`check_syntax`
        remains usable through the authoritative CodeQL CLI fallback.
        """

        if self.available:
            return True
        self.last_error = None
        self._stderr_tail.clear()

        if self.pack_root is None or not self.pack_root.is_dir():
            self.last_error = f"CodeQL pack root does not exist: {self.pack_root}"
            logger.error(self.last_error)
            return False
        if self.query_file is None:
            self.last_error = "CodeQL query file was not provided"
            logger.error(self.last_error)
            return False
        if self.codeql is None:
            self.last_error = (
                "CodeQL CLI was not found. Install it, add it to PATH, or set "
                "PURE_AUTO_CODEQL_CODEQL to its executable path."
            )
            logger.error(self.last_error)
            return False
        missing_packs = missing_required_language_packs(self.codeql)
        if missing_packs:
            self.last_error = (
                "The standalone CodeQL CLI is installed, but the complete "
                f"bundle is missing packs: {', '.join(missing_packs)}. "
                "Run scripts/bootstrap.sh or install the official CodeQL bundle."
            )
            logger.error(self.last_error)
            return False

        cmd = [
            sys.executable,
            "-m",
            "pure_auto_codeql.tools.lsp_codeql",
            "--pack-root",
            str(self.pack_root),
            "--port",
            str(self.port),
            "--query-file",
            str(self.query_file),
            "--codeql",
            self.codeql,
            "--quiet-logs",
        ]
        logger.debug("启动 CodeQL 查询 LSP: %s", " ".join(cmd))

        try:
            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                **process_group_popen_kwargs(),
            )
            register_process(self.process)
            assert self.process.stderr is not None
            self._stderr_thread = threading.Thread(
                target=self._drain_stderr,
                args=(self.process.stderr,),
                daemon=True,
                name=f"codeql-lsp-stderr-{self.process.pid}",
            )
            self._stderr_thread.start()

            logger.info("等待 CodeQL 查询 LSP 启动... (超时: %.0f 秒)", self.init_timeout)
            deadline = time.monotonic() + self.init_timeout
            while time.monotonic() < deadline:
                if self.process.poll() is not None:
                    # Give the drain thread time to collect the final traceback.
                    self._stderr_thread.join(timeout=1)
                    self.last_error = self._failure_detail(
                        f"CodeQL 查询 LSP 在就绪前退出 (exit={self.process.returncode})"
                    )
                    logger.error(self.last_error)
                    self._cleanup_process()
                    return False
                try:
                    response = self._http.get(f"{self.base_url}/health", timeout=0.5)
                    if response.status_code == 200 and response.json().get("ok") is True:
                        logger.info("CodeQL 查询 LSP 已就绪")
                        return True
                except (requests.RequestException, ValueError):
                    pass
                time.sleep(0.2)

            self.last_error = self._failure_detail(f"CodeQL 查询 LSP 启动超时 ({self.init_timeout:.0f} 秒)")
            logger.error(self.last_error)
            self._cleanup_process()
            return False
        except Exception as exc:
            self.last_error = self._failure_detail(f"CodeQL 查询 LSP 启动失败: {exc}")
            logger.exception(self.last_error)
            self._cleanup_process()
            return False

    def _check_syntax_with_cli(self, codeql_code: str) -> dict[str, Any]:
        """Validate with CodeQL CLI when the long-running LSP is unavailable."""

        if self.query_file is None:
            return {"error": "CodeQL CLI fallback has no query file"}
        codeql = self.codeql or find_codeql()
        if not codeql:
            return {"error": "CodeQL CLI is unavailable; configure PATH or PURE_AUTO_CODEQL_CODEQL"}
        missing_packs = missing_required_language_packs(codeql)
        if missing_packs:
            return {"error": ("Complete CodeQL bundle required; missing packs: " + ", ".join(missing_packs))}

        self.query_file.parent.mkdir(parents=True, exist_ok=True)
        self.query_file.write_text(codeql_code, encoding="utf-8")
        cmd = [codeql, "query", "compile", "--check-only", str(self.query_file)]
        process: subprocess.Popen[str] | None = None
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                **process_group_popen_kwargs(),
            )
            register_process(process)
            stdout, stderr = process.communicate(timeout=120)
        except subprocess.TimeoutExpired:
            if process is not None:
                terminate_process_tree(process)
            return {"error": "CodeQL CLI syntax validation timed out after 120 seconds"}
        except OSError as exc:
            return {"error": f"CodeQL CLI syntax validation failed to start: {exc}"}
        finally:
            if process is not None:
                unregister_process(process)

        assert process is not None
        output = "\n".join(part.strip() for part in (stdout, stderr) if part.strip())
        if process.returncode == 0:
            return {
                "diagnostics": [],
                "summary": {"errors": 0, "warnings": 0, "information": 0, "hints": 0},
                "validator": "codeql-cli",
            }
        return {
            "diagnostics": [
                {
                    "severity": 1,
                    "message": output or f"codeql query compile exited {process.returncode}",
                    "source": "codeql-cli",
                }
            ],
            "summary": {"errors": 1, "warnings": 0, "information": 0, "hints": 0},
            "validator": "codeql-cli",
        }

    def check_syntax(self, codeql_code: str) -> dict[str, Any]:
        """Check QL syntax, falling back to ``codeql query compile``."""

        if self.available:
            try:
                response = self._http.post(
                    f"{self.base_url}/check",
                    json={"code": codeql_code},
                    timeout=30,
                )
                response.raise_for_status()
                result = response.json()
                if "error" not in result:
                    result.setdefault("validator", "codeql-lsp")
                    return result
                self.last_error = str(result["error"])
            except (requests.RequestException, ValueError) as exc:
                self.last_error = f"CodeQL 查询 LSP 检查失败: {exc}"
                logger.warning("%s；改用 CodeQL CLI 校验", self.last_error)

        return self._check_syntax_with_cli(codeql_code)

    def stop(self) -> None:
        """Stop the helper and its CodeQL child process group."""

        process = self.process
        if process is None:
            return
        if process.poll() is None:
            try:
                self._http.post(f"{self.base_url}/shutdown", timeout=3)
                process.wait(timeout=5)
            except (requests.RequestException, subprocess.TimeoutExpired):
                terminate_process_tree(process)
        self._cleanup_process()


__all__ = ["CodeQLLSPService"]
