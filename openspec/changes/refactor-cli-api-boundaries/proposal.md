## Why
The current CLI entry point, API routes, and service orchestration still carry
older project structure decisions. They work, but the command surface is large,
some modes are mixed into a single parser, and API/CLI behavior is not always
described by the same contracts.

## What Changes
- Introduce a subcommand-based CLI layout such as `analyze`, `import`, `md`,
  `providers`, `serve`, and `doctor`.
- Move command handlers behind reusable application services so CLI and API
  paths share the same validation and error model.
- Define structured result and error objects for long-running analysis tasks.
- Move top-level packages into a project namespace before publishing console
  entry points, because the current local `agents` package conflicts with the
  `openai-agents` dependency's top-level package.
- Keep compatibility shims for the current `Analyze.py --case ...` style during
  a migration window.

## Impact
- Affected specs: cli, api, analysis-orchestration
- Affected code: `Analyze.py`, `agents/*`, `api/*`, `core/*`,
  `utils/project_importer.py`, packaging metadata, documentation and tests
- Risk: Medium. The change touches public command behavior and API task
  contracts, so it should be implemented behind tests and migration notes.
