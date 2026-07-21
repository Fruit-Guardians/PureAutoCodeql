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
import sys


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


def resolve_codeql_database_root(path: str, language: Optional[str] = None) -> str:
    """
    解析真正的CodeQL数据库根目录。
    如果给定路径本身包含 codeql-database.yml，则返回该路径。
    如果给定路径不包含，但其子目录（如 python/ 或 cpp/）包含，则返回子目录路径。
    如果指定了language，则优先查找名称匹配的子目录。
    
    支持的格式：
    - {path}/codeql-database.yml (直接路径)
    - {path}/{language}/codeql-database.yml (例如: db/cpp, db/python, db/java)
    - {path}/db-{language}/codeql-database.yml (例如: db-java, db-cpp)
    - {path}/db/{language}/codeql-database.yml (例如: projects/CVE-xxx/db/cpp)
    """
    if not path:
        return path
        
    db_path = Path(path)
    if not db_path.exists():
        return path
        
    # 如果路径本身就是数据库根目录
    if (db_path / "codeql-database.yml").exists():
        return str(db_path)
        
    # 检查子目录
    try:
        # 如果指定了语言，优先检查对应的子目录
        if language:
            lang_lower = language.lower().strip()
            # 处理一些常见的语言名称变体
            lang_map = {
                'c': 'cpp', 'c++': 'cpp', 'cplusplus': 'cpp',
                'c#': 'csharp', 'cs': 'csharp',
                'js': 'javascript', 'ts': 'javascript', 'typescript': 'javascript'
            }
            target_lang = lang_map.get(lang_lower, lang_lower)
            
            # 尝试查找精确匹配的子目录或 db-{lang} 格式
            # 优先级：直接子目录 > db-{lang} > db/{lang}
            candidates = [
                db_path / target_lang,  # 例如: db_path/cpp
                db_path / f"db-{target_lang}",  # 例如: db_path/db-cpp
                db_path / "db" / target_lang,  # 例如: db_path/db/cpp
            ]
            
            for candidate in candidates:
                if candidate.is_dir() and (candidate / "codeql-database.yml").exists():
                    return str(candidate)
            
            # 如果没找到，尝试在 db/{lang}/db-{lang} 这样的嵌套结构中查找
            nested_candidate = db_path / "db" / target_lang / f"db-{target_lang}"
            if nested_candidate.is_dir() and (nested_candidate / "codeql-database.yml").exists():
                return str(nested_candidate)

        # 如果没指定语言或没找到特定语言目录，则遍历一级子目录
        for subdir in db_path.iterdir():
            if subdir.is_dir() and (subdir / "codeql-database.yml").exists():
                return str(subdir)
        
        # 尝试深入一层 (例如 db/python/codeql-database.yml 或 db/cpp/codeql-database.yml)
        db_subdir = db_path / "db"
        if db_subdir.is_dir():
            # 先检查 db 下的直接子目录
            for subdir in db_subdir.iterdir():
                if subdir.is_dir() and (subdir / "codeql-database.yml").exists():
                    return str(subdir)
            
            # 再检查 db/{lang}/db-{lang} 这样的嵌套结构
            for lang_subdir in db_subdir.iterdir():
                if lang_subdir.is_dir():
                    nested_db = lang_subdir / f"db-{lang_subdir.name}"
                    if nested_db.is_dir() and (nested_db / "codeql-database.yml").exists():
                        return str(nested_db)
                    
    except Exception:
        pass
            
    return path


def validate_codeql_database(database_path: str, language: Optional[str] = None) -> Tuple[bool, str]:
    """
    验证CodeQL数据库是否存在且有效。

    Args:
        database_path: CodeQL数据库的路径
        language: 可选的语言提示，用于辅助定位数据库子目录

    Returns:
        (is_valid, error_message) 元组：
        - is_valid: 数据库是否有效
        - error_message: 如果无效，包含详细的错误信息；如果有效，为空字符串
    """
    if not database_path:
        return False, "数据库路径为空。请提供有效的CodeQL数据库路径。"

    # 尝试解析真实的数据库根目录
    real_db_path_str = resolve_codeql_database_root(database_path, language)
    db_path = Path(real_db_path_str)

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
        # 尝试使用 codeql resolve database 命令验证
        try:
            result = subprocess.run(
                ['codeql', 'resolve', 'database', str(db_path)],
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

    try:
        open(query_file,'w').write(query_content)
        
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
        import threading
        process = subprocess.Popen(
            [
                'codeql', 'query', 'run',
                str(query_file),
                '--database', resolved_database_path,
                f'--output={str(bqrs_path)}',
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        stdout_lines = []
        stderr_lines = []

        # 实时读取并输出 stdout 和 stderr
        def read_stdout():
            for line in process.stdout:
                stdout_lines.append(line)
                print(line, end='', flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        def read_stderr():
            for line in process.stderr:
                stderr_lines.append(line)
                print(line, end='', file=sys.stderr, flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        stdout_thread = threading.Thread(target=read_stdout)
        stderr_thread = threading.Thread(target=read_stderr)
        stdout_thread.start()
        stderr_thread.start()

        # 等待进程完成
        returncode = process.wait(timeout=600)
        stdout_thread.join()
        stderr_thread.join()

        stdout = ''.join(stdout_lines)
        stderr = ''.join(stderr_lines)

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
                enhanced_error = (
                    f"数据库错误:\n"
                    f"{combined_error}\n\n"
                    f"建议:\n"
                    f"1. 检查数据库路径是否正确: {database_path}\n"
                    f"2. 使用 'codeql database info {database_path}' 验证数据库\n"
                    f"3. 如果数据库不存在或已损坏，请使用 'codeql database create' 重新创建"
                )
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
        decode_process = subprocess.Popen(
            [
                'codeql', 'bqrs', 'decode',
                '--format=json',
                str(bqrs_path),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        decode_stdout_lines = []
        decode_stderr_lines = []

        # 实时读取并输出解码过程的 stdout 和 stderr
        def read_decode_stdout():
            for line in decode_process.stdout:
                decode_stdout_lines.append(line)
                print(line, end='', flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        def read_decode_stderr():
            for line in decode_process.stderr:
                decode_stderr_lines.append(line)
                print(line, end='', file=sys.stderr, flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        decode_stdout_thread = threading.Thread(target=read_decode_stdout)
        decode_stderr_thread = threading.Thread(target=read_decode_stderr)
        decode_stdout_thread.start()
        decode_stderr_thread.start()

        # 等待解码进程完成
        decode_returncode = decode_process.wait(timeout=300)
        decode_stdout_thread.join()
        decode_stderr_thread.join()

        decode_stdout = ''.join(decode_stdout_lines)
        decode_stderr = ''.join(decode_stderr_lines)

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
    try:
        open(query_file,'w').write(query_content)
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
        import threading
        process = subprocess.Popen(
            [
                'codeql', 'database', 'analyze',
                '--verbose',
                resolved_database_path,
                str(query_file),
                '--rerun',
                '--format=sarifv2.1.0',
                f'--output={str(sarif_path)}',
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        stdout_lines = []
        stderr_lines = []

        # 实时读取并输出 stdout 和 stderr
        def read_stdout():
            for line in process.stdout:
                stdout_lines.append(line)
                print(line, end='', flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        def read_stderr():
            for line in process.stderr:
                stderr_lines.append(line)
                print(line, end='', file=sys.stderr, flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        stdout_thread = threading.Thread(target=read_stdout)
        stderr_thread = threading.Thread(target=read_stderr)
        stdout_thread.start()
        stderr_thread.start()

        # 等待进程完成
        returncode = process.wait(timeout=600)
        stdout_thread.join()
        stderr_thread.join()

        stdout = ''.join(stdout_lines)
        stderr = ''.join(stderr_lines)

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
        import threading
        process = subprocess.Popen(
            [
                'codeql', 'query', 'run',
                '--verbose',
                str(query_file),
                '--database', resolved_database_path,
                f'--output={str(bqrs_path)}',
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        stdout_lines = []
        stderr_lines = []

        # 实时读取并输出 stdout 和 stderr
        def read_stdout():
            for line in process.stdout:
                stdout_lines.append(line)
                print(line, end='', flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        def read_stderr():
            for line in process.stderr:
                stderr_lines.append(line)
                print(line, end='', file=sys.stderr, flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        stdout_thread = threading.Thread(target=read_stdout)
        stderr_thread = threading.Thread(target=read_stderr)
        stdout_thread.start()
        stderr_thread.start()

        # 等待进程完成
        returncode = process.wait(timeout=600)
        stdout_thread.join()
        stderr_thread.join()

        stdout = ''.join(stdout_lines)
        stderr = ''.join(stderr_lines)

        if returncode != 0:
            parts: List[str] = []
            if stderr:
                parts.append(stderr.strip())
            if stdout:
                parts.append(stdout.strip())

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

        # 执行 codeql bqrs decode，添加 --verbose 参数
        log_timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(f"\n{'='*80}\n")
            f.write(f"[{log_timestamp}] 执行 codeql bqrs decode\n")
            f.write(f"{'='*80}\n")

        decode_process = subprocess.Popen(
            [
                'codeql', 'bqrs', 'decode',
                '--verbose',
                '--format=table',
                str(bqrs_path),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )

        decode_stdout_lines = []
        decode_stderr_lines = []

        # 实时读取并输出 stdout 和 stderr
        def read_decode_stdout():
            for line in decode_process.stdout:
                decode_stdout_lines.append(line)
                print(line, end='', flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        def read_decode_stderr():
            for line in decode_process.stderr:
                decode_stderr_lines.append(line)
                print(line, end='', file=sys.stderr, flush=True)
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)

        decode_stdout_thread = threading.Thread(target=read_decode_stdout)
        decode_stderr_thread = threading.Thread(target=read_decode_stderr)
        decode_stdout_thread.start()
        decode_stderr_thread.start()

        # 等待进程完成
        decode_returncode = decode_process.wait(timeout=300)
        decode_stdout_thread.join()
        decode_stderr_thread.join()

        decode_stdout = ''.join(decode_stdout_lines)
        decode_stderr = ''.join(decode_stderr_lines)

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
