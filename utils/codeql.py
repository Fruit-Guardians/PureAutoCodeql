import json
import subprocess
import tempfile
import os
import shutil
import textwrap
import time
from pathlib import Path
from typing import Optional, Dict, List, Any, Tuple
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


def validate_codeql_database(database_path: str) -> Tuple[bool, str]:
    """
    验证CodeQL数据库是否存在且有效。

    Args:
        database_path: CodeQL数据库的路径

    Returns:
        (is_valid, error_message) 元组：
        - is_valid: 数据库是否有效
        - error_message: 如果无效，包含详细的错误信息；如果有效，为空字符串
    """
    if not database_path:
        return False, "数据库路径为空。请提供有效的CodeQL数据库路径。"

    db_path = Path(database_path)

    # 检查路径是否存在
    if not db_path.exists():
        return False, (
            f"数据库路径不存在: {database_path}\n"
            f"请检查路径是否正确，或使用 'codeql database create' 创建数据库。"
        )

    # 检查是否为目录
    if not db_path.is_dir():
        return False, (
            f"数据库路径不是目录: {database_path}\n"
            f"CodeQL数据库必须是一个目录。"
        )

    # 检查关键文件/目录是否存在（CodeQL数据库的典型结构）
    # CodeQL数据库通常包含 codeql-database.yml 或 db-* 目录
    has_database_yml = (db_path / "codeql-database.yml").exists()
    has_db_subdirs = any(
        subdir.name.startswith("db-") or subdir.name == "db"
        for subdir in db_path.iterdir()
        if subdir.is_dir()
    )

    if not (has_database_yml or has_db_subdirs):
        # 尝试使用 codeql database info 命令验证
        try:
            result = subprocess.run(
                ['codeql', 'database', 'info', str(db_path)],
                capture_output=True,
                text=True,
                timeout=10
            )
            if result.returncode != 0:
                error_msg = result.stderr.strip() or result.stdout.strip()
                if "not a recognized CodeQL database" in error_msg:
                    return False, (
                        f"无效的CodeQL数据库: {database_path}\n"
                        f"错误详情: {error_msg}\n"
                        f"请使用 'codeql database create' 创建有效的数据库，或检查数据库是否已损坏。"
                    )
                return False, (
                    f"无法验证数据库: {database_path}\n"
                    f"错误详情: {error_msg}"
                )
        except FileNotFoundError:
            # CodeQL CLI 未找到，但至少路径存在，返回警告而不是错误
            return True, "警告: 无法验证数据库（CodeQL CLI 未找到），但路径存在。"
        except subprocess.TimeoutExpired:
            return False, (
                f"数据库验证超时: {database_path}\n"
                f"数据库可能已损坏或无法访问。"
            )
        except Exception as e:
            return False, (
                f"数据库验证失败: {database_path}\n"
                f"错误: {str(e)}"
            )

    # 数据库看起来有效
    return True, ""


def is_database_error(error_output: str) -> bool:
    """
    检查错误输出是否与数据库相关。

    Args:
        error_output: CodeQL命令的错误输出

    Returns:
        如果是数据库相关错误，返回True；否则返回False
    """
    if not error_output:
        return False

    error_lower = error_output.lower()
    database_error_patterns = [
        "not a recognized codeql database",
        "is not a codeql database",
        "database does not exist",
        "database path",
        "invalid database",
        "database not found",
        "无法识别",
        "不是有效的",
    ]

    return any(pattern in error_lower for pattern in database_error_patterns)

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
  codeql/controlflow:
    version: 2.0.17
  codeql/cpp-all:
    version: 6.0.0
  codeql/dataflow:
    version: 2.0.17
  codeql/mad:
    version: 1.0.33
  codeql/quantum:
    version: 0.0.11
  codeql/rangeanalysis:
    version: 1.0.33
  codeql/ssa:
    version: 2.0.9
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
    if lang == 'cpp':
        return cpp_yml
    elif lang == 'python':
        return python_yml
    else:
        return java_yml

def create_temporary_qlpack(query_content: str, language: Optional[str] = None, task_id: Optional[str] = None) -> Path:
    print("创建被调用")
    temp_base_dir = Path('./temp/codeql_temp')
    temp_base_dir.mkdir(parents=True, exist_ok=True)

    if task_id:
        # 使用固定的任务ID路径
        pack_dir = temp_base_dir / task_id
        pack_dir.mkdir(parents=True, exist_ok=True)
        timestamp = task_id
    else:
        # 向后兼容: 使用时间戳路径
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S_%f')
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

    # 根据语言选择 qlpack 模板；C/C++ 使用用户提供的固定模板
    if lang == 'cpp':
        qlpack_content = textwrap.dedent(
            """
name: cve3
version: 6.0.1
dependencies:
  codeql/cpp-all: "^6.0.0"
"""
        )
    else:
        qlpack_content = textwrap.dedent(
            f"""
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


def run_simple_query(query_content: str, database_path: str, language: Optional[str] = None, query_file: Optional[Path] = None) -> Dict[str, Any]:
    """
    执行简单的CodeQL查询，不进行路径分析。

    Args:
        query_content: 要执行的CodeQL查询字符串。
        database_path: CodeQL数据库的路径。
        language: 可选的显式语言（'java'、'python'、'c'）。如果省略，则从查询中自动检测。
        query_file: 可选的查询文件路径。

    Returns:
        包含以下内容的字典：
        - success (bool): 执行是否成功
        - output (str): 查询输出或错误消息
        - results (List): 如果成功则包含解析结果，否则为空列表
        - result_file (str): 结果文件的路径
    """
    # 在执行前验证数据库
    is_valid, validation_error = validate_codeql_database(database_path)
    if not is_valid:
        return {
            'success': False,
            'output': f"数据库验证失败:\n{validation_error}",
            'results': [],
            'result_file': None,
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }

    try:
        open(query_file,'w').write(query_content)
        
        # 准备输出目录
        output_dir = Path('./output')
        try:
            output_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            # 如果/output不可写，则回退到当前工作目录
            output_dir = Path.cwd() / 'output'
            output_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        bqrs_path = output_dir / f'result_{timestamp}.bqrs'

        start_time = time.time()

        # 使用`codeql query run`执行简单查询
        result = subprocess.run(
            [
                'codeql', 'query', 'run',
                str(query_file),
                '--database', database_path,
                f'--output={str(bqrs_path)}',
            ],
            capture_output=True,
            text=True,
            timeout=600
        )

        # 计算并显示实际执行时间
        execution_time = time.time() - start_time
        print(f"✅ CodeQL简单查询执行完成! 用时: {execution_time:.2f}秒")
        print("📊 正在处理查询结果...")

        if result.returncode != 0:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if result.stderr:
                error_output.append(result.stderr.strip())
            if result.stdout:
                error_output.append(result.stdout.strip())

            combined_error = '\n'.join(filter(None, error_output))

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = (
                    f"数据库错误:\n"
                    f"{combined_error}\n\n"
                    f"建议:\n"
                    f"1. 检查数据库路径是否正确: {database_path}\n"
                    f"2. 使用 'codeql database info {database_path}' 验证数据库\n"
                    f"3. 如果数据库不存在或已损坏，请使用 'codeql database create' 重新创建"
                )
                return {
                    'success': False,
                    'output': enhanced_error,
                    'results': [],
                    'result_file': None,
                    'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
                }

            return {
                'success': False,
                'output': combined_error or 'Unknown CodeQL execution error',
                'results': [],
                'result_file': None,
                'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
            }

        # 解码BQRS文件为JSON格式，以便detect_breakpoints方法能够正确解析
        decode_result = subprocess.run(
            [
                'codeql', 'bqrs', 'decode',
                '--format=json',
                str(bqrs_path),
            ],
            capture_output=True,
            text=True,
            timeout=300,
        )

        if decode_result.returncode != 0:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if decode_result.stderr:
                error_output.append(decode_result.stderr.strip())
            if decode_result.stdout:
                error_output.append(decode_result.stdout.strip())

            combined_error = '\n'.join(filter(None, error_output))
            return {
                'success': False,
                'output': f"BQRS解码失败: {combined_error}",
                'results': [],
                'result_file': None,
                'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
            }

        # 保存结果到文件
        result_file = output_dir / f'result_{timestamp}.txt'
        result_content = decode_result.stdout or 'No results.'
        result_file.write_text(result_content, encoding='utf-8')

        # 尝试解析结果为JSON格式，如果失败则保持原始文本
        parsed_results = None
        try:
            # 尝试解析为JSON
            if result_content.strip().startswith('{') or result_content.strip().startswith('['):
                parsed_results = json.loads(result_content)
        except (json.JSONDecodeError, AttributeError):
            # 如果不是JSON格式，保持原始文本
            pass

        return {
            'success': True,
            'output': result_content,
            'results': parsed_results if parsed_results is not None else [],  # 如果解析成功则返回解析后的结果
            'result_file': str(result_file),
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }

    except subprocess.TimeoutExpired:
        return {
            'success': False,
            'output': 'CodeQL简单查询执行超时（600秒）。这可能表示查询复杂或性能问题。',
            'results': [],
            'result_file': None,
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }
    except FileNotFoundError:
        return {
            'success': False,
            'output': '未找到CodeQL CLI。请确保CodeQL已正确安装并在PATH中可访问。',
            'results': [],
            'result_file': None,
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }
    except Exception as e:
        # 捕获详细的错误信息，包括潜在的QL编译错误
        error_msg = f'CodeQL简单查询执行失败: {str(e)}'
        if hasattr(e, '__cause__') and e.__cause__:
            error_msg += f'\nCause: {str(e.__cause__)}'

        return {
            'success': False,
            'output': error_msg,
            'results': [],
            'result_file': None,
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }


def execute_codeql_query(query_content: str, database_path: str, language: Optional[str] = None , query_file: Optional[Path] = None, alert: Optional[str] = None) -> Dict[str, Any]:
    """
    对指定的数据库执行CodeQL查询。

    Args:
        query_content: 要执行的CodeQL查询字符串。
        database_path: CodeQL数据库的路径。
        language: 可选的显式语言（'java'、'python'、'c'）。如果省略，则从查询中自动检测。
        query_file: 可选的查询文件路径。
        alert: 可选参数，当设置为'alert'时，执行简单查询而不进行路径分析。

    Returns:
        包含以下内容的字典：
        - success (bool): 执行是否成功
        - output (str): 查询输出或错误消息
        - results (List): 如果成功则包含解析结果，否则为空列表
        - sarif_path (str): SARIF输出文件的路径
    """
    # 在执行前验证数据库
    is_valid, validation_error = validate_codeql_database(database_path)
    if not is_valid:
        return {
            'success': False,
            'output': f"数据库验证失败:\n{validation_error}",
            'results': [],
            'sarif_path': None,
        }

    # 如果指定了alert参数，执行简单查询
    if alert == 'alert':
        return run_simple_query(query_content, database_path, language, query_file)
    
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

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = (
                    f"数据库错误:\n"
                    f"{combined_error}\n\n"
                    f"建议:\n"
                    f"1. 检查数据库路径是否正确: {database_path}\n"
                    f"2. 使用 'codeql database info {database_path}' 验证数据库\n"
                    f"3. 如果数据库不存在或已损坏，请使用 'codeql database create' 重新创建"
                )
                print(f"CodeQL execution failed with database error: \n {enhanced_error}")
                return {
                    'success': False,
                    'output': enhanced_error,
                    'results': [],
                    'sarif_path': str(sarif_path) if sarif_path else None,
                }

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
    # 在执行前验证数据库
    is_valid, validation_error = validate_codeql_database(database_path)
    if not is_valid:
        return {
            'success': False,
            'output': f"数据库验证失败:\n{validation_error}",
            'result_file': None,
        }

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

            combined_error = '\n'.join(filter(None, parts)) or 'Unknown CodeQL run error'

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = (
                    f"数据库错误:\n"
                    f"{combined_error}\n\n"
                    f"建议:\n"
                    f"1. 检查数据库路径是否正确: {database_path}\n"
                    f"2. 使用 'codeql database info {database_path}' 验证数据库\n"
                    f"3. 如果数据库不存在或已损坏，请使用 'codeql database create' 重新创建"
                )
                return {
                    'success': False,
                    'output': enhanced_error,
                    'result_file': None,
                }

            return {
                'success': False,
                'output': combined_error,
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


def save_query_to_persistent_dir(query_content: str, task_id: str, language: str, metadata: Optional[Dict[str, Any]] = None) -> Optional[Path]:
    """
    将生成的 CodeQL 查询保存到持久化目录（temp/ql_queries），每轮保存一个版本。

    Args:
        query_content: CodeQL 查询内容
        task_id: 任务 ID
        language: 查询语言
        metadata: 可选的元数据字典（应包含 round 信息）

    Returns:
        保存的查询文件路径，如果失败则返回 None
    """
    try:
        output_base_dir = Path('./temp/ql_queries')
        output_base_dir.mkdir(parents=True, exist_ok=True)

        task_dir = output_base_dir / task_id
        task_dir.mkdir(parents=True, exist_ok=True)

        # 获取轮次信息，用于生成不同版本的文件名
        round_num = metadata.get('round', 1) if metadata else 1

        # 保存查询文件（每轮一个文件）
        query_file = task_dir / f'query_round{round_num}.ql'
        query_file.write_text(query_content, encoding='utf-8')

        # 保存或更新元数据
        if metadata:
            metadata_file = task_dir / f'metadata_round{round_num}.json'
            metadata_file.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding='utf-8')

        print(f"✅ [持久化] 查询已保存到: {query_file}")
        return query_file

    except Exception as e:
        print(f"⚠️  [持久化] 保存查询失败: {e}")
        return None


def is_empty_result(sarif_path: Optional[str]) -> bool:
    """
    检测SARIF结果文件是否为空（无数据流路径）。

    Args:
        sarif_path: SARIF输出文件的路径

    Returns:
        如果结果为空返回True，否则返回False
    """
    if not sarif_path:
        return True
    
    sarif_file = Path(sarif_path)
    if not sarif_file.exists():
        return True
    
    try:
        with open(sarif_file, 'r', encoding='utf-8') as f:
            sarif_data = json.load(f)
        
        # 检查SARIF格式的结果
        if isinstance(sarif_data, dict) and 'runs' in sarif_data:
            for run in sarif_data.get('runs', []):
                results = run.get('results', [])
                if results and len(results) > 0:
                    return False
            return True
        
        return True
    except Exception as e:
        print(f"⚠️  [is_empty_result] 解析SARIF文件失败: {e}")
        return True


def count_dataflow_paths(sarif_path: Optional[str] = None, json_path: Optional[str] = None) -> int:
    """
    统计数据流路径数量。

    Args:
        sarif_path: SARIF输出文件的路径
        json_path: JSON格式的路径输出文件

    Returns:
        数据流路径的数量
    """
    count = 0
    
    # 优先检查SARIF文件
    if sarif_path:
        sarif_file = Path(sarif_path)
        if sarif_file.exists():
            try:
                with open(sarif_file, 'r', encoding='utf-8') as f:
                    sarif_data = json.load(f)
                
                if isinstance(sarif_data, dict) and 'runs' in sarif_data:
                    for run in sarif_data.get('runs', []):
                        results = run.get('results', [])
                        count += len(results)
                    return count
            except Exception as e:
                print(f"⚠️  [count_dataflow_paths] 解析SARIF文件失败: {e}")
    
    # 检查JSON路径文件
    if json_path:
        json_file = Path(json_path)
        if json_file.exists():
            try:
                with open(json_file, 'r', encoding='utf-8') as f:
                    json_data = json.load(f)
                
                if isinstance(json_data, list):
                    count += len(json_data)
                elif isinstance(json_data, dict):
                    # 可能是包含paths键的字典
                    paths = json_data.get('paths', [])
                    count += len(paths)
                return count
            except Exception as e:
                print(f"⚠️  [count_dataflow_paths] 解析JSON文件失败: {e}")
    
    return count


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
