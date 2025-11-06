"""CodeQL 语法检查服务封装。主要是LSP"""

from __future__ import annotations

from contextlib import AbstractContextManager
from pathlib import Path
from typing import Any, Dict, Optional, Type

from services.lsp_service import CodeQLLSPService


class CodeQLSyntaxSession(AbstractContextManager):
    """LSP 语法检查会话，支持 with 语句管理生命周期。"""

    def __init__(
        self,
        pack_root: Path,
        *,
        lsp_cls: Type[CodeQLLSPService] = CodeQLLSPService,
    ) -> None:
        self._pack_root = Path(pack_root)
        self._lsp_cls = lsp_cls
        self._lsp: Optional[CodeQLLSPService] = None

    def __enter__(self) -> "CodeQLSyntaxSession":
        self.start()
        return self

    def __exit__(self, exc_type, exc, exc_tb) -> None:
        self.stop()

    # 手动控制时也可调用
    def start(self) -> None:
        if self._lsp is not None:
            return
        lsp = self._lsp_cls(str(self._pack_root))
        if not lsp.start():
            raise RuntimeError("Failed to start CodeQL LSP service for syntax checking")
        self._lsp = lsp

    def stop(self) -> None:
        if self._lsp:
            self._lsp.stop()
            self._lsp = None

    def check(self, codeql_query: str) -> Dict[str, Any]:
        if self._lsp is None:
            raise RuntimeError("Syntax session is not started")
        return self._lsp.check_syntax(codeql_query)


__all__ = ["CodeQLSyntaxSession"]
