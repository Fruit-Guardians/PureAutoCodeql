#!/usr/bin/env python3
"""
LSP Definition Lookup Utility

Provides functionality to query CodeQL function definitions using LSP protocol.
Reuses existing HotCodeQL LSP engine instance without restarting the service.
"""

import json
import time
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
    
    def _next_request_id(self) -> int:
        """Generate unique request ID for LSP messages."""
        self._request_id_counter += 1
        return self._request_id_counter
    
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
        Extract complete function definition block with brace matching.
        
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
        
        block_lines = []
        brace_balance = 0
        started = False
        
        line_idx = start_line
        search_char = start_char or 0
        
        while line_idx < len(lines):
            line_text = lines[line_idx]
            block_lines.append((line_idx + 1, line_text))
            
            # Scan characters for brace matching
            for ch in line_text[search_char:]:
                if ch == '{':
                    brace_balance += 1
                    started = True
                elif ch == '}':
                    if started:
                        brace_balance -= 1
                
                if started and brace_balance == 0:
                    # Found matching closing brace
                    return block_lines
            
            line_idx += 1
            search_char = 0
        
        # Return what we collected even if braces didn't match
        return block_lines if started else None
    
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
            # Create a temporary query with the function reference
            temp_query = query_text + f"\n// Temporary reference for LSP lookup\n// {function_name}\n"
            
            # Find the injected symbol
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
                    
                    # Wait a bit for LSP to process
                    import time
                    time.sleep(0.5)
                    
                except Exception:
                    return None
            else:
                return None
        
        line, character = position
        
        # Step 2: Query definition via LSP
        definition = self.query_definition(line, character, timeout)
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
