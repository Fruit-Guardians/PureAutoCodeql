"""Legacy tools package.

Python tool implementations live under ``pure_auto_codeql.tools``.
``tools/mcp_ripgrep`` remains at the repository root for Node build tooling.
"""

from pure_auto_codeql.tools import CodeQLComposeTool

__all__ = ["CodeQLComposeTool"]
