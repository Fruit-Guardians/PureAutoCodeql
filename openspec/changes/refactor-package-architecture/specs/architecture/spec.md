## ADDED Requirements

### Requirement: Canonical Package Namespace
The system SHALL use `pure_auto_codeql` as the canonical namespace for new
runtime application modules while keeping documented compatibility wrappers for
legacy top-level imports during the migration window.

#### Scenario: Canonical import path
- **WHEN** application code imports a moved runtime module
- **THEN** the import uses the `pure_auto_codeql` namespace as the canonical path
- **AND** packaging metadata includes the module in installed distributions

#### Scenario: Legacy import compatibility
- **WHEN** existing scripts import a supported legacy top-level module path
- **THEN** the import continues to resolve to equivalent behavior during the
  migration window

### Requirement: Configuration Module Clarity
The system SHALL provide a clear canonical configuration module boundary that
removes ambiguity between the root `config.py` module and the `config/` package.

#### Scenario: Runtime configuration import
- **WHEN** CLI, API, or services need runtime configuration helpers
- **THEN** they import from the documented canonical configuration module
- **AND** legacy configuration imports remain covered by compatibility tests

### Requirement: Automated Architecture Checks
The system SHALL include automated checks that detect package import regressions,
OpenSpec drift, and selected Python quality issues before changes are pushed.

#### Scenario: CI validation
- **WHEN** CI runs on a pushed branch or pull request
- **THEN** it validates OpenSpec changes, Python tests, import compatibility, and
  the configured quality gates
