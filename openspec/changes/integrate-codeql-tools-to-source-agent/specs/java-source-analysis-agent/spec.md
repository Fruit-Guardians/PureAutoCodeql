## ADDED Requirements
### Requirement: CodeQL Tool Integration
Java Source Analysis Agent SHALL integrate CodeQL Generator and Runner tools to perform dynamic source analysis instead of using hardcoded prompts.

#### Scenario: CodeQL Query Generation
- **WHEN** analyzing Java sources for potential source points
- **THEN** the agent SHALL use CodeQL Generator Tool to create appropriate queries based on CVE analysis

#### Scenario: CodeQL Query Execution
- **WHEN** a CodeQL query is generated for source analysis
- **THEN** the agent SHALL use CodeQL Runner Tool to execute the query against the Java codebase

#### Scenario: Dynamic Analysis Workflow
- **WHEN** the agent receives CVE analysis and Java file paths
- **THEN** it SHALL generate and execute CodeQL queries to identify source points instead of using static prompts

## MODIFIED Requirements
### Requirement: Source Analysis Method
The analyze_java_sources method SHALL be modified to use CodeQL tools for analysis instead of relying on hardcoded prompts.

#### Scenario: Tool-based Analysis
- **WHEN** the agent analyzes Java sources
- **THEN** it SHALL use CodeQL Generator Tool to create source-specific queries
- **AND** it SHALL use CodeQL Runner Tool to execute these queries
- **AND** it SHALL process the results to identify potential source points

## REMOVED Requirements
### Requirement: Hardcoded Prompt Analysis
**Reason**: Replaced with dynamic CodeQL-based analysis for better accuracy and flexibility
**Migration**: The build_prompt method will be simplified to remove hardcoded analysis prompts, as analysis will now be performed by CodeQL tools
