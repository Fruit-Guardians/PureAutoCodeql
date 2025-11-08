# Python CodeQL Generation Pipeline (RAG + Planner, 2025-11)

This note captures how the Python CodeQL generation stack now works after the latest refactor: knowledge-base plumbing, prompt contracts, preflight guards, and the new placeholders that flow through the prompts.

## 1. Knowledge Base Infrastructure
- `services/knowledge_base/base.py` defines the `LanguageKnowledgeBase` protocol and a caching `KnowledgeBaseFactory`.
- `services/knowledge_base/python.py`
  - Mirrors `resources/codeql/python` into `projects/python_kb/` for MCP-friendly access.
  - Exposes `build_context()` returning:
    - `kb_directory_index`, `kb_suggestions` (legacy textual hints).
    - `kb_structured_context` – JSON block containing modules/helpers/templates/cases/errors + tags.
    - `kb_reference_snippets` – top matching case queries (first N lines) so the generator can quote real code.
  - Registering a new language only requires implementing the interface and calling `KnowledgeBaseFactory.register(...)`.

## 2. Prompt / Planner updates
- `prompts/python_template_ql.md`
  - Forces `Plan Summary` to spell out Sources/Sinks/Sanitizers/Helpers/Scope + cite Requirement or KB.
  - Provides a locked-down skeleton (`DataFlow::ParameterNode`, `DataFlow::CallCfgNode`, `Flow::PathGraph`, etc.)—the model only fills designated `<...>` regions.
- `prompts/codeql_erroranalyze.md`
  - Response = “错误快照 + 新的修复计划 + 完整 QL”.
  - Bundles a Python new-dataflow API cheat sheet and forbids patching the previous code.
- `prompts/codeql_generate.md`
  - Injects `[[KB_STRUCTURED_CONTEXT]]` (JSON) and `[[KB_REFERENCE_SNIPPETS]]` (QL excerpts) ahead of the planner instructions so the LLM can anchor to real examples.

## 3. CodeQLComposeTool Enhancements
- Uses `KnowledgeBaseFactory` to lazily hydrate the language-specific KB; `build_placeholder_map` now threads all `kb_*` fields into both generator and error prompts.
- `_preflight_validate_query()` runs before LSP:
  - Global checks: metadata, `Flow` module, `select`, `import Flow::PathGraph`.
  - Python-specific checks:
    - **Required tokens**: `import python`, `import semmle.python.dataflow.new.DataFlow`, `import ...TaintTracking`, `DataFlow::ParameterNode`, `DataFlow::CallCfgNode`, `DataFlow::AttrRead`, `getLocation().getFile()`.
    - **Blacklists**: `MethodCall`, naked `ParameterNode`, direct `getFile()`, etc. Any hit short-circuits the iteration with a descriptive error.
- `_lsp_and_execute()` encapsulates LSP diagnostics + query execution so the main loop only orchestrates “preflight → LSP → run”.

## 4. Placeholder Reference
| Placeholder | Meaning |
| --- | --- |
| `[[KB_DIRECTORY_INDEX]]` | human summary of mirrored resources |
| `[[KB_SUGGESTED_ITEMS]]` | legacy textual hints (modules/helpers/templates/errors) |
| `[[KB_STRUCTURED_CONTEXT]]` | JSON payload with modules/helpers/templates/cases/errors/tags |
| `[[KB_REFERENCE_SNIPPETS]]` | code snippets copied from matching case queries |
| `[[RELEVANT_TAGS]]` | matched tags |

## 5. Future Work
- Implement `LanguageKnowledgeBase` for other languages (Java, Go, …) to reuse the same pipeline.
- Emit structured requirements from `core/pipeline.py` instead of free-form text so prompts can drop more heuristics.
- Cache successful queries (SARIF hits + metadata) into `output/case_cache/` and eventually feed them back into the KB after verification.
