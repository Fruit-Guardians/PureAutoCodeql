"""Generate lock files and assemble temporary CodeQL query packs."""

import logging
import textwrap
from datetime import datetime
from pathlib import Path
from typing import Optional

from .language import detect_language_from_query, normalize_language

logger = logging.getLogger(__name__)


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
    logger.debug("create_temporary_qlpack called")
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
