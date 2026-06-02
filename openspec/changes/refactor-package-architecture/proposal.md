## Why
The project now has a safer CLI entry point and namespaced local agents, but
many core modules still live at the repository root. The remaining top-level
packages (`api`, `core`, `services`, `utils`, `tools`) and the `config.py` /
`config/` name split make packaging, imports, tests, and future refactors harder
than necessary.

## What Changes
- Move application code toward the `pure_auto_codeql` package namespace while
  preserving compatibility shims for legacy imports and scripts.
- Resolve the `config.py` and `config/` ambiguity with a documented compatibility
  path and a clearer runtime configuration module boundary.
- Introduce a small shared application-service layer for project import and
  analysis task validation so CLI and API routes can share behavior without
  importing route modules.
- Add quality gates for the modernized structure, including lint/static checks
  where practical, import-compatibility tests, and CI/OpenSpec validation.
- Refresh documentation for the package layout, migration path, and local
  development checks.

## Impact
- Affected specs: architecture, cli, api
- Affected code: `pure_auto_codeql/*`, `Analyze.py`, `api/*`, `core/*`,
  `services/*`, `utils/*`, `config.py`, `config/*`, tests, CI, and docs
- Risk: Medium-high. This touches import paths and packaging boundaries. The
  implementation must be staged with shims and regression tests before removing
  any legacy import surface.
