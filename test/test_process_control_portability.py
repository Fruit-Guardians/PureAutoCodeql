from __future__ import annotations

from typing import Any, cast

import pure_auto_codeql.services.process_control as process_control


class _WindowsProcess:
    pid = 4242

    def __init__(self) -> None:
        self.killed = False

    def poll(self):
        return None

    def wait(self, timeout=None):
        return 0

    def kill(self):
        self.killed = True


def test_windows_termination_uses_taskkill_process_tree(monkeypatch) -> None:
    commands: list[list[str]] = []
    process = _WindowsProcess()
    monkeypatch.setattr(process_control.os, "name", "nt")
    monkeypatch.setattr(
        process_control.subprocess,
        "run",
        lambda command, **kwargs: commands.append(command),
    )

    process_control.terminate_process_tree(cast(Any, process))

    assert commands == [["taskkill", "/PID", "4242", "/T", "/F"]]
    assert process.killed is False
