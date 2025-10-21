import json
import subprocess
import tempfile
import os
import shutil
from pathlib import Path
from typing import Optional, Dict, List, Any


def create_temporary_qlpack(query_content: str) -> Path:
    """
    Create a temporary CodeQL query pack with a qlpack.yml file and install dependencies.
    
    Args:
        query_content: The CodeQL query string.
    
    Returns:
        Path to the created temporary query file inside the pack.
    """
    # Create a temporary directory to act as the qlpack
    pack_dir = Path(tempfile.mkdtemp())
    
    # Define the content of qlpack.yml
    qlpack_content = """name: temp-query-pack
version: 0.0.0
dependencies:
  codeql/java-all: "*"
"""
    
    # Write the qlpack.yml file
    (pack_dir / "qlpack.yml").write_text(qlpack_content, encoding='utf-8')
    
    # Install pack dependencies
    try:
        result = subprocess.run(
            ['codeql', 'pack', 'install'],
            cwd=pack_dir,
            capture_output=True,
            text=True,
            timeout=60
        )
        if result.returncode != 0:
            print(f"Warning: Failed to install pack dependencies: {result.stderr}")
    except Exception as e:
        print(f"Warning: Failed to run codeql pack install: {str(e)}")
    
    # Write the query file
    query_file = pack_dir / "query.ql"
    query_file.write_text(query_content, encoding='utf-8')
    
    return query_file


def execute_codeql_query(query_content: str, database_path: str) -> Dict[str, Any]:
    """
    Execute a CodeQL query against a specified database.
    
    Args:
        query_content: The CodeQL query string to execute.
        database_path: Path to the CodeQL database.
    
    Returns:
        A dictionary containing:
        - success (bool): Whether execution succeeded
        - output (str): Query output or error message
        - results (List): Parsed results if successful, empty list otherwise
    """
    query_file = None
    pack_dir = None
    try:
        query_file = create_temporary_qlpack(query_content)
        pack_dir = query_file.parent
        
        result = subprocess.run(
            ['codeql', 'query', 'run', '--database', database_path, str(query_file)],
            capture_output=True,
            text=True,
            timeout=300
        )
        
        if result.returncode == 0:
            parsed_results = parse_codeql_results(result.stdout)
            return {
                'success': True,
                'output': result.stdout,
                'results': parsed_results
            }
        else:
            return {
                'success': False,
                'output': result.stderr or result.stdout,
                'results': []
            }
    
    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'output': 'Query execution timed out after 300 seconds',
            'results': []
        }
    except FileNotFoundError:
        return {
            'success': False,
            'output': 'CodeQL CLI not found. Ensure codeql is in PATH',
            'results': []
        }
    except Exception as e:
        return {
            'success': False,
            'output': f'Execution error: {str(e)}',
            'results': []
        }
    finally:
        if pack_dir and pack_dir.exists():
            try:
                shutil.rmtree(pack_dir)
            except Exception:
                pass


def parse_codeql_results(result_output: str) -> List[Dict[str, Any]]:
    """
    Parse CodeQL query output into structured data.
    
    Args:
        result_output: Raw output from CodeQL query execution.
    
    Returns:
        List of parsed result records. Returns empty list if no results or parsing fails.
    """
    if not result_output or not result_output.strip():
        return []
    
    try:
        data = json.loads(result_output)
        if isinstance(data, dict) and 'runs' in data:
            results = []
            for run in data.get('runs', []):
                for result in run.get('results', []):
                    results.append(result)
            return results
        elif isinstance(data, list):
            return data
        else:
            return []
    except json.JSONDecodeError:
        lines = result_output.strip().split('\n')
        if not lines:
            return []
        
        results = []
        for line in lines:
            line = line.strip()
            if line and not line.startswith('#'):
                parts = line.split('|')
                if len(parts) > 1:
                    results.append({
                        'data': [p.strip() for p in parts]
                    })
        return results
