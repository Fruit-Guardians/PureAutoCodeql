"""Language detection and normalization for CodeQL queries."""

from typing import Optional


def detect_language_from_query(query_content: str) -> str:
    content = (query_content or '').lower()
    if 'import java' in content:
        return 'java'
    if 'import python' in content:
        return 'python'
    if (
        'import cpp' in content
        or 'import cplusplus' in content
        or 'import c ' in content
        or '\nimport c\n' in content
    ):
        return 'cpp'
    return 'java'


def normalize_language(language: Optional[str]) -> str:
    lang = (language or 'java').strip().lower()
    if lang in {'c', 'cplusplus', 'cpp'}:
        return 'cpp'
    if lang in {'python', 'java'}:
        return lang
    return 'java'
