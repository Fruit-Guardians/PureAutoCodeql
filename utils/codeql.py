import json
import subprocess
import tempfile
import os
import shutil
import textwrap
from pathlib import Path
from typing import Optional, Dict, List, Any
from datetime import datetime


DEFAULT_CODEQL_PACK_VERSION = "^4.0.17"


# 功能：
def detect_language_from_query(query_content: str) -> str:
    """�򵥵�����ʽ�������Ӳ�ѯ�����м��CodeQL���ԡ�"""
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


def language_to_pack(language: str) -> str:
    """������ӳ�䵽��Ӧ��CodeQL��������"""
    lang = normalize_language(language)
    mapping = {
        'java': 'codeql/java-all',
        'python': 'codeql/python-all',
        'cpp': 'codeql/cpp-all',
    }
    return mapping.get(lang, 'codeql/java-all')


def create_temporary_qlpack(query_content: str, language: Optional[str] = None) -> Path:
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S_%f')
    temp_base_dir = Path('./temp/codeql_temp')
    temp_base_dir.mkdir(parents=True, exist_ok=True)
    pack_dir = temp_base_dir / timestamp
    pack_dir.mkdir(parents=True, exist_ok=True)

    lang = normalize_language(language or detect_language_from_query(query_content))
    dep_pack = language_to_pack(lang)

    qlpack_content = textwrap.dedent(
        f"""\
        name: temp-query-pack
        version: 0.0.0
        library: false
        warnOnImplicitThis: false
        kind: query
        language: {lang}
        dependencies:
          {dep_pack}: "{DEFAULT_CODEQL_PACK_VERSION}"
        """
    )

    # д��qlpack.yml�ļ�
    (pack_dir / 'qlpack.yml').write_text(qlpack_content, encoding='utf-8')

    # ��װ�����Դ�����ȷ�����ļ�
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

    # д���ѯ�ļ�
    sanitized = query_content
    # �Ƴ�UTF-8 BOM
    if sanitized.startswith('\ufeff'):
        sanitized = sanitized.lstrip('\ufeff')
    # �Ƴ�ǰ���հ��кͿո��Ա�Ԫ����ע��λ�ڶ���
    sanitized = sanitized.lstrip()

    query_file = pack_dir / f'query_{timestamp}.ql'
    query_file.write_text(sanitized, encoding='utf-8')

    return query_file


def execute_codeql_query(query_content: str, database_path: str, language: Optional[str] = None) -> Dict[str, Any]:
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
    query_file = None
    pack_dir = None
    sarif_path: Optional[Path] = None
    try:
        query_file = create_temporary_qlpack(query_content, language=language)
        pack_dir = query_file.parent

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

        # 显示进度条
        print("🚀 开始执行CodeQL查询...")
        print("⏳ 查询执行中，请稍候...")
        
        # 创建简单的进度指示器
        import time
        import threading
        
        progress_active = True
        
        def show_progress():
            progress_chars = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"]
            start_time = time.time()
            i = 0
            while progress_active:
                elapsed = time.time() - start_time
                progress = min(int((elapsed / 60) * 100), 99)  # 假设最大60秒
                char = progress_chars[i % len(progress_chars)]
                print(f"\r{char} CodeQL查询执行中... [{progress}%] 已用时: {int(elapsed)}秒", end="", flush=True)
                i += 1
                time.sleep(0.5)
        
        # 启动进度线程
        progress_thread = threading.Thread(target=show_progress)
        progress_thread.daemon = True
        progress_thread.start()
        
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
        
        # 停止进度指示器
        progress_active = False
        time.sleep(0.6)  # 等待进度线程完成最后一次更新
        
        # 显示完成状态
        print(f"\r✅ CodeQL查询执行完成!")
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
