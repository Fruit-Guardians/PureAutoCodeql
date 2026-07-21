"""
Utility modules for vulnerability analysis tools.

This package contains common utility functions:
- io: File I/O operations (read_json_text, write_analysis_output)
- java: Java-specific utilities (find_path_from_java_file)
- codeql: CodeQL execution utilities (execute_codeql_query, parse_codeql_results)
"""

from .codeql import execute_codeql_query, parse_codeql_results

__all__ = [
    'execute_codeql_query',
    'parse_codeql_results',
]
