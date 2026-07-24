"""Task-scoped subprocess tracking and process-tree termination."""

from __future__ import annotations

import contextlib
import contextvars
import os
import signal
import subprocess
import threading
from collections.abc import Iterator
from typing import Optional


def process_group_popen_kwargs() -> dict:
    if os.name == "nt":
        return {"creationflags": getattr(subprocess, "CREATE_NEW_PROCESS_GROUP")}
    return {"start_new_session": True}


def terminate_process_tree(process: subprocess.Popen, grace_seconds: float = 2.0) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        # terminate() only stops the direct Python helper and can leave the
        # CodeQL launcher/Java language-server grandchildren alive. taskkill
        # provides the process-tree semantics expected by cancellation.
        try:
            subprocess.run(
                ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=max(5.0, grace_seconds),
            )
            process.wait(timeout=max(1.0, grace_seconds))
            return
        except (OSError, subprocess.TimeoutExpired):
            process.kill()
            return
    try:
        os.killpg(os.getpgid(process.pid), signal.SIGTERM)
        process.wait(timeout=grace_seconds)
        return
    except (OSError, subprocess.TimeoutExpired):
        pass

    try:
        os.killpg(os.getpgid(process.pid), signal.SIGKILL)
        process.wait(timeout=max(1.0, grace_seconds))
    except (OSError, subprocess.TimeoutExpired):
        process.kill()


class ProcessScope:
    """Tracks every child process created while a task is active."""

    def __init__(self) -> None:
        self._processes: set[subprocess.Popen] = set()
        self._lock = threading.Lock()

    def register(self, process: subprocess.Popen) -> None:
        with self._lock:
            self._processes.add(process)

    def unregister(self, process: subprocess.Popen) -> None:
        with self._lock:
            self._processes.discard(process)

    def terminate_all(self) -> None:
        with self._lock:
            processes = list(self._processes)
        for process in processes:
            terminate_process_tree(process)
            self.unregister(process)


_CURRENT_SCOPE: contextvars.ContextVar[Optional[ProcessScope]] = contextvars.ContextVar(
    "pure_auto_codeql_process_scope",
    default=None,
)


@contextlib.contextmanager
def bind_process_scope(scope: ProcessScope) -> Iterator[ProcessScope]:
    token = _CURRENT_SCOPE.set(scope)
    try:
        yield scope
    finally:
        _CURRENT_SCOPE.reset(token)


def register_process(process: subprocess.Popen) -> None:
    scope = _CURRENT_SCOPE.get()
    if scope is not None:
        scope.register(process)


def unregister_process(process: subprocess.Popen) -> None:
    scope = _CURRENT_SCOPE.get()
    if scope is not None:
        scope.unregister(process)


__all__ = [
    "ProcessScope",
    "bind_process_scope",
    "process_group_popen_kwargs",
    "register_process",
    "terminate_process_tree",
    "unregister_process",
]
