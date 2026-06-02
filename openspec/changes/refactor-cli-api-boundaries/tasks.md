## 1. Design
- [ ] 1.1 Document the target CLI command tree and backward-compatible aliases.
- [ ] 1.2 Define shared request/result/error objects for CLI and API workflows.
- [ ] 1.3 Identify current behavior that must remain compatible.
- [ ] 1.4 Decide the package namespace needed to avoid the `agents` top-level
      package conflict with `openai-agents`.

## 2. Implementation
- [ ] 2.1 Extract command handlers from `Analyze.py` into testable functions.
- [ ] 2.2 Move local modules into the chosen namespace or otherwise remove the
      dependency/package-name collision.
- [ ] 2.3 Add console entry points and subcommands while preserving existing
      top-level flags.
- [ ] 2.4 Route API task creation through the same service-layer validation.
- [ ] 2.5 Update API and README documentation.

## 3. Verification
- [ ] 3.1 Add CLI parser and handler tests.
- [ ] 3.2 Add API integration tests for shared validation paths.
- [ ] 3.3 Run full regression suite and document migration notes.
