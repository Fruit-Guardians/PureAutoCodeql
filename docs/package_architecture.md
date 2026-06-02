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
| LLM configuration imports | `pure_auto_codeql.configuration` | Introduced as canonical facade |
| `api` | `pure_auto_codeql.api` | Planned staged migration |
| `core` | `pure_auto_codeql.core` | Planned staged migration |
| `services` | `pure_auto_codeql.services` | Planned staged migration |
| `utils` | `pure_auto_codeql.utils` | Planned staged migration |
| `tools` | `pure_auto_codeql.tools` | Planned staged migration |

## Compatibility Surface

The following legacy imports must continue to work until a later proposal
explicitly removes them:

- `python Analyze.py ...`
- `from Analyze import ...`
- `from config import ...`
- `from api import ...`
- `from core import ...`
- `from services import ...`
- `from utils import ...`
- `from tools import ...`

Compatibility shims should be quiet by default. Tests should cover both the new
canonical imports and supported legacy imports before a module is moved.

## Migration Order

1. Keep `Analyze.py` as a CLI compatibility shim and use
   `pure_auto_codeql.cli` for packaged command code.
2. Add shared application services under `pure_auto_codeql.application`.
3. Route CLI/API overlapping workflows through the shared services.
4. Use `pure_auto_codeql.configuration` as the canonical LLM configuration
   facade while keeping `config` available.
5. Move low-risk modules in small batches, leaving top-level wrappers.
6. Update CI and documentation after each batch.

## Verification

Run these checks before committing package-architecture changes:

```bash
uv run pytest -q
uv run python -m compileall -q Analyze.py api core services utils pure_auto_codeql tools
uv lock --check
openspec validate --all --strict
```

For the MCP helper:

```bash
cd tools/mcp_ripgrep
npm audit --audit-level=high
npm run build
```
