# api Specification

## Purpose
Define the FastAPI behavior for project management, analysis task creation,
shared validation, and user-facing error handling.
## Requirements
### Requirement: Shared Workflow Validation
The API SHALL use the same project import, analysis request validation, and
error reporting rules as the CLI workflow services.

#### Scenario: Unsafe import path
- **WHEN** an API request tries to import a source path outside the configured
  import root without explicit opt-in
- **THEN** the API rejects the request with the same policy reason used by the
  CLI/service validation layer
