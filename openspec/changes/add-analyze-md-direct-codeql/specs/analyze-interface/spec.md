## ADDED Requirements
### Requirement: MD File Direct CodeQL Generation
The system SHALL provide a command-line interface to directly generate CodeQL queries from a specified MD file containing vulnerability descriptions.

#### Scenario: MD file direct processing
- **WHEN** user provides `--md-file` parameter with a valid MD file path
- **THEN** the system SHALL read the MD file content
- **AND** extract vulnerability description from the file
- **AND** directly invoke CodeQL generation tools without full case analysis
- **AND** output generated CodeQL query and execution results

#### Scenario: MD file validation
- **WHEN** user provides an invalid or non-existent MD file path
- **THEN** the system SHALL display appropriate error message
- **AND** exit without processing

### Requirement: MD File Parameter Integration
The Analyze.py command-line interface SHALL support the new `--md-file` parameter alongside existing options.

#### Scenario: Parameter parsing
- **WHEN** user runs `python Analyze.py --md-file /path/to/vulnerability.md`
- **THEN** the system SHALL parse the MD file parameter
- **AND** initiate direct CodeQL generation mode
- **AND** bypass normal case analysis workflow

#### Scenario: Parameter exclusivity
- **WHEN** user provides `--md-file` parameter
- **THEN** the system SHALL not allow simultaneous use of `--case`, `--list`, or `--validate` parameters
- **AND** shall display clear usage instructions if conflicting parameters are detected
