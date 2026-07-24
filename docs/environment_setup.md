# Environment setup

PureAutoCodeQL uses a reproducible Python environment plus pinned MCP
dependencies. The supported setup paths are:

- local development: `./scripts/bootstrap.sh`
- service deployment: `docker compose up --build`

## Local bootstrap

Install these host prerequisites first:

- Python 3.13 and `uv`
- CodeQL CLI in `PATH`
- Node.js 18 or newer
- Go (used once to build the pinned MCP-to-LSP bridge)

Then run:

```bash
# macOS / Linux
chmod +x scripts/bootstrap.sh
./scripts/bootstrap.sh

# Windows PowerShell
./scripts/bootstrap.ps1
```

The script performs the following idempotent operations:

1. installs the locked Python environment;
2. installs the pinned filesystem MCP dependency;
3. builds and tests the repository's ripgrep MCP server;
4. installs `mcp-language-server` at the repository-pinned release;
5. creates `config/keys.toml` from the template when absent;
6. runs the environment doctor.
7. starts all three language servers and performs a real `diagnostics` call.

The script never overwrites an existing `config/keys.toml`.

## Language services

| Language | Server | Installation |
| --- | --- | --- |
| Python | `pyright-langserver` | installed by `uv sync` |
| Java | Eclipse JDT LS (`jdtls`) | `brew install jdtls`, or use the distribution package for your OS |
| C/C++ | `clangd` | Xcode Command Line Tools, LLVM, or the system package manager |

The common `mcp-language-server` process receives the workspace and exact
language-server executable from PureAutoCodeQL. Set
`PURE_AUTO_CODEQL_LSP_MCP` to an absolute bridge path only when it is installed
outside `PATH`.

## Verification

```bash
uv run pure-auto-codeql doctor
uv run python scripts/smoke_source_lsp.py
npm --prefix tools/mcp_ripgrep test
npm --prefix tools/mcp_runtime audit --audit-level=moderate
```

`doctor` reports the resolved executable path for CodeQL, the MCP bridge, and
all three language servers. A missing language server produces an explicit
capability downgrade instead of a silent success.

## Updating MCP dependencies

The filesystem MCP package is pinned in
`tools/mcp_runtime/package.json` and locked by
`tools/mcp_runtime/package-lock.json`. The bridge release is pinned in
`scripts/bootstrap.py`. Update either dependency in a dedicated change and run:

```bash
npm --prefix tools/mcp_runtime ci
npm --prefix tools/mcp_runtime audit --audit-level=moderate
uv run pytest -q
```

Do not use `npm audit fix --force`; review and pin compatible transitive fixes.
