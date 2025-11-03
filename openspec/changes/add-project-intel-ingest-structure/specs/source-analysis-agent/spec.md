## ADDED Requirements
### Requirement: Case Workspace Layout
The system SHALL validate that every selected case workspace under `projects/<case-id>/` provides the required subdirectories (`source_code/`, `queries/`, `db/`, `inputs/`, `intel/`) before running any agents.

#### Scenario: Directory Validation
- **WHEN** the user invokes a multi-agent workflow with `--case <case-id>`
- **THEN** the runtime SHALL confirm that each required subdirectory exists and surface a descriptive error if any are missing

#### Scenario: Switching Between Cases
- **WHEN** an analyst chooses a different case-id on the CLI
- **THEN** the system SHALL resolve all resources (inputs, databases, source trees) relative to that case root, without reading stray files from the repository root

### Requirement: Intelligence Cache Handling
The system SHALL store GHSA/NVD fetch artifacts inside `projects/<case-id>/intel/` and reuse them to avoid redundant network calls.

#### Scenario: Initial Intelligence Fetch
- **WHEN** the `intel/` directory does not yet contain GHSA/NVD results for the selected CVE
- **THEN** the orchestrator SHALL invoke the fetch utilities, persist raw JSON, formatted summaries, and the normalized bundle, and record a `sources_failed` list when a source cannot be retrieved

#### Scenario: Reuse Cached Intelligence
- **WHEN** cached intelligence files already exist
- **THEN** the orchestrator SHALL reuse them, skip the network requests, and note that cached data was applied

## MODIFIED Requirements
### Requirement: Multi-agent Workflow Execution
The system SHALL extend `run_multi_agent_analysis` to include the intelligence ingest stage and to source inputs from the case workspace layout.

#### Scenario: Agent Execution Order
- **WHEN** the workflow executes
- **THEN** it SHALL run in the order: intelligence ingest → CVE analysis → Sink analysis → Source analysis → report generation

#### Scenario: Case Input Resolution
- **WHEN** preparing prompts for downstream agents
- **THEN** the workflow SHALL pull JSON/diff files from `projects/<case-id>/inputs/` and pass the intelligence bundle to the CVE agent

#### Scenario: Failure Handling
- **WHEN** any stage fails
- **THEN** the workflow SHALL log the failure, continue executing remaining agents when possible, and, if intelligence fetches failed, record the missing sources for inclusion in the final report
