# Project Context

## Purpose
PureAutoCodeQL is a multi-agent CodeQL vulnerability analysis pipeline for CVE
research. It imports CVE-oriented source projects, builds or locates CodeQL
databases, runs AI-assisted source/sink/query generation workflows, and exposes
the workflow through both a CLI and FastAPI service.

## Tech Stack
- Python 3.13 with FastAPI, Pydantic, pytest, uv, and setuptools packaging.
- CodeQL CLI and generated CodeQL query/resources for Java, Python, and C/C++.
- LLM integrations through OpenAI-compatible providers and LangChain/LangGraph.
- A TypeScript MCP helper under `tools/mcp_ripgrep`.

## Project Conventions

### Code Style
- Prefer existing module patterns and small, testable functions.
- Keep backward-compatible CLI and import shims during migration windows.
- Avoid introducing new top-level packages that can collide with dependencies.
- Use `rg` for repository search and pytest for Python regression coverage.

### Architecture Patterns
- `pure_auto_codeql/` is the project namespace for new package code.
- `Analyze.py` is a legacy compatibility entry point; packaged CLI code belongs
  under `pure_auto_codeql.cli`.
- CLI and API workflows should share validation and service-layer behavior where
  the user-facing operation is the same.
- API routes should remain thin and delegate project import, task management, and
  analysis orchestration to reusable helpers or services.

### Testing Strategy
- Add focused unit tests for parser/handler/service behavior.
- Add HTTP-level FastAPI tests when API routes are expected to enforce shared
  validation or security policy.
- Run `uv run pytest -q`, `uv run python -m compileall -q ...`,
  `uv lock --check`, npm build/audit for MCP helpers, and OpenSpec validation
  before committing broad changes.

### Git Workflow
- Main branch is pushed to `origin/main` for this maintenance flow.
- Use concise conventional-style commit subjects such as `refactor: ...`,
  `fix: ...`, and `chore: ...`.
- Do not revert unrelated user changes; inspect dirty files before staging.

## Domain Context
- A case workspace lives under `projects/<case_id>/` and is expected to contain
  `source_code`, `db`, `inputs`, and `intel` directories.
- CVE metadata lives in `inputs` and may include JSON, diff, patch, and extra
  context files.
- CodeQL database creation may be skipped or may require language/build-specific
  behavior, especially for C/C++ projects.
- LLM provider configuration can come from environment variables and
  `config/keys.toml`; real secrets must never be committed.

## Important Constraints
- Preserve legacy commands such as `python Analyze.py --case ...`.
- Preserve existing API routes unless a proposal explicitly marks a breaking
  change and migration path.
- API project import must reject unsafe paths and API-supplied build commands
  unless explicitly enabled by configuration.
- The local project must not rely on a top-level `agents` package name because
  `openai-agents` also exposes one.

## External Dependencies
- GitHub and GitHub Actions for repository hosting and CI.
- CodeQL CLI for database creation and query execution.
- Optional LLM providers: DeepSeek, SiliconFlow, Zhipu, Kimi, Gemini, or custom
  OpenAI-compatible providers.
- NVD/GHSA information fetchers for CVE intelligence.
