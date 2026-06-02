# Contributing

Thanks for helping improve PureAutoCodeQL.

## Development Setup

```bash
git clone https://github.com/Fruit-Guardians/PureAutoCodeql.git
cd PureAutoCodeql
uv sync
```

Build the MCP ripgrep tool when you need agent-side code search:

```bash
chmod +x build_mcp.sh
./build_mcp.sh
```

## Before Opening a Pull Request

Please run the focused checks that match your change. For broad changes, run:

```bash
uv run pytest -q
uv lock --check
uv run python -m compileall -q Analyze.py api core services utils agents tools
```

For documentation-only changes, at minimum check Markdown links and run:

```bash
git diff --check
```

## Pull Request Guidance

- Keep the change focused and explain why it is needed.
- Include reproduction steps for bug fixes.
- Include sample input or output when behavior changes.
- Do not commit secrets, generated local reports, or private target projects.
- Follow existing module boundaries and local coding style.

## Commit Message Examples

```text
fix: harden project import path validation
feat: add source-sink fallback query mode
docs: refresh API quickstart
test: cover unsafe case id rejection
```
