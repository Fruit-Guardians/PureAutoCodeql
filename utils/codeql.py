import json
import subprocess
import tempfile
import os
import shutil
from pathlib import Path
from typing import Optional, Dict, List, Any
from datetime import datetime


# 功能：
def detect_language_from_query(query_content: str) -> str:
    """Simple heuristic to detect CodeQL language from query content."""
    content = query_content.lower()
    if 'import java' in content:
        return 'java'
    if 'import python' in content:
        return 'python'
    if 'import cpp' in content or 'import cplusplus' in content or 'import c' in content:
        return 'cpp'
    return 'java'


def language_to_pack(language: str) -> str:
    """Map language to the appropriate CodeQL pack dependency."""
    lang = (language or 'java').lower()
    mapping = {
        'java': 'codeql/java-all',
        'python': 'codeql/python-all',
        'cpp': 'codeql/cpp-all',
    }
    return mapping.get(lang, 'codeql/java-all')


def create_temporary_qlpack(query_content: str, language: Optional[str] = None) -> Path:
    """
    Create a temporary CodeQL query pack with a qlpack.yml file and install dependencies.
    Dynamically selects pack dependencies based on target language.
    
    Args:
        query_content: The CodeQL query string.
        language: Optional explicit language (e.g., 'java', 'python', 'cpp'). If omitted, auto-detect from query.
    
    Returns:
        Path to the created temporary query file inside the pack.
    """
    # Create a temporary directory to act as the qlpack
    pack_dir = Path(tempfile.mkdtemp())
    
    # Determine dependency pack
    lang = language or detect_language_from_query(query_content)
    dep_pack = language_to_pack(lang)
    
    # Define the content of qlpack.yml
    qlpack_content = f"""name: temp-query-pack
version: 0.0.0
dependencies:
  {dep_pack}: "*"
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
    # Ensure any BOM/leading whitespace is removed so metadata starts at byte 0
    sanitized = query_content
    # Strip UTF-8 BOM if present
    if sanitized.startswith('\ufeff'):
        sanitized = sanitized.lstrip('\ufeff')
    # Remove leading blank lines and spaces so the metadata comment is at the top
    sanitized = sanitized.lstrip()

    query_file = pack_dir / "query.ql"
    query_file.write_text(sanitized, encoding='utf-8')
    
    return query_file


def execute_codeql_query(query_content: str, database_path: str, language: Optional[str] = None) -> Dict[str, Any]:
    """
    Execute a CodeQL query against a specified database.
    
    Args:
        query_content: The CodeQL query string to execute.
        database_path: Path to the CodeQL database.
        language: Optional explicit language ( 'java', 'python', 'c'). If omitted, auto-detect from query.
    
    Returns:
        A dictionary containing:
        - success (bool): Whether execution succeeded
        - output (str): Query output or error message
        - results (List): Parsed results if successful, empty list otherwise
        - sarif_path (str): Path to the SARIF output file
    """
    query_file = None
    pack_dir = None
    sarif_path: Optional[Path] = None
    try:
        query_file = create_temporary_qlpack(query_content, language=language)
        pack_dir = query_file.parent

        # Prepare standardized SARIF output path
        output_dir = Path('./output')
        try:
            output_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            # Fallback to current working directory if /output is not writable
            output_dir = Path.cwd() / 'output'
            output_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        sarif_path = output_dir / f'result_{timestamp}.sarif'

        # Execute using `codeql database analyze` with SARIF v2.1.0 output
        result = subprocess.run(
            [
                'codeql', 'database', 'analyze',
                database_path,
                str(query_file),
                '--rerun',
                '--format=sarifv2.1.0',
                f'--output={str(sarif_path)}',
            ],
            capture_output=True,
            text=True,
            timeout=600
        )

        if result.returncode == 0:
            return {
                'success': True,
                'output': (result.stdout or '').strip(),
                'results': [],
                'sarif_path': str(sarif_path) if sarif_path else None,
            }
        else:
            return {
                'success': False,
                'output': (result.stderr or result.stdout or '').strip(),
                'results': [],
                'sarif_path': str(sarif_path) if sarif_path else None,
            }
    
    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'output': 'Query execution timed out after 600 seconds',
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
        }
    except FileNotFoundError:
        return {
            'success': False,
            'output': 'CodeQL CLI not found. Ensure codeql is in PATH',
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
        }
    except Exception as e:
        return {
            'success': False,
            'output': f'Execution error: {str(e)}',
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
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
