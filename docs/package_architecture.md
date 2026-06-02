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

The `config/` package remains a compatibility surface for older scripts that
use `from config import ...`. The root-level `config.py` file is only a legacy
script shim for `python config.py ...`; normal `import config` resolves to the
`config/` package in this repository.

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
