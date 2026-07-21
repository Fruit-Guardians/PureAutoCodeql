# Package Architecture Migration

PureAutoCodeQL is moving runtime application code toward the
`pure_auto_codeql` namespace while preserving legacy imports during the
migration window.

## Canonical Namespace Targets

| Current surface | Canonical target | Current status |
| --- | --- | --- |
| `Analyze.py` | `pure_auto_codeql.cli` | Legacy shim kept |
| `agents` | `pure_auto_codeql.agents` | Migrated |
| shared CLI/API workflow helpers | `pure_auto_codeql.application` | Introduced |
| LLM configuration imports | `pure_auto_codeql.configuration` | Canonical facade |
| repo path helpers | `pure_auto_codeql.paths` | Introduced (`get_repo_root`, `prompts_dir`) |
| `Information` | `pure_auto_codeql.information` | Migrated (top-level shim removed) |
| `prompts` | `pure_auto_codeql.prompts` | Migrated (top-level shim removed; `.md` assets co-located) |
| `utils` | `pure_auto_codeql.utils` | Migrated (top-level shim removed) |
| `tools` | `pure_auto_codeql.tools` | Migrated Python modules (top-level py shim removed; `tools/mcp_ripgrep` stays at repo root) |
| `services` | `pure_auto_codeql.services` | Migrated (top-level shim removed) |
| `core` | `pure_auto_codeql.core` | Migrated (top-level shim removed) |
| `api` | `pure_auto_codeql.api` | Migrated (top-level shim removed; docs remain under pure_auto_codeql/api and mirrored paths if needed) |
| `config` (LLM provider impl) | `pure_auto_codeql.config` | Migrated; repo-root `config/` only holds keys + README |
| `pure_auto_codeql.configuration` | facade over `pure_auto_codeql.config` | Canonical import for app code |

## Compatibility Surface

The following legacy imports must continue to work until a later proposal
explicitly removes them:

- `python Analyze.py ...`
- `from Analyze import ...`
- `python config.py ...` (keys stay at repo-root `config/`); prefer `pure_auto_codeql.configuration`
- `uvicorn pure_auto_codeql.api.server:app` (preferred; legacy `api.server` removed)

Compatibility shims should be quiet by default. Tests should cover both the new
canonical imports and supported legacy imports before a module is moved.

## Migration Order

1. Keep `Analyze.py` as a CLI compatibility shim and use
   `pure_auto_codeql.cli` for packaged command code.
2. Add shared application services under `pure_auto_codeql.application`.
3. Route CLI/API overlapping workflows through the shared services.
4. Use `pure_auto_codeql.configuration` as the canonical LLM configuration
   facade while keeping `config` and `python config.py` available for legacy
   callers.
5. Move low-risk modules in small batches, leaving top-level wrappers.
6. Update CI and documentation after each batch.

## Configuration Boundary

Runtime application code should import LLM configuration helpers from
`pure_auto_codeql.configuration`:

```python
from pure_auto_codeql.configuration import get_llm_config, LLMRole
```

Repository-root `config/` holds only user-facing secrets (`keys.toml`) and
`keys.example.toml` / README. Implementation code lives under
`pure_auto_codeql.config`. Prefer:

```python
from pure_auto_codeql.configuration import get_llm_config, LLMRole
```

`python config.py ...` remains a thin launcher for the configuration CLI.


## Internal Import Convention

Runtime code under `pure_auto_codeql/` should import siblings via the
canonical namespace (for example `from pure_auto_codeql.utils.case import ...`),
not removed flat package names. Flat top-level runtime packages have been deleted.

## Verification

Run these checks before committing package-architecture changes:

```bash
uv run pytest -q
uv run python -m compileall -q Analyze.py pure_auto_codeql
uv lock --check
```

For the MCP helper:

```bash
cd tools/mcp_ripgrep
npm audit --audit-level=high
npm run build
```

## Top-level layout after migration

Runtime packages live only under `pure_auto_codeql/`. Repository root keeps:

- `Analyze.py` / `config.py` CLI script shims
- `config/keys.example.toml` (+ local `keys.toml`, gitignored)
- `tools/mcp_ripgrep` for the Node MCP build
- `docs/`, `resources/`, `scripts/`, `projects/case-template/`, `test/`
