#!/bin/bash
# Compatibility wrapper — prefer ./scripts/build_mcp.sh
exec "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/scripts/build_mcp.sh" "$@"
