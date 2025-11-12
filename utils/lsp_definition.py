#!/usr/bin/env python3
"""
LSP Definition Lookup Utility

Provides functionality to query CodeQL function definitions using LSP protocol.
Reuses existing HotCodeQL LSP engine instance without restarting the service.
"""

import json
import time
import re
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from urllib.parse import unquote, urlparse

from tools.lsp_codeql import HotCodeQL, write_msg


class LSPDefinitionLookup:
    """
    Query CodeQL function definitions using LSP textDocument/definition protocol.
    
    This class provides methods to:
    - Find symbol positions in code text
    - Query definition locations via LSP
    - Extract complete function definition blocks
    """
    
    def __init__(self, engine: HotCodeQL):
        """
        Initialize with an existing HotCodeQL engine instance.
        
        Args:
            engine: Running HotCodeQL LSP engine instance
        """
        self.engine = engine
        self._request_id_counter = 10000
        self._codeql_stdlib_path = None  # Cache for CodeQL standard library path
    
    def _next_request_id(self) -> int:
        """Generate unique request ID for LSP messages."""
        self._request_id_counter += 1
        return self._request_id_counter
    
    def _get_codeql_stdlib_path(self) -> Optional[Path]:
        """
        Get the path to CodeQL standard library.
        
        Returns:
            Path to CodeQL stdlib, or None if not found
        """
        if self._codeql_stdlib_path is not None:
            return self._codeql_stdlib_path
        
        try:
            # Try to find codeql home directory
            result = subprocess.run(
                ["codeql", "resolve", "qlpacks"],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            if result.returncode == 0:
                # Parse output to find standard library path
                # Format: "codeql/java-all (/path/to/codeql/java/ql/lib)"
                for line in result.stdout.splitlines():
                    if "codeql/" in line and "(" in line:
                        # Extract path from parentheses
                        match = re.search(r'\(([^)]+)\)', line)
                        if match:
                            path = Path(match.group(1).strip())
                            # Go up to find the root codeql directory (may be named codeql or codeql-main)
                            while not path.name.startswith("codeql") and path.parent != path:
                                path = path.parent
                            if path.name.startswith("codeql"):
                                self._codeql_stdlib_path = path
                                return self._codeql_stdlib_path
        except Exception:
            pass
        
        # Fallback: try common locations (cross-platform)
        import platform
        
        common_paths = [
            Path.home() / "codeql",  # User home directory (all platforms)
        ]
        
        # Add platform-specific paths
        if platform.system() == "Windows":
            # Windows common locations
            common_paths.extend([
                Path("C:/codeql"),
                Path("C:/Program Files/codeql"),
                Path("C:/tools/codeql"),
                Path.home() / "AppData" / "Local" / "codeql",
            ])
        else:
            # Unix/Linux/Mac common locations
            common_paths.extend([
                Path("/opt/codeql"),
                Path("/usr/local/codeql"),
                Path("/usr/local/bin/codeql"),
            ])
        
        for path in common_paths:
            if path.exists() and (path / "java" / "ql" / "lib").exists():
                self._codeql_stdlib_path = path
                return self._codeql_stdlib_path
        
        return None
    
    def _python_search_files(
        self,
        symbol_name: str,
        lang_dir: Path
    ) -> Optional[Dict[str, Any]]:
        """
        Cross-platform Python implementation to search for symbol definitions.
        
        This replaces grep for Windows compatibility.
        
        Args:
            symbol_name: Name of symbol to search for
            lang_dir: Directory to search in
            
        Returns:
            Dict with file_path, start_line, end_line, and code, or None if not found
        """
        # Compile regex patterns
        patterns = [
            re.compile(rf"^\s*class\s+{symbol_name}\s"),  # class definition
            re.compile(rf"^\s*predicate\s+{symbol_name}\s*\("),  # predicate definition
            re.compile(rf"^\s*(private|override|abstract|final)\s+{symbol_name}\s+{symbol_name}\s*\("),  # method
            re.compile(rf"^\s*[a-zA-Z_][a-zA-Z0-9_]*\s+{symbol_name}\s*\("),  # function with return type
        ]
        
        # Search all .qll and .ql files
        for file_path in lang_dir.rglob("*.qll"):
            # Skip upgrade scripts
            if "upgrades" in file_path.parts:
                continue
            
            try:
                content = file_path.read_text(encoding="utf-8", errors="ignore")
                lines = content.splitlines()
                
                for line_num, line_text in enumerate(lines, 1):
                    # Check if any pattern matches
                    for pattern in patterns:
                        if pattern.match(line_text):
                            # Found a match! Extract the definition block
                            block = self.extract_definition_block(
                                file_path,
                                line_num - 1,  # Convert to 0-indexed
                                0
                            )
                            
                            if block:
                                code_lines = [text for _, text in block]
                                return {
                                    "file_path": str(file_path),
                                    "start_line": block[0][0],
                                    "end_line": block[-1][0],
                                    "code": "\n".join(code_lines)
                                }
            except Exception:
                # Skip files that can't be read
                continue
        
        return None
    
    def _grep_search_definition(
        self, 
        symbol_name: str, 
        language: str = "java"
    ) -> Optional[Dict[str, Any]]:
        """
        Search for symbol definition in CodeQL stdlib.
        
        Strategy:
        1. Try ripgrep (rg) first - fastest and cross-platform (Windows/Linux/Mac)
        2. Fallback to pure Python implementation if ripgrep not available
        
        Args:
            symbol_name: Name of symbol to search for
            language: Target language (java, python, cpp, etc.)
            
        Returns:
            Dict with file_path, start_line, end_line, and code, or None if not found
        """
        stdlib_path = self._get_codeql_stdlib_path()
        if not stdlib_path:
            return None
        
        # Determine search directory based on language
        lang_dir = stdlib_path / language / "ql" / "lib"
        if not lang_dir.exists():
            # Try alternative paths
            alt_paths = [
                stdlib_path / language / "ql" / "src",
                stdlib_path / f"{language}-all" / "ql" / "lib",
            ]
            for alt in alt_paths:
                if alt.exists():
                    lang_dir = alt
                    break
            else:
                return None
        
        # Try ripgrep first (faster and cross-platform)
        try:
            # Search for class or predicate definitions using ripgrep
            # ripgrep works on Windows/Linux/Mac
            patterns = [
                f"^\\s*class\\s+{symbol_name}\\s",
                f"^\\s*predicate\\s+{symbol_name}\\s*\\(",
                f"^\\s*(private|override|abstract|final)\\s+{symbol_name}\\s+{symbol_name}\\s*\\(",
                f"^\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s+{symbol_name}\\s*\\(",
            ]
            
            for pattern in patterns:
                result = subprocess.run(
                    [
                        "rg",  # Works on all platforms when in PATH
                        "--line-number",  # Show line numbers
                        "--no-heading",   # Don't group by file
                        "--glob", "*.qll", # Only search .qll files
                        "--glob", "!upgrades/", # Exclude upgrades directory
                        pattern,
                        str(lang_dir)
                    ],
                    capture_output=True,
                    text=True,
                    timeout=10
                )
                
                if result.returncode == 0 and result.stdout:
                    lines = result.stdout.strip().splitlines()
                    for line in lines:
                        # Format: /path/to/file.qll:123:code
                        match = re.match(r"^([^:]+):(\d+):", line)
                        if match:
                            file_path = Path(match.group(1))
                            line_num = int(match.group(2))
                            
                            block = self.extract_definition_block(
                                file_path, 
                                line_num - 1,
                                0
                            )
                            
                            if block:
                                code_lines = [text for _, text in block]
                                return {
                                    "file_path": str(file_path),
                                    "start_line": block[0][0],
                                    "end_line": block[-1][0],
                                    "code": "\n".join(code_lines)
                                }
        except (FileNotFoundError, subprocess.TimeoutExpired):
            # ripgrep not available or timed out, fall back to Python implementation
            pass
        
        # Fallback: Use pure Python implementation (cross-platform)
        return self._python_search_files(symbol_name, lang_dir)
    
    def find_symbol_in_text(self, text: str, symbol: str) -> Optional[Tuple[int, int]]:
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
    
    def query_definition(
        self, 
        line: int, 
        character: int, 
        timeout: float = 5.0
    ) -> Optional[Dict[str, Any]]:
        """
        Query definition location using LSP textDocument/definition.
        
        Args:
            line: 0-indexed line number
            character: 0-indexed character position
            timeout: Maximum wait time in seconds
            
        Returns:
            Definition location dict or None if not found
        """
        req_id = self._next_request_id()
        
        # Send textDocument/definition request
        write_msg(self.engine.proc, {
            "jsonrpc": "2.0",
            "id": req_id,
            "method": "textDocument/definition",
            "params": {
                "textDocument": {"uri": self.engine.uri},
                "position": {"line": line, "character": character}
            }
        })
        
        # Wait for response
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                payload = self.engine.q.get(timeout=0.2)
                if not isinstance(payload, dict):
                    continue
                
                # Handle workspace/configuration requests
                if payload.get("method") == "workspace/configuration":
                    items = payload.get("params", {}).get("items", [])
                    write_msg(self.engine.proc, {
                        "jsonrpc": "2.0",
                        "id": payload["id"],
                        "result": [{} for _ in items]
                    })
                    continue
                
                # Check if this is our response
                if payload.get("id") == req_id:
                    result = payload.get("result")
                    if result:
                        return result
                    return None
                    
            except Exception:
                continue
        
        return None
    
    def extract_definition_block(
        self, 
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
        # for keywords like 'class', 'predicate', 'private', etc.
        def_start_line = start_line
        for i in range(start_line, max(0, start_line - 10), -1):
            line = lines[i].strip()
            # Look for definition keywords
            if any(keyword in line for keyword in ['class ', 'predicate ', 'private ', 'override ', 'abstract ', 'final ']):
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
                    if any(keyword in stripped for keyword in ['class ', 'predicate ', 'private ', 'override ']):
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
    
    def get_function_definition(
        self, 
        function_name: str, 
        timeout: float = 5.0
    ) -> Optional[Dict[str, Any]]:
        """
        Get complete function definition including location and code.
        
        This is the main entry point that combines all functionality:
        1. Find symbol in current query file (or inject it temporarily)
        2. Query definition location via LSP
        3. Extract complete definition block
        
        Strategy:
        - First try to find the symbol in the existing query
        - If not found, inject multiple reference patterns to help LSP resolve:
          * Direct usage: functionName
          * Type declaration: Type t
          * Method call: obj.functionName()
        - Wait longer for LSP to process the changes
        
        Args:
            function_name: Name of function to look up
            timeout: Maximum wait time for LSP query
            
        Returns:
            Dict with keys:
                - file_path: Path to definition file
                - start_line: Starting line number (1-indexed)
                - end_line: Ending line number (1-indexed)
                - code: Complete function definition code
            Returns None if lookup fails
        """
        # Step 1: Find symbol in current query file
        try:
            query_text = self.engine.query_file.read_text(
                encoding="utf-8", errors="replace"
            )
        except Exception:
            return None
        
        position = self.find_symbol_in_text(query_text, function_name)
        original_content = None  # Track if we modified the file
        
        # If symbol not found, temporarily inject it to enable LSP lookup
        if not position:
            # Create multiple reference patterns to help LSP resolve the symbol
            # This handles different cases: functions, classes, methods, etc.
            temp_references = [
                f"\n// === Temporary LSP lookup references ===",
                f"// Direct reference: {function_name}",
                f"// Type usage: {function_name} temp",
                f"// Method call pattern: x.{function_name}()",
                f"// Class instantiation: new {function_name}()",
                f"// === End temporary references ===",
                ""
            ]
            temp_query = query_text + "\n".join(temp_references)
            
            # Find the injected symbol (try the direct reference line)
            position = self.find_symbol_in_text(temp_query, function_name)
            
            if position:
                # Update the query file temporarily
                try:
                    original_content = query_text
                    self.engine.query_file.write_text(temp_query, encoding="utf-8")
                    
                    # Notify LSP of the change
                    self.engine.version += 1
                    from tools.lsp_codeql import write_msg
                    write_msg(self.engine.proc, {
                        "jsonrpc": "2.0",
                        "method": "textDocument/didChange",
                        "params": {
                            "textDocument": {"uri": self.engine.uri, "version": self.engine.version},
                            "contentChanges": [{"text": temp_query}]
                        }
                    })
                    
                    # Wait longer for LSP to process and re-index
                    # LSP needs time to resolve references in the standard library
                    import time
                    time.sleep(1.5)
                    
                except Exception:
                    return None
            else:
                return None
        
        line, character = position
        
        # Step 2: Query definition via LSP
        definition = self.query_definition(line, character, timeout)
        if not definition:
            # If injection was used, try alternative positions
            if original_content is not None:
                # Try other reference patterns in the injected code
                for pattern in [f"{function_name} temp", f"x.{function_name}()", f"new {function_name}()"]:
                    alt_position = self.find_symbol_in_text(temp_query, function_name)
                    if alt_position and alt_position != (line, character):
                        alt_line, alt_char = alt_position
                        definition = self.query_definition(alt_line, alt_char, timeout)
                        if definition:
                            break
            
            if not definition:
                return None
        
        # Handle both single definition and list of definitions
        def_item = definition if isinstance(definition, dict) else (
            definition[0] if isinstance(definition, list) and definition else None
        )
        if not def_item:
            return None
        
        # Step 3: Extract file path and range
        uri = def_item.get("uri") or def_item.get("targetUri")
        if not uri:
            return None
        
        parsed = urlparse(uri)
        if parsed.scheme != "file":
            return None
        
        # Convert URI to filesystem path
        if parsed.netloc and parsed.netloc != "localhost":
            file_path = Path(f"//{parsed.netloc}{parsed.path}")
        else:
            file_path = Path(unquote(parsed.path))
        
        # Get range information
        range_info = (
            def_item.get("range")
            or def_item.get("targetSelectionRange")
            or def_item.get("targetRange")
        )
        if not range_info:
            return None
        
        start = range_info.get("start", {})
        start_line = start.get("line")
        start_char = start.get("character", 0)
        
        if start_line is None:
            return None
        
        block_lines = self.extract_definition_block(
            file_path, start_line, start_char
        )
        if not block_lines:
            return None
        
        code_lines = [text for _, text in block_lines]
        result = {
            "file_path": str(file_path),
            "start_line": block_lines[0][0],
            "end_line": block_lines[-1][0],
            "code": "\n".join(code_lines)
        }
        
        # Cleanup: restore original content if we modified the file
        if original_content is not None:
            try:
                self.engine.query_file.write_text(original_content, encoding="utf-8")
                self.engine.version += 1
                from tools.lsp_codeql import write_msg
                write_msg(self.engine.proc, {
                    "jsonrpc": "2.0",
                    "method": "textDocument/didChange",
                    "params": {
                        "textDocument": {"uri": self.engine.uri, "version": self.engine.version},
                        "contentChanges": [{"text": original_content}]
                    }
                })
            except Exception:
                pass
        
        return result
    
    def get_function_definition_with_fallback(
        self, 
        function_name: str, 
        language: str = "java",
        timeout: float = 5.0
    ) -> Optional[Dict[str, Any]]:
        """
        Get function definition with fallback to grep search.
        
        Strategy (optimized):
        1. First try grep search (fast and reliable)
        2. If grep fails, try LSP as fallback (slower but can find local definitions)
        
        Args:
            function_name: Name of function to look up
            language: Target language (java, python, cpp, etc.)
            timeout: Maximum wait time for LSP query
            
        Returns:
            Dict with file_path, start_line, end_line, and code, or None if not found
        """
        # Try grep first (faster and more reliable for stdlib)
        print(f"   [Grep搜索] 在标准库中搜索 '{function_name}'...")
        result = self._grep_search_definition(function_name, language)
        
        if result:
            print(f"✅ [Grep搜索] 在标准库中找到 '{function_name}' 的定义")
            return result
        
        print(f"   [Grep搜索失败] 未在标准库中找到，尝试LSP查询...")
        
        # Fallback to LSP (for local definitions in query file)
        # NOTE: LSP is commented out for now as it's rarely useful
        # result = self.get_function_definition(function_name, timeout)
        # if result:
        #     print(f"✅ [LSP查询] 找到 '{function_name}' 的定义")
        #     return result
        
        print(f"❌ [查询失败] 未找到 '{function_name}' 的定义")
        return None
