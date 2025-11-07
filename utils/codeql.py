import json
import subprocess
import tempfile
import os
import shutil
import textwrap
import time
from pathlib import Path
from typing import Optional, Dict, List, Any
from datetime import datetime


# 功能：
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

def gen_codeql_lock_yml(lang: str) -> str:
    python_yml = '''---
lockVersion: 1.0.0
dependencies:
  codeql/concepts:
    version: 0.0.7
  codeql/controlflow:
    version: 2.0.17
  codeql/dataflow:
    version: 2.0.17
  codeql/mad:
    version: 1.0.33
  codeql/python-all:
    version: 4.0.17
  codeql/regex:
    version: 1.0.33
  codeql/ssa:
    version: 2.0.9
  codeql/threat-models:
    version: 1.0.33
  codeql/tutorial:
    version: 1.0.33
  codeql/typetracking:
    version: 2.0.17
  codeql/util:
    version: 2.0.20
  codeql/xml:
    version: 1.0.33
  codeql/yaml:
    version: 1.0.33
compiled: false
'''

    java_yml = '''---
lockVersion: 1.0.0
dependencies:
  codeql/controlflow:
    version: 2.0.17
  codeql/dataflow:
    version: 2.0.17
  codeql/java-all:
    version: 7.7.2
  codeql/mad:
    version: 1.0.33
  codeql/quantum:
    version: 0.0.11
  codeql/rangeanalysis:
    version: 1.0.33
  codeql/regex:
    version: 1.0.33
  codeql/ssa:
    version: 2.0.9
  codeql/threat-models:
    version: 1.0.33
  codeql/tutorial:
    version: 1.0.33
  codeql/typeflow:
    version: 1.0.33
  codeql/typetracking:
    version: 2.0.17
  codeql/util:
    version: 2.0.20
  codeql/xml:
    version: 1.0.33
compiled: false
'''

    cpp_yml = '''---
lockVersion: 1.0.0
dependencies:
  codeql/concepts:
    version: 0.0.7
  codeql/controlflow:
    version: 2.0.17
  codeql/dataflow:
    version: 2.0.17
  codeql/mad:
    version: 1.0.33
  codeql/python-all:
    version: 4.0.17
  codeql/regex:
    version: 1.0.33
  codeql/ssa:
    version: 2.0.9
  codeql/threat-models:
    version: 1.0.33
  codeql/tutorial:
    version: 1.0.33
  codeql/typetracking:
    version: 2.0.17
  codeql/util:
    version: 2.0.20
  codeql/xml:
    version: 1.0.33
  codeql/yaml:
    version: 1.0.33
compiled: false
'''
    if lang == 'cpp':
        return cpp_yml
    elif lang == 'python':
        return python_yml
    else:
        return java_yml

def create_temporary_qlpack(query_content: str, language: Optional[str] = None) -> Path:
    print("创建被调用")
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S_%f')
    temp_base_dir = Path('./temp/codeql_temp')
    temp_base_dir.mkdir(parents=True, exist_ok=True)
    pack_dir = temp_base_dir / timestamp
    pack_dir.mkdir(parents=True, exist_ok=True)

    lang = normalize_language(language or detect_language_from_query(query_content))
    if lang == 'cpp':
        version = "^6.0.0"
        dep_pack = 'codeql/cpp-all'
    elif lang == 'python':
        version = "^4.0.17"
        dep_pack = 'codeql/python-all'
    else:
        version = "^7.7.1"
        dep_pack = 'codeql/java-all'

    qlpack_content = textwrap.dedent(
        f"""---
library: false
warnOnImplicitThis: false
name: getting-started/codeql-extra-queries-{lang}
version: 1.0.0
dependencies:
  {dep_pack}: "{version}"
"""
    )


    (pack_dir / 'qlpack.yml').write_text(qlpack_content, encoding='utf-8')


    try:
        open(pack_dir / 'codeql-pack.lock.yml','w').write(gen_codeql_lock_yml(lang))
    except Exception as e:
        print(f"Warning: Failed to write codeql-pack.lock.yml: {str(e)}")

    sanitized = query_content

    if sanitized.startswith('\ufeff'):
        sanitized = sanitized.lstrip('\ufeff')

    sanitized = sanitized.lstrip()

    query_file = pack_dir / f'query_{timestamp}.ql'
    query_file.write_text(sanitized, encoding='utf-8')

    return query_file


def execute_codeql_query(query_content: str, database_path: str, language: Optional[str] = None , query_file: Optional[Path] = None) -> Dict[str, Any]:
    """
    对指定的数据库执行CodeQL查询。
    
    Args:
        query_content: 要执行的CodeQL查询字符串。
        database_path: CodeQL数据库的路径。
        language: 可选的显式语言（'java'、'python'、'c'）。如果省略，则从查询中自动检测。
    
    Returns:
        包含以下内容的字典：
        - success (bool): 执行是否成功
        - output (str): 查询输出或错误消息
        - results (List): 如果成功则包含解析结果，否则为空列表
        - sarif_path (str): SARIF输出文件的路径
    """
    sarif_path: Optional[Path] = None
    try:
        open(query_file,'w').write(query_content)
        # 准备标准化的SARIF输出路径
        output_dir = Path('./output')
        try:
            output_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            # 如果/output不可写，则回退到当前工作目录
            output_dir = Path.cwd() / 'output'
            output_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        sarif_path = output_dir / f'result_{timestamp}.sarif'

        start_time = time.time()
        
        # 使用`codeql database analyze`执行查询，并使用SARIF v2.1.0输出
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
        
        # 计算并显示实际执行时间
        execution_time = time.time() - start_time
        print(f"✅ CodeQL查询执行完成! 用时: {execution_time:.2f}秒")
        print("📊 正在处理查询结果...")

        if result.returncode == 0:
            return {
                'success': True,
                'output': 'Query executed successfully',
                'results': [],
                'sarif_path': str(sarif_path) if sarif_path else None,
            }
        else:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if result.stderr:
                error_output.append(result.stderr.strip())
            if result.stdout:
                error_output.append(result.stdout.strip())
            
            combined_error = '\n'.join(filter(None, error_output))
            
            print(f"CodeQL execution failed with error: \n {combined_error}")
            return {
                'success': False,
                'output': combined_error or 'Unknown CodeQL execution error',
                'results': [],
                'sarif_path': str(sarif_path) if sarif_path else None,
            }
    
    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'output': 'CodeQL query execution timed out after 600 seconds. This may indicate a complex query or performance issue.',
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
        }
    except FileNotFoundError:
        return {
            'success': False,
            'output': 'CodeQL CLI not found in PATH. Please ensure CodeQL is properly installed and accessible.',
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
        }
    except Exception as e:
        # 捕获详细的错误信息，包括潜在的QL编译错误
        error_msg = f'CodeQL execution failed: {str(e)}'
        if hasattr(e, '__cause__') and e.__cause__:
            error_msg += f'\nCause: {str(e.__cause__)}'
        
        return {
            'success': False,
            'output': error_msg,
            'results': [],
            'sarif_path': str(sarif_path) if sarif_path else None,
        }
    finally:
        # 删除临时目录
        # if pack_dir and pack_dir.exists():
        #     try:
        #         shutil.rmtree(pack_dir)
        #     except Exception:
                pass


def run_query_and_decode_to_text(
    query_content: str,
    database_path: str,
    language: Optional[str] = None,
    output_dir: Optional[str] = None,
) -> Dict[str, Any]:
    query_file = None
    pack_dir = None
    try:
        query_file = create_temporary_qlpack(query_content, language=language)
        pack_dir = query_file.parent

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        bqrs_path = pack_dir / f"result_{timestamp}.bqrs"

        result = subprocess.run(
            [
                'codeql', 'query', 'run',
                str(query_file),
                '--database', database_path,
                f'--output={str(bqrs_path)}',
            ],
            capture_output=True,
            text=True,
            timeout=600,
        )

        if result.returncode != 0:
            parts: List[str] = []
            if result.stderr:
                parts.append(result.stderr.strip())
            if result.stdout:
                parts.append(result.stdout.strip())
            return {
                'success': False,
                'output': '\n'.join(filter(None, parts)) or 'Unknown CodeQL run error',
                'result_file': None,
            }

        decode = subprocess.run(
            [
                'codeql', 'bqrs', 'decode',
                '--format=table',
                str(bqrs_path),
            ],
            capture_output=True,
            text=True,
            timeout=300,
        )

        if decode.returncode != 0:
            parts: List[str] = []
            if decode.stderr:
                parts.append(decode.stderr.strip())
            if decode.stdout:
                parts.append(decode.stdout.strip())
            return {
                'success': False,
                'output': '\n'.join(filter(None, parts)) or 'Unknown BQRS decode error',
                'result_file': None,
            }

        content = decode.stdout or ''
        out_dir = Path(output_dir) if output_dir else Path('./temp/search_temp')
        try:
            out_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            out_dir = Path.cwd() / 'temp' / 'search_temp'
            out_dir.mkdir(parents=True, exist_ok=True)

        result_file = out_dir / f"query_{timestamp}.txt"
        text_to_write = content if content.strip() else 'No results.'
        result_file.write_text(text_to_write, encoding='utf-8')

        return {
            'success': True,
            'output': content,
            'result_file': str(result_file),
        }
    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'output': 'CodeQL run/decode timed out.',
            'result_file': None,
        }
    except FileNotFoundError:
        return {
            'success': False,
            'output': 'CodeQL CLI not found in PATH. Please ensure CodeQL is properly installed and accessible.',
            'result_file': None,
        }
    except Exception as e:
        return {
            'success': False,
            'output': f'Unexpected error: {str(e)}',
            'result_file': None,
        }


def parse_codeql_results(result_output: str) -> List[Dict[str, Any]]:
    """
    将CodeQL查询输出解析为结构化数据。
    
    Args:
        result_output: CodeQL查询执行的原始输出。
    
    Returns:
        解析后的结果记录列表。如果没有结果或解析失败，则返回空列表。
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
