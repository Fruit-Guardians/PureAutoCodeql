#!/usr/bin/env python3
"""
LSP Definition Lookup Utility

Provides functionality to query CodeQL function definitions using LSP protocol.
Reuses existing HotCodeQL LSP engine instance without restarting the service.
"""

import json
import os
import time
import re
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple
from urllib.parse import unquote, urlparse

from pure_auto_codeql.tools.lsp_codeql import HotCodeQL, write_msg
from pure_auto_codeql.paths import get_repo_root


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
    
    def get_supported_languages(self) -> List[str]:
        """
        Get list of supported languages based on available CodeQL packs.
        Handles both versioned (e.g., java-all/7.7.2/) and non-versioned structures.
        
        Returns:
            List of supported language identifiers
        """
        stdlib_path = self._get_codeql_stdlib_path()
        if not stdlib_path:
            return []
        
        supported = []
        
        # Check for language-specific packs (versioned structure)
        language_patterns = [
            "java-all",    # Java
            "python-all",  # Python  
            "cpp-all",     # C++
            "javascript-all", # JavaScript
            "go-all",      # Go
            "csharp-all",  # C#
            "ruby-all",    # Ruby
            "swift-all",   # Swift
        ]
        
        for pattern in language_patterns:
            pack_dir = stdlib_path / pattern
            if pack_dir.exists():
                # Check if it has version subdirectories
                has_versions = any(d.is_dir() and d.name[0].isdigit() for d in pack_dir.iterdir())
                if has_versions or pack_dir.is_dir():
                    lang = pattern.replace("-all", "")
                    supported.append(lang)
        
        # Also check direct language directories (non-versioned)
        direct_langs = ["java", "python", "cpp", "javascript", "go", "csharp", "ruby", "swift"]
        for lang in direct_langs:
            if (stdlib_path / lang).exists():
                if lang not in supported:
                    supported.append(lang)
        
        return supported
    
    def _get_codeql_stdlib_path(self) -> Optional[Path]:
        """
        Get the path to CodeQL standard library.
        
        Returns:
            Path to CodeQL stdlib, or None if not found
        """
        if self._codeql_stdlib_path is not None:
            return self._codeql_stdlib_path
        
        try:
            config_path = get_repo_root() / "config" / "keys.toml"
            if config_path.exists():
                try:
                    import tomli as tomllib
                except ImportError:
                    try:
                        import tomllib  # Python >= 3.11
                    except ImportError:
                        import tomli as tomllib
                
                with open(config_path, 'rb') as f:
                    config = tomllib.load(f)
                    settings = config.get('settings', {})
                    configured_path = settings.get('codeql_stdlib_path')
                    
                    if configured_path:
                        path = Path(configured_path)
                        if path.exists():
                            # Verify it's a valid stdlib
                            valid_indicators = [
                                "java-all", "python-all", "cpp-all",
                                "dataflow", "util", "ssa",
                                "java", "python", "cpp"
                            ]
                            for indicator in valid_indicators:
                                if (path / indicator).exists():
                                    self._codeql_stdlib_path = path
                                    return self._codeql_stdlib_path
        except Exception:
            pass  # Continue to other methods if config loading fails
        env_path = os.getenv('CODEQL_STDLIB_PATH')
        if env_path:
            path = Path(env_path)
            if path.exists():
                self._codeql_stdlib_path = path
                return self._codeql_stdlib_path
        
        try:
            # Try to find codeql home directory
            result = subprocess.run(
                ["codeql", "resolve", "packs"],
                capture_output=True,
                text=True,
                timeout=5
            )
            
            if result.returncode == 0:
                # Parse output to find standard library path
                # Look for the local pack cache path which contains the standard library
                # Format: "Searching in: /home/user/.codeql/packages"
                for line in result.stdout.splitlines():
                    if "Searching in:" in line and ".codeql/packages" in line:
                        # Extract path after "Searching in:"
                        path_str = line.split("Searching in:", 1)[1].strip()
                        path = Path(path_str)
                        # The standard library is in the 'codeql' subdirectory of packages
                        stdlib_path = path / "codeql"
                        if stdlib_path.exists():
                            # Verify it's a valid stdlib by checking for common packages
                            # Support multiple languages: java, python, cpp, etc.
                            valid_indicators = [
                                "java-all", "python-all", "cpp-all",  # Language-specific packs
                                "dataflow", "util", "ssa",           # Common packages
                                "java", "python", "cpp"              # Direct language directories
                            ]
                            
                            for indicator in valid_indicators:
                                if (stdlib_path / indicator).exists():
                                    self._codeql_stdlib_path = stdlib_path
                                    return self._codeql_stdlib_path
                
                # Alternative: try to find CodeQL distribution path
                # Format: "Searching in: /home/user/Software/codeql"
                for line in result.stdout.splitlines():
                    if "Searching in:" in line and "codeql" in line:
                        path_str = line.split("Searching in:", 1)[1].strip()
                        path = Path(path_str)
                        if path.exists() and path.name.startswith("codeql"):
                            self._codeql_stdlib_path = path
                            return self._codeql_stdlib_path
        except Exception:
            pass
        
        # Fallback: try common locations (cross-platform)
        import platform
        
        common_paths = [
            Path.home() / ".codeql" / "packages" / "codeql",  # User .codeql directory (package manager)
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
            # Check for valid CodeQL stdlib indicators supporting multiple languages
            if path.exists():
                valid_indicators = [
                    "java-all", "python-all", "cpp-all",  # Language-specific packs
                    "dataflow", "util", "ssa",           # Common packages
                    "java", "python", "cpp"              # Direct language directories
                ]
                
                # Check direct paths
                for indicator in valid_indicators:
                    if (path / indicator).exists():
                        self._codeql_stdlib_path = path
                        return self._codeql_stdlib_path
                
                # Check nested paths (e.g., java/ql/lib)
                nested_indicators = [
                    Path("java") / "ql" / "lib",
                    Path("python") / "ql" / "lib", 
                    Path("cpp") / "ql" / "lib"
                ]
                
                for indicator in nested_indicators:
                    if (path / indicator).exists():
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
            re.compile(rf"^\s*signature\s+module\s+{symbol_name}\s*(<|{{)"),  # signature module definition
            re.compile(rf"^\s*class\s+{symbol_name}\s"),  # class definition
            re.compile(rf"^\s*abstract\s+class\s+{symbol_name}\s"),  # abstract class definition
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
        
        # Build list of search directories
        search_dirs = []
        
        # 1. Language-specific directories
        # Handle versioned package structure (e.g., java-all/7.7.2/)
        lang_pack_dir = stdlib_path / f"{language}-all"
        if lang_pack_dir.exists():
            # Find the latest version directory
            version_dirs = [d for d in lang_pack_dir.iterdir() if d.is_dir()]
            if version_dirs:
                # Sort by version (simple string sort works for semantic versions)
                latest_version = sorted(version_dirs)[-1]
                search_dirs.append(latest_version)
        
        # Try non-versioned paths
        lang_dir = stdlib_path / language / "ql" / "lib"
        if lang_dir.exists():
            search_dirs.append(lang_dir)
        
        # Try alternative paths for different language pack structures
        alt_paths = [
            stdlib_path / language / "ql" / "src",
            stdlib_path / f"{language}-all" / "ql" / "lib",
            stdlib_path / f"{language}-all" / "ql" / "src",
            # For language-specific packages that might be directly under stdlib
            stdlib_path / f"codeql-{language}-all" / "ql" / "lib",
            stdlib_path / f"codeql-{language}" / "ql" / "lib",
        ]
        for alt in alt_paths:
            if alt.exists() and alt not in search_dirs:
                search_dirs.append(alt)
        
        # 2. Common/shared libraries (for signature modules like ConfigSig)
        # These are also versioned packages
        common_packages = ["dataflow", "util", "ssa", "typetracking", "regex"]
        for pkg_name in common_packages:
            pkg_dir = stdlib_path / pkg_name
            if pkg_dir.exists():
                # Check if it's a versioned package
                version_dirs = [d for d in pkg_dir.iterdir() if d.is_dir() and d.name[0].isdigit()]
                if version_dirs:
                    # Use the latest version
                    latest_version = sorted(version_dirs)[-1]
                    search_dirs.append(latest_version)
                else:
                    # Use the package directory directly
                    search_dirs.append(pkg_dir)
        
        if not search_dirs:
            return None
        
        # Try ripgrep first (faster and cross-platform)
        try:
            # Search for class or predicate definitions using ripgrep
            # ripgrep works on Windows/Linux/Mac
            patterns = [
                f"^\\s*signature\\s+module\\s+{symbol_name}\\s*(<|\\{{)",  # signature module (escape brace)
                f"^\\s*class\\s+{symbol_name}\\s",
                f"^\\s*abstract\\s+class\\s+{symbol_name}\\s",
                f"^\\s*predicate\\s+{symbol_name}\\s*\\(",
                f"^\\s*(private|override|abstract|final)\\s+{symbol_name}\\s+{symbol_name}\\s*\\(",
                f"^\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s+{symbol_name}\\s*\\(",
            ]
            
            for pattern in patterns:
                # Search all directories
                for search_dir in search_dirs:
                    result = subprocess.run(
                        [
                            "rg",  # Works on all platforms when in PATH
                            "--line-number",  # Show line numbers
                            "--no-heading",   # Don't group by file
                            "--glob", "*.qll", # Only search .qll files
                            "--glob", "!upgrades/", # Exclude upgrades directory
                            pattern,
                            str(search_dir)
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
        # Try all search directories
        for search_dir in search_dirs:
            result = self._python_search_files(symbol_name, search_dir)
            if result:
                return result
        
        return None
    
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
                    from pure_auto_codeql.tools.lsp_codeql import write_msg
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
            path_str = unquote(parsed.path)
            # Windows: Remove leading slash from drive letter paths (e.g., /C:/path -> C:/path)
            import platform
            if platform.system() == "Windows" and len(path_str) > 2 and path_str[0] == '/' and path_str[2] == ':':
                path_str = path_str[1:]
            file_path = Path(path_str)
        
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
                from pure_auto_codeql.tools.lsp_codeql import write_msg
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
    
    def test_language_support(self, language: str) -> bool:
        """
        Test if a specific language is supported in the current CodeQL installation.
        
        Args:
            language: Language identifier (java, python, cpp, etc.)
            
        Returns:
            True if language is supported, False otherwise
        """
        stdlib_path = self._get_codeql_stdlib_path()
        if not stdlib_path:
            return False
        
        # Check various possible locations for the language
        possible_paths = [
            stdlib_path / f"{language}-all",
            stdlib_path / f"codeql-{language}-all", 
            stdlib_path / language,
            stdlib_path / language / "ql" / "lib",
            stdlib_path / language / "ql" / "src",
        ]
        
        for path in possible_paths:
            if path.exists():
                return True
        
        return False
    
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
