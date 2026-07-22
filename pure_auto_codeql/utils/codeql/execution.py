"""Run CodeQL queries against a database and decode results."""

import json
import subprocess
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

from .database import (
    _format_db_error,
    is_database_error,
    resolve_codeql_database_root,
    validate_codeql_database,
)
from .qlpack import create_temporary_qlpack
from .subprocess_runner import _stream_subprocess


def run_simple_query(
    query_content: str,
    database_path: str,
    language: Optional[str] = None,
    query_file: Optional[Path] = None,
    output_dir: Optional[Path] = None,
) -> Dict[str, Any]:
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
    # 在执行前验证数据库并解析真正的数据库路径
    resolved_database_path = resolve_codeql_database_root(database_path, language)
    is_valid, validation_error = validate_codeql_database(resolved_database_path, language)
    if not is_valid:
        return {
            'success': False,
            'output': f"数据库验证失败:\n{validation_error}",
            'results': [],
            'result_file': None,
            'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
        }

    if query_file is None:
        return {
            'success': False,
            'output': 'run_simple_query 需要提供 query_file 路径。',
            'results': [],
            'result_file': None,
            'sarif_path': None,
        }
    try:
        Path(query_file).write_text(query_content, encoding='utf-8')

        # 准备输出目录
        output_dir = Path(output_dir) if output_dir else Path('./output')
        try:
            output_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            # 如果/output不可写，则回退到当前工作目录
            output_dir = Path.cwd() / 'output'
            output_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        bqrs_path = output_dir / f'result_{timestamp}.bqrs'

        # 确定日志目录（从 query_file 路径提取）
        log_dir = query_file.parent if query_file else Path('./temp/codeql_temp') / datetime.now().strftime('%Y%m%d_%H%M%S_%f')
        log_dir.mkdir(parents=True, exist_ok=True)
        log_file = log_dir / 'codeql_simple_verbose.log'

        # 写入命令信息到日志文件头部
        log_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(f"\n{'='*80}\n")
            f.write(f"[{log_timestamp}] 执行 codeql query run (简单查询)\n")
            f.write(f"原始数据库路径: {database_path}\n")
            f.write(f"解析后数据库路径: {resolved_database_path}\n")
            f.write(f"{'='*80}\n")

        start_time = time.time()

        # 使用`codeql query run`执行简单查询，添加实时日志输出
        returncode, stdout, stderr = _stream_subprocess(
            [
                'codeql', 'query', 'run',
                str(query_file),
                '--database', resolved_database_path,
                f'--output={str(bqrs_path)}',
            ],
            log_file,
            timeout=600,
        )

        # 计算并显示实际执行时间
        execution_time = time.time() - start_time
        print(f"✅ CodeQL简单查询执行完成! 用时: {execution_time:.2f}秒")
        print("📊 正在处理查询结果...")

        if returncode != 0:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if stderr:
                error_output.append(stderr.strip())
            if stdout:
                error_output.append(stdout.strip())

            combined_error = '\n'.join(filter(None, error_output))

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = _format_db_error(combined_error, database_path)
                print(f"CodeQL简单查询执行失败，数据库错误: \n {enhanced_error}")
                return {
                    'success': False,
                    'output': enhanced_error,
                    'results': [],
                    'result_file': None,
                    'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
                }

            print(f"CodeQL简单查询执行失败，错误: \n {combined_error}")
            return {
                'success': False,
                'output': combined_error or 'Unknown CodeQL execution error',
                'results': [],
                'result_file': None,
                'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
            }

        # 解码BQRS文件为JSON格式，以便detect_breakpoints方法能够正确解析
        decode_returncode, decode_stdout, decode_stderr = _stream_subprocess(
            [
                'codeql', 'bqrs', 'decode',
                '--format=json',
                str(bqrs_path),
            ],
            log_file,
            timeout=300,
        )

        if decode_returncode != 0:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if decode_stderr:
                error_output.append(decode_stderr.strip())
            if decode_stdout:
                error_output.append(decode_stdout.strip())

            combined_error = '\n'.join(filter(None, error_output))
            print(f"BQRS解码失败: {combined_error}")
            return {
                'success': False,
                'output': f"BQRS解码失败: {combined_error}",
                'results': [],
                'result_file': None,
                'sarif_path': None,  # 简单查询不生成SARIF文件，保持兼容性
            }

        # 保存结果到文件
        result_file = output_dir / f'result_{timestamp}.txt'
        result_content = decode_stdout or 'No results.'
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


def execute_codeql_query(
    query_content: str,
    database_path: str,
    language: Optional[str] = None,
    query_file: Optional[Path] = None,
    alert: Optional[str] = None,
    output_dir: Optional[Path] = None,
) -> Dict[str, Any]:
    """
    对指定的数据库执行CodeQL查询。

    注意: 此函数会添加 --verbose 参数到 CodeQL 命令，实时输出执行进度到控制台，
    并将详细日志保存到 temp/codeql_temp/<task_id>/codeql_verbose.log。

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
    # 在执行前验证数据库并解析真正的数据库路径
    resolved_database_path = resolve_codeql_database_root(database_path, language)
    is_valid, validation_error = validate_codeql_database(resolved_database_path, language)
    if not is_valid:
        return {
            'success': False,
            'output': f"数据库验证失败:\n{validation_error}",
            'results': [],
            'sarif_path': None,
        }

    # 如果指定了alert参数，执行简单查询（使用解析后的路径）
    if alert == 'alert':
        return run_simple_query(query_content, resolved_database_path, language, query_file, output_dir)

    sarif_path: Optional[Path] = None
    if query_file is None:
        return {
            'success': False,
            'output': 'execute_codeql_query 需要提供 query_file 路径。',
            'results': [],
            'sarif_path': None,
        }
    try:
        Path(query_file).write_text(query_content, encoding='utf-8')
        # 准备标准化的SARIF输出路径
        output_dir = Path(output_dir) if output_dir else Path('./output')
        try:
            output_dir.mkdir(parents=True, exist_ok=True)
        except Exception:
            # 如果/output不可写，则回退到当前工作目录
            output_dir = Path.cwd() / 'output'
            output_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        sarif_path = output_dir / f'result_{timestamp}.sarif'

        start_time = time.time()

        # 确定日志目录（从 query_file 路径提取）
        log_dir = query_file.parent if query_file else Path('./temp/codeql_temp') / datetime.now().strftime('%Y%m%d_%H%M%S_%f')
        log_dir.mkdir(parents=True, exist_ok=True)
        log_file = log_dir / 'codeql_verbose.log'

        # 写入命令信息到日志文件头部
        log_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(f"\n{'='*80}\n")
            f.write(f"[{log_timestamp}] 执行 codeql database analyze\n")
            f.write(f"原始数据库路径: {database_path}\n")
            f.write(f"解析后数据库路径: {resolved_database_path}\n")
            f.write(f"{'='*80}\n")

        # 使用`codeql database analyze`执行查询，并使用SARIF v2.1.0输出，添加 --verbose 参数
        returncode, stdout, stderr = _stream_subprocess(
            [
                'codeql', 'database', 'analyze',
                '--verbose',
                resolved_database_path,
                str(query_file),
                '--rerun',
                '--format=sarifv2.1.0',
                f'--output={str(sarif_path)}',
            ],
            log_file,
            timeout=600,
        )

        # 计算并显示实际执行时间
        execution_time = time.time() - start_time
        print(f"✅ CodeQL查询执行完成! 用时: {execution_time:.2f}秒")
        print("📊 正在处理查询结果...")

        if returncode == 0:
            return {
                'success': True,
                'output': 'Query executed successfully',
                'results': [],
                'sarif_path': str(sarif_path) if sarif_path else None,
            }
        else:
            # 合并stderr和stdout以捕获所有错误信息
            error_output = []
            if stderr:
                error_output.append(stderr.strip())
            if stdout:
                error_output.append(stdout.strip())

            combined_error = '\n'.join(filter(None, error_output))

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = _format_db_error(combined_error, database_path)
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


def run_query_and_decode_to_text(
    query_content: str,
    database_path: str,
    language: Optional[str] = None,
    output_dir: Optional[str] = None,
) -> Dict[str, Any]:
    """
    执行 CodeQL 查询并解码为文本格式。

    注意: 此函数会添加 --verbose 参数到 CodeQL 命令，实时输出执行进度到控制台，
    并将详细日志保存到 temp/codeql_temp/<task_id>/codeql_verbose.log。

    Args:
        query_content: CodeQL 查询内容
        database_path: CodeQL 数据库路径
        language: 可选的显式语言
        output_dir: 可选的输出目录

    Returns:
        包含以下内容的字典：
        - success (bool): 执行是否成功
        - output (str): 查询输出或错误消息
        - result_file (str): 结果文件路径
    """
    # 在执行前验证数据库并解析真正的数据库路径
    resolved_database_path = resolve_codeql_database_root(database_path, language)
    is_valid, validation_error = validate_codeql_database(resolved_database_path, language)
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

        # 确保日志目录存在
        log_file = pack_dir / 'codeql_verbose.log'
        log_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(f"\n{'='*80}\n")
            f.write(f"[{log_timestamp}] 执行 codeql query run\n")
            f.write(f"原始数据库路径: {database_path}\n")
            f.write(f"解析后数据库路径: {resolved_database_path}\n")
            f.write(f"{'='*80}\n")

        # 执行 codeql query run，添加 --verbose 参数
        returncode, stdout, stderr = _stream_subprocess(
            [
                'codeql', 'query', 'run',
                '--verbose',
                str(query_file),
                '--database', resolved_database_path,
                f'--output={str(bqrs_path)}',
            ],
            log_file,
            timeout=600,
        )

        if returncode != 0:
            parts: List[str] = []
            if stderr:
                parts.append(stderr.strip())
            if stdout:
                parts.append(stdout.strip())

            combined_error = '\n'.join(filter(None, parts)) or 'Unknown CodeQL run error'

            # 检查是否为数据库相关错误
            if is_database_error(combined_error):
                enhanced_error = _format_db_error(combined_error, database_path)
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

        # 执行 codeql bqrs decode，添加 --verbose 参数
        log_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(f"\n{'='*80}\n")
            f.write(f"[{log_timestamp}] 执行 codeql bqrs decode\n")
            f.write(f"{'='*80}\n")

        decode_returncode, decode_stdout, decode_stderr = _stream_subprocess(
            [
                'codeql', 'bqrs', 'decode',
                '--verbose',
                '--format=table',
                str(bqrs_path),
            ],
            log_file,
            timeout=300,
        )

        if decode_returncode != 0:
            parts: List[str] = []
            if decode_stderr:
                parts.append(decode_stderr.strip())
            if decode_stdout:
                parts.append(decode_stdout.strip())
            return {
                'success': False,
                'output': '\n'.join(filter(None, parts)) or 'Unknown BQRS decode error',
                'result_file': None,
            }

        content = decode_stdout or ''
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
