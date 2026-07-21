"""Legacy utils package.

Implementation lives under ``pure_auto_codeql.utils``. This top-level package
re-exports the public API for compatibility.
"""

from pure_auto_codeql.utils import execute_codeql_query, parse_codeql_results

__all__ = [
    "execute_codeql_query",
    "parse_codeql_results",
]
