#!/usr/bin/env python3
"""Perform real MCP and source-language LSP diagnostic calls."""

from __future__ import annotations

import argparse
import asyncio
import tempfile
from contextlib import AsyncExitStack
from pathlib import Path

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

from pure_auto_codeql.services.mcp_language_config import MCPLanguageConfigService

ROOT = Path(__file__).resolve().parents[1]
CASES = {
    "python": (
        ROOT / "test" / "golden" / "python_command_injection",
        "src/app.py",
    ),
    "java": (
        ROOT / "test" / "golden" / "java_path_traversal",
        "src/PathTraversal.java",
    ),
    "cpp": (
        ROOT / "test" / "golden" / "cpp_buffer_overflow",
        "src/app.c",
    ),
}


async def check_language(language: str) -> None:
    workspace, source_file = CASES[language]
    source_path = str((workspace / source_file).resolve())
    connection = MCPLanguageConfigService().get_language_server_config(
        language,
        str(workspace),
    )
    parameters = StdioServerParameters(
        command=connection["command"],
        args=connection["args"],
        cwd=connection.get("cwd"),
        env=connection.get("env"),
    )

    stage = "starting MCP bridge"
    print(f"[check] {language}: {stage}", flush=True)
    with tempfile.TemporaryFile(mode="w+", encoding="utf-8") as errlog:
        try:
            async with AsyncExitStack() as stack:
                read_stream, write_stream = await stack.enter_async_context(
                    stdio_client(parameters, errlog=errlog)
                )
                session = await stack.enter_async_context(
                    ClientSession(read_stream, write_stream)
                )
                stage = "initializing MCP session"
                async with asyncio.timeout(30):
                    await session.initialize()
                stage = "listing MCP tools"
                async with asyncio.timeout(15):
                    tools = {tool.name for tool in (await session.list_tools()).tools}
                required = {"definition", "references", "diagnostics", "hover"}
                missing = required - tools
                if missing:
                    raise RuntimeError(
                        f"{language}: bridge is missing tools: "
                        f"{', '.join(sorted(missing))}"
                    )
                stage = f"requesting diagnostics for {source_path}"
                async with asyncio.timeout(30):
                    result = await session.call_tool(
                        "diagnostics",
                        {"filePath": source_path},
                    )
                if result.isError:
                    detail = " ".join(
                        getattr(item, "text", str(item)) for item in result.content
                    )
                    raise RuntimeError(f"{language}: diagnostics failed: {detail}")
        except Exception as exc:
            errlog.seek(0)
            server_log = errlog.read().strip()
            detail = f"\nBridge stderr:\n{server_log}" if server_log else ""
            raise RuntimeError(f"{language}: failed while {stage}: {exc}{detail}") from exc

    print(f"[ok] {language}: MCP handshake and diagnostics call succeeded")


async def async_main(languages: list[str]) -> None:
    for language in languages:
        await check_language(language)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--language",
        action="append",
        choices=sorted(CASES),
        help="Language to verify; repeat as needed. Defaults to all.",
    )
    args = parser.parse_args()
    asyncio.run(async_main(args.language or list(CASES)))


if __name__ == "__main__":
    main()
