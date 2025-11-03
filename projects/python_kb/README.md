# QL Generation Knowledge Base Demo

This demo shows how to organise a lightweight, model-facing knowledge base that supports Python CodeQL path-query generation. The goal is to let a model retrieve exactly the context it needs, produce syntactically correct queries, run `codeql query compile`, and self-fix when errors appear.

## Directory layout

- `templates/path_problem_skeleton.ql`  
  Fixed `@kind path-problem` skeleton. Only fill the placeholders and the predicates inside `module VulnConfig`.
- `knowledge_base/modules.json`  
  Public SDK modules (`semmle/python/dataflow/new/*`) with import paths, exported symbols, notes, and tags.
- `knowledge_base/helpers.json`  
  Reusable helper predicates with signature, description, example, and source query id.
- `knowledge_base/templates.json`  
  Scenario templates. Each record now lists the helper/module ids it depends on (`helpers_required`, `modules_required`).
- `knowledge_base/cases.json`  
  Successful CVE queries and the helper ids they use.
- `knowledge_base/errors.json`  
  Compiler diagnostic patterns → likely cause → minimal fix steps.
- `tools/retrieve.py`  
  CLI retrieval helper that loads the JSON data, filters by tags, and prints either text or JSON summaries.

## Retrieval workflow

1. **Gather context**
   - Extract tags from the CVE or diff description, for example `["django", "redirect"]`.
   - Run `python demo/tools/retrieve.py --tags django redirect`.  
     - The script always prepends core modules (`DataFlow`, `TaintTracking`, `RemoteFlowSources`) so the import list is never missing.  
     - Use `--sections modules helpers` to narrow categories.  
     - Use `--limit 3` to cap items per section (`0` means no cap).  
     - Use `--format json` for machine-readable output.
   - From the output, build the prompt in the order `skeleton → modules → helpers → templates/cases`.
2. **Generate & compile**
   - Ask the model to fill the skeleton with the retrieved helpers, modules, and notes.  
   - Compile with `codeql query compile demo/queries/<name>.ql`.
3. **Self-fix**
   - On compilation failure, parse the error text, match it against `errors.json["pattern"]`, and feed the paired `cause`/`fix` instructions back to the model for a focused repair.
4. **Keep data fresh**
   - When you add new helpers/templates/cases, append a JSON record with a stable `id`, a `tags` array, and concise summary.  
   - If a new compiler error arises, log its regex pattern and the fix that worked.

## Prompt efficiency guidelines

- **Tag-driven retrieval** keeps prompts compact by only loading items tagged for the current scenario.
- **Core module default** guarantees the model always sees `import python`, `DataFlow`, `TaintTracking`, and `RemoteFlowSources`.
- **Two-tier summaries**: JSON entries stay short; keep long explanations elsewhere and fetch them only when necessary.
- **Template bundles**: use `modules_required` + `helpers_required` from `templates.json` to pull everything a template needs in one step.
- **Error-on-demand**: only add error remediation info after the compiler surfaces an issue.

## Extending the demo

- Parse additional queries to enrich `helpers.json`, `templates.json`, and `cases.json`.
- Wrap `tools/retrieve.py` as a function/API to integrate retrieval directly into the generation pipeline.
- Add regression expectations (databases + result sets) to validate behaviour, not just syntax.
- Track SDK version metadata in the JSON records so outdated references can be flagged during upgrades.

This setup is intentionally small—you can migrate the JSON data into SQLite or a vector index and keep the same retrieval contract once the vocabulary grows.***
