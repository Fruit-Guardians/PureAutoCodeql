"""CodeQL utilities (package split from the former module).

Public surface is unchanged: every name previously importable from
``pure_auto_codeql.utils.codeql`` is re-exported here.
"""

from .database import (
    _format_db_error,
    is_database_error,
    resolve_codeql_database_root,
    validate_codeql_database,
)
from .execution import (
    execute_codeql_query,
    run_query_and_decode_to_text,
    run_simple_query,
)
from .language import detect_language_from_query, normalize_language
from .qlpack import create_temporary_qlpack, gen_codeql_lock_yml
from .results import (
    count_dataflow_paths,
    is_empty_result,
    parse_codeql_results,
    save_query_to_persistent_dir,
)
from .subprocess_runner import _stream_subprocess

__all__ = [
    "_format_db_error",
    "_stream_subprocess",
    "count_dataflow_paths",
    "create_temporary_qlpack",
    "detect_language_from_query",
    "execute_codeql_query",
    "gen_codeql_lock_yml",
    "is_database_error",
    "is_empty_result",
    "normalize_language",
    "parse_codeql_results",
    "resolve_codeql_database_root",
    "run_query_and_decode_to_text",
    "run_simple_query",
    "save_query_to_persistent_dir",
    "validate_codeql_database",
]
