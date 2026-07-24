"""Smoke-test the packaged CodeQL query LSP and CLI fallback."""

from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from pure_auto_codeql.services.lsp_service import CodeQLLSPService

QUERY = """\
import python

from Function function
select function
"""


def run_smoke(*, timeout: float = 90.0) -> None:
    """Run both syntax-validation backends against a temporary query pack."""

    with tempfile.TemporaryDirectory(prefix="pure-auto-codeql-lsp-") as temp_dir:
        pack_root = Path(temp_dir)
        (pack_root / "qlpack.yml").write_text(
            """\
name: pure-auto-codeql/lsp-smoke
version: 1.0.0
dependencies:
  codeql/python-all: "*"
""",
            encoding="utf-8",
        )
        query_file = pack_root / "smoke.ql"
        query_file.write_text(QUERY, encoding="utf-8")

        service = CodeQLLSPService(
            pack_root,
            query_file,
            init_timeout=timeout,
        )
        try:
            if not service.start():
                raise RuntimeError(service.last_error or "CodeQL query LSP failed to start")
            lsp_result = service.check_syntax(QUERY)
            if lsp_result.get("summary", {}).get("errors"):
                raise RuntimeError(f"CodeQL query LSP rejected smoke query: {lsp_result}")
            print("[OK] CodeQL query LSP")
        finally:
            service.stop()

        cli_result = service.check_syntax(QUERY)
        if cli_result.get("validator") != "codeql-cli":
            raise RuntimeError(f"CodeQL CLI fallback was not used: {cli_result}")
        if cli_result.get("summary", {}).get("errors"):
            raise RuntimeError(f"CodeQL CLI rejected smoke query: {cli_result}")
        print("[OK] CodeQL CLI syntax fallback")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--timeout",
        type=float,
        default=90.0,
        help="Maximum seconds to wait for the query LSP to initialize.",
    )
    args = parser.parse_args()
    run_smoke(timeout=args.timeout)


if __name__ == "__main__":
    main()
