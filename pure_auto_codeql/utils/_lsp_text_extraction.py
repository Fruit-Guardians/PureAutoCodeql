"""LSP 定义查找的纯文本处理助手。

从 LSPDefinitionLookup 抽出的无状态函数（不依赖实例状态），
便于独立测试与复用。
"""

from pathlib import Path
from typing import List, Optional, Tuple


def find_symbol_in_text(text: str, symbol: str) -> Optional[Tuple[int, int]]:
    """
    Find the first occurrence of a symbol in text.

    Args:
        text: Source code text to search
        symbol: Symbol name to find

    Returns:
        Tuple of (line_index, character_index) if found, None otherwise
    """
    for line_index, line_text in enumerate(text.splitlines()):
        column = line_text.find(symbol)
        if column != -1:
            # Return position in middle of symbol for better LSP resolution
            return (line_index, column + len(symbol) // 2)
    return None


def extract_definition_block(
    file_path: Path,
    start_line: int,
    start_char: int
) -> Optional[List[Tuple[int, str]]]:
    """
    Extract complete definition block with intelligent boundary detection.

    This method handles:
    - Class definitions (with brace matching)
    - Method/predicate definitions (with brace matching)
    - Single-line definitions (no braces)
    - Multi-line definitions with proper indentation

    Args:
        file_path: Path to source file
        start_line: 0-indexed starting line
        start_char: Starting character position

    Returns:
        List of (line_number, line_text) tuples, or None if extraction fails
    """
    try:
        file_text = file_path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return None

    lines = file_text.splitlines()
    if start_line >= len(lines):
        return None

    # First, try to find the start of the definition by looking backwards
    # for keywords like 'class', 'predicate', 'private', 'signature module', etc.
    def_start_line = start_line
    for i in range(start_line, max(0, start_line - 10), -1):
        line = lines[i].strip()
        # Look for definition keywords
        if any(keyword in line for keyword in ['signature module ', 'class ', 'predicate ', 'private ', 'override ', 'abstract ', 'final ']):
            def_start_line = i
            break
        # Stop if we hit a closing brace (previous definition)
        if line.startswith('}'):
            break

    block_lines = []
    brace_balance = 0
    started = False
    found_opening_brace = False

    line_idx = def_start_line
    search_char = start_char if line_idx == start_line else 0

    # Get the initial indentation level
    initial_indent = len(lines[def_start_line]) - len(lines[def_start_line].lstrip())

    while line_idx < len(lines):
        line_text = lines[line_idx]
        block_lines.append((line_idx + 1, line_text))

        # Scan characters for brace matching
        for ch in line_text[search_char:]:
            if ch == '{':
                brace_balance += 1
                started = True
                found_opening_brace = True
            elif ch == '}':
                if started:
                    brace_balance -= 1

            if started and brace_balance == 0:
                # Found matching closing brace
                return block_lines

        # If no braces found yet, check for single-line or indentation-based definitions
        if not found_opening_brace and line_idx > def_start_line:
            stripped = line_text.strip()
            current_indent = len(line_text) - len(line_text.lstrip())

            # Stop if we hit another definition at the same or lower indentation
            if stripped and current_indent <= initial_indent:
                if any(keyword in stripped for keyword in ['signature module ', 'class ', 'predicate ', 'private ', 'override ']):
                    # Remove the last line (it's a new definition)
                    block_lines.pop()
                    return block_lines if block_lines else None

            # Stop if we've collected enough lines for a single-line definition
            if len(block_lines) > 1 and stripped.endswith(';'):
                return block_lines

        line_idx += 1
        search_char = 0

        # Safety limit: don't extract more than 200 lines
        if len(block_lines) > 200:
            break

    # Return what we collected
    return block_lines if block_lines else None
