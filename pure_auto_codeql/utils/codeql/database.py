"""Locate, validate, and diagnose CodeQL databases."""

import subprocess
from pathlib import Path
from typing import Optional, Tuple


def _format_db_error(combined_error: str, database_path: str) -> str:
    """构建带排查建议的数据库错误提示（多个执行函数共用）。"""
    return (
        f"数据库错误:\n"
        f"{combined_error}\n\n"
        f"建议:\n"
        f"1. 检查数据库路径是否正确: {database_path}\n"
        f"2. 使用 'codeql database info {database_path}' 验证数据库\n"
        f"3. 如果数据库不存在或已损坏，请使用 'codeql database create' 重新创建"
    )


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
