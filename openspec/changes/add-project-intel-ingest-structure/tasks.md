## 1. Workspace Layout
- [ ] 1.1 Add reusable project template directory (README instructions + sample tree)
- [ ] 1.2 Update docs explaining how to create `projects/<case-id>/` and required subfolders

## 2. Intel Ingest Pipeline
- [ ] 2.1 Implement ingest module to read CVE JSON/diff from `inputs/` and invoke GHSA/NVD fetchers
- [ ] 2.2 Persist fetch outputs and normalized bundle under `intel/`, honoring cache + failure markers
- [ ] 2.3 Extend orchestrators (Java & Python flows) to accept case selection and feed ingest results to agents

## 3. Validation & Tooling
- [ ] 3.1 Provide CLI/docs guidance for `--case` usage and credential handling
- [ ] 3.2 Add automated check or dry-run to verify project folder presence before analysis
