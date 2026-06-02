# Security Policy

PureAutoCodeQL is a security research tool. Please report vulnerabilities in
the project itself responsibly.

## Supported Versions

The `main` branch is the actively maintained development line.

## Reporting a Vulnerability

If you find a security issue in this repository, please do not publish a public
proof of concept before maintainers have had time to respond.

Recommended report contents:

- Affected component and version or commit
- Impact and expected attacker capability
- Reproduction steps with the smallest possible input
- Relevant logs, stack traces, or screenshots
- Suggested fix, if you already have one

Use GitHub private vulnerability reporting when available. If it is not
available for this repository, open a minimal public issue that asks maintainers
for a private contact channel and avoid including exploit details.

## Handling Secrets

Do not commit real API keys or service tokens. Local key files such as
`config/keys.toml` and `config/keys*.toml` are intentionally ignored. If a key
was committed accidentally, rotate or revoke it immediately and consider Git
history cleanup.

## Operational Notes

- Keep the API bound to `127.0.0.1` unless remote access is required.
- Set `API_AUTH_TOKEN` before exposing the API outside a trusted machine.
- Treat imported source trees and build scripts as untrusted code.
- Prefer isolated environments for C/C++ project builds.
