# api Specification

## Purpose
Define the FastAPI behavior for project management, analysis task creation,
shared validation, and user-facing error handling.
## Requirements
### Requirement: Shared Workflow Validation
The API SHALL use the same project import, analysis request validation, and
error reporting rules as the CLI workflow services by delegating overlapping
workflow behavior to shared application services instead of route-local helpers.

#### Scenario: Unsafe import path
- **WHEN** an API request tries to import a source path outside the configured
  import root without explicit opt-in
- **THEN** the API rejects the request with the same policy reason used by the
  CLI/service validation layer

#### Scenario: Shared analysis validation
- **WHEN** an API request starts an analysis task for an invalid or unsafe case
  identifier
- **THEN** the API returns the service-layer validation error mapped to the
  appropriate HTTP status code
- **AND** equivalent CLI validation paths remain covered by regression tests
