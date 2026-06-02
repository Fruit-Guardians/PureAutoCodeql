## Context
The repository has moved local agents into `pure_auto_codeql.agents` and moved
the CLI implementation into `pure_auto_codeql.cli`. Other runtime modules still
remain as top-level packages, which makes installed package behavior harder to
reason about and increases collision risk with dependencies. The root-level
`config.py` module also coexists with the `config/` package, which is legal but
confusing for maintainers and import tooling.

## Goals
- Make `pure_auto_codeql` the canonical namespace for application code.
- Keep existing scripts, tests, and user commands working through compatibility
  shims during the migration.
- Replace route-to-helper coupling with a small shared service layer where CLI
  and API behavior overlaps.
- Add automated checks that catch import regressions and OpenSpec drift.

## Non-Goals
- Rewriting the analysis pipeline or agent prompts.
- Changing public API routes or response schemas unless a later proposal marks a
  breaking migration.
- Removing legacy `Analyze.py` support in this change.
- Replacing the provider configuration system wholesale.

## Approach
1. Create canonical modules under `pure_auto_codeql` for shared services and
   configuration boundaries.
2. Move or wrap root-level modules in small batches, starting with modules that
   already have good tests or limited dependencies.
3. Leave top-level compatibility modules/packages that re-export the canonical
   implementation and emit no noisy runtime warnings by default.
4. Add import tests for both canonical and legacy paths.
5. Update CI and docs after the new structure is stable.

## Risks
- Import cycles may appear when route modules, config modules, and services move
  at different speeds.
- Some examples or ad-hoc tests may import root modules directly.
- Packaging metadata may accidentally omit files if namespace moves are too
  aggressive.

## Verification
- `uv run pytest -q`
- `uv run python -m compileall -q Analyze.py api core services utils pure_auto_codeql tools`
- `uv lock --check`
- `npm audit --audit-level=high` and `npm run build` in `tools/mcp_ripgrep`
- OpenSpec validation in strict mode
- Explicit CLI smoke tests for `python Analyze.py --help`,
  `pure-auto-codeql --help`, and representative subcommands
