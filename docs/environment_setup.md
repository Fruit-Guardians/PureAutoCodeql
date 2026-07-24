# Environment setup

PureAutoCodeQL uses a reproducible Python environment plus pinned MCP
dependencies. The supported setup paths are:

- local development: `./scripts/bootstrap.sh`
- service deployment: `docker compose up --build`

## Local bootstrap

Install these host prerequisites first:

- Python 3.13 and `uv`
- Node.js 18 or newer
- Go (used once to build the pinned MCP-to-LSP bridge)

The bootstrap downloads the repository-pinned complete CodeQL bundle when it
is not already available. The standalone CLI archive is not sufficient because
it omits the Java, Python, and C/C++ QL packs. The download is checked against
the SHA-256 published with the GitHub release. Pass `--no-codeql-download` only
in managed environments that provide the complete bundle separately.

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
6. runs the environment doctor;
7. starts all three source language servers and performs a real `diagnostics` call;
8. starts the packaged CodeQL query LSP and verifies the CLI fallback.

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

CodeQL is resolved from `PATH` by default. For package-manager, CI, or managed
installations where CodeQL is stored elsewhere, set
`PURE_AUTO_CODEQL_CODEQL` to the absolute `codeql`/`codeql.exe` path. The same
resolved executable is used by query LSP diagnostics, CLI validation fallback,
and environment checks.

## Verification

```bash
uv run pure-auto-codeql doctor
uv run python scripts/smoke_source_lsp.py
uv run python -m pure_auto_codeql.tools.smoke_codeql
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

## Docker Compose permissions

Before the first Compose start, create the bind-mounted directories as the
current user:

```bash
mkdir -p projects output config
docker compose up --build
```

The services run as a non-root UID/GID. Linux users with a nonstandard account
ID can set `PUID` and `PGID`; the defaults are `1000:1000`:

```bash
PUID="$(id -u)" PGID="$(id -g)" docker compose up --build
```

Temporary CodeQL packs use an in-container tmpfs, while final run artifacts are
persisted under the host `output/` directory.
