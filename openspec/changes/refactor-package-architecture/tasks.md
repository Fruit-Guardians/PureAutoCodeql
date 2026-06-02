## 1. Design And Inventory
- [ ] 1.1 Inventory top-level runtime modules and identify canonical namespace targets.
- [ ] 1.2 Document compatibility imports that must remain available.
- [ ] 1.3 Decide the staged migration order for `api`, `core`, `services`, `utils`, `tools`, and configuration modules.

## 2. Package Architecture
- [ ] 2.1 Add canonical `pure_auto_codeql` modules for shared application services.
- [ ] 2.2 Move selected low-risk modules into the namespace with compatibility shims.
- [ ] 2.3 Resolve the `config.py` and `config/` ambiguity with a clearer canonical module and legacy wrapper.
- [ ] 2.4 Update packaging metadata so installed commands include the canonical modules.

## 3. CLI/API Shared Behavior
- [ ] 3.1 Route CLI project import through the shared service layer.
- [ ] 3.2 Route API project import and analysis validation through the same service layer.
- [ ] 3.3 Preserve legacy CLI commands and legacy Python imports.

## 4. Quality Gates
- [ ] 4.1 Add import-compatibility tests for canonical and legacy module paths.
- [ ] 4.2 Add CLI/API regression tests for shared validation behavior.
- [ ] 4.3 Add or update CI checks for OpenSpec validation and the selected Python quality tools.
- [ ] 4.4 Update README/docs with the package layout and migration notes.

## 5. Verification
- [ ] 5.1 Run the full Python, OpenSpec, and MCP helper regression suite.
- [ ] 5.2 Confirm `python Analyze.py --help` and `pure-auto-codeql --help` both work.
- [ ] 5.3 Confirm no unrelated worktree changes are staged.
