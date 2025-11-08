## Why
- Multi-agent workflows require a consistent place for project-specific artifacts (source, CodeQL DB, CVE inputs). The current layout scatters files in repo root, making multi-case analysis brittle.
- GHSA/NVD fetch utilities exist but are not integrated. Analysts manually download data, leading to inconsistent intel and duplicated work.
- We need a documented ingest stage so both Java and Python orchestrators can reuse one structure and automatically hydrate intelligence before analysis.

## What Changes
- Introduce a standard `projects/<case-id>/` workspace layout covering `source_code/`, `db/`, `inputs/`, and `intel/`.
- Extend orchestrators to accept a `--case` (or equivalent) argument, load CVE JSON/diff from `inputs/`, and run `ghsa_fetch.py` / `nvd_info_fetch.py`, caching results under `intel/`.
- Normalize ingest output into a reusable structure that downstream agents can consume (e.g., populated CVE markdown, cached JSON, failure markers).
- Update specs to capture the new workspace requirement, ingest stage, caching policy, and final report consolidation.
- Refresh documentation so analysts know how to prepare new cases and manage API credentials.

## Impact
- Analysts follow a single folder template for new cases, reducing setup errors when switching projects.
- Automated GHSA/NVD fetching shortens onboarding; cached intel avoids redundant API calls and rate-limit surprises.
- Agents receive curated, de-duplicated context, improving final CVE summaries and preventing repeated content.
- New CLI surface (`--case`) adds minor learning curve; docs and default behavior mitigate confusion.
