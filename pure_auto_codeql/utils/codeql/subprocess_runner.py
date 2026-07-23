"""Stream a subprocess, tee output to a log file, and capture stdout/stderr."""

import subprocess
import sys
from pathlib import Path
from typing import List, Tuple

from pure_auto_codeql.services.process_control import (
    process_group_popen_kwargs,
    register_process,
    terminate_process_tree,
    unregister_process,
)


def _stream_subprocess(
    cmd: List[str],
    log_file: Path,
    timeout: int,
) -> Tuple[int, str, str]:
    """执行子进程，实时将 stdout/stderr tee 到控制台与日志文件，返回 (returncode, stdout, stderr)。

    多个 CodeQL 执行函数共用此逻辑（此前每处都内联了一份相同的读取线程）。
    与原内联实现行为一致：subprocess.TimeoutExpired / FileNotFoundError 会向外传播，
    由调用方的 try/except 处理。
    """
    import threading

    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
        **process_group_popen_kwargs(),
    )
    register_process(process)

    stdout_lines: List[str] = []
    stderr_lines: List[str] = []

    def _pump(stream, sink: List[str], out) -> None:
        for line in stream:
            sink.append(line)
            print(line, end='', file=out, flush=True)
            with open(log_file, 'a', encoding='utf-8') as f:
                f.write(line)

    stdout_thread = threading.Thread(target=_pump, args=(process.stdout, stdout_lines, sys.stdout))
    stderr_thread = threading.Thread(target=_pump, args=(process.stderr, stderr_lines, sys.stderr))
    stdout_thread.start()
    stderr_thread.start()

    try:
        returncode = process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        terminate_process_tree(process)
        raise
    finally:
        stdout_thread.join(timeout=2)
        stderr_thread.join(timeout=2)
        unregister_process(process)

    return returncode, ''.join(stdout_lines), ''.join(stderr_lines)
