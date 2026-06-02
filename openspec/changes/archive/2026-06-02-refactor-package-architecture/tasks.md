## 1. Design And Inventory
- [x] 1.1 Inventory top-level runtime modules and identify canonical namespace targets.
- [x] 1.2 Document compatibility imports that must remain available.
- [x] 1.3 Decide the staged migration order for `api`, `core`, `services`, `utils`, `tools`, and configuration modules.

## 2. Package Architecture
- [x] 2.1 Add canonical `pure_auto_codeql` modules for shared application services.
- [x] 2.2 Move selected low-risk modules into the namespace with compatibility shims.
- [x] 2.3 Resolve the `config.py` and `config/` ambiguity with a clearer canonical module and legacy wrapper.
- [x] 2.4 Update packaging metadata so installed commands include the canonical modules.

## 3. CLI/API Shared Behavior
- [x] 3.1 Route CLI project import through the shared service layer.
- [x] 3.2 Route API project import and analysis validation through the same service layer.
- [x] 3.3 Preserve legacy CLI commands and legacy Python imports.

## 4. Quality Gates
- [x] 4.1 Add import-compatibility tests for canonical and legacy module paths.
- [x] 4.2 Add CLI/API regression tests for shared validation behavior.
- [x] 4.3 Add or update CI checks for OpenSpec validation and the selected Python quality tools.
- [x] 4.4 Update README/docs with the package layout and migration notes.

## 5. Verification
- [x] 5.1 Run the full Python, OpenSpec, and MCP helper regression suite.
- [x] 5.2 Confirm `python Analyze.py --help` and `pure-auto-codeql --help` both work.
- [x] 5.3 Confirm no unrelated worktree changes are staged.
