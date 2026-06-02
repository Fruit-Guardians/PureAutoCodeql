## ADDED Requirements

### Requirement: Subcommand-Based CLI
The system SHALL provide a subcommand-based CLI that groups analysis, project
import, Markdown query generation, provider management, server startup, and
environment diagnostics into distinct commands.

#### Scenario: Run case analysis
- **WHEN** a user runs the future packaged CLI command
  `pure-auto-codeql analyze --case CVE-2021-21985`
- **THEN** the system starts the same analysis workflow as the current
  `Analyze.py --case CVE-2021-21985` command

#### Scenario: Preserve legacy command
- **WHEN** a user runs `python Analyze.py --case CVE-2021-21985`
- **THEN** the system continues to accept the command during the migration
  window and reports equivalent results

### Requirement: Package Namespace Safety
The system SHALL avoid top-level package names that collide with third-party
dependencies before publishing console entry points.

#### Scenario: Local agent modules
- **WHEN** the packaged CLI imports local agent modules
- **THEN** it resolves the project's own modules instead of the third-party
  `openai-agents` package

### Requirement: Environment Diagnostics
The system SHALL provide an environment diagnostics command that checks required
local tools, model configuration, and generated helper artifacts without
starting an analysis task.

#### Scenario: Missing CodeQL
- **WHEN** CodeQL CLI is not available in `PATH`
- **THEN** the diagnostics command reports the missing tool and prints an
  actionable installation hint
