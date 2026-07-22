"""Detect language and resolve source roots inside imported projects."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional

from .filesystem import _copy_dir_contents, _try_extract_zip

logger = logging.getLogger(__name__)


IGNORABLE_TOP_LEVEL_FILES = {
    "readme",
    "readme.md",
    "readme.txt",
    "license",
    "license.txt",
    "license.md",
    ".ds_store",
}
IGNORABLE_TOP_LEVEL_SUFFIXES = {
    ".md",
    ".txt",
    ".rst",
    ".json",
    ".cfg",
    ".ini",
    ".conf",
    ".yml",
    ".yaml",
    ".license",
    ".log",
    ".zip",
    ".tar",
    ".gz",
}


def _resolve_python_source_root(source_code_dir: Path) -> Path:
    current = source_code_dir
    while True:
        entries = [child for child in current.iterdir()]
        direct_py = any(child.is_file() and child.suffix.lower() == ".py" for child in entries)
        if direct_py:
            break

        dir_entries = [child for child in entries if child.is_dir()]
        file_entries = [child for child in entries if child.is_file()]

        non_ignorable_files = [
            child
            for child in file_entries
            if child.suffix.lower() not in IGNORABLE_TOP_LEVEL_SUFFIXES
            and child.name.lower() not in IGNORABLE_TOP_LEVEL_FILES
        ]

        if len(dir_entries) != 1 or non_ignorable_files:
            break

        logger.info("Python项目仅包含单一子目录，继续深入: %s", dir_entries[0].name)
        current = dir_entries[0]

    if current != source_code_dir:
        logger.info("CodeQL Python 源码根目录调整为: %s", current)
    return current

def _resolve_java_source_root(source_code_dir: Path) -> Path:
    """
    解析Java项目的源码根目录。
    如果只有单一子目录且没有.java文件，则深入到该子目录。
    """
    current = source_code_dir
    while True:
        entries = [child for child in current.iterdir()]
        # 检查是否有直接的.java文件
        direct_java = any(child.is_file() and child.suffix.lower() == ".java" for child in entries)
        if direct_java:
            break

        dir_entries = [child for child in entries if child.is_dir()]
        file_entries = [child for child in entries if child.is_file()]

        # 过滤可忽略的文件
        non_ignorable_files = [
            child
            for child in file_entries
            if child.suffix.lower() not in IGNORABLE_TOP_LEVEL_SUFFIXES
            and child.name.lower() not in IGNORABLE_TOP_LEVEL_FILES
        ]

        # 如果只有一个子目录且没有其他重要文件，则继续深入
        if len(dir_entries) != 1 or non_ignorable_files:
            break

        logger.info("Java项目仅包含单一子目录，继续深入: %s", dir_entries[0].name)
        current = dir_entries[0]

    if current != source_code_dir:
        logger.info("CodeQL Java 源码根目录调整为: %s", current)
    return current

def _has_extracted_sources(container_dir: Path) -> bool:
    for entry in container_dir.iterdir():
        if entry.is_dir():
            return True
        if entry.is_file() and entry.suffix.lower() != ".zip":
            return True
    return False

def _prepare_source_code(input_dir: Path, target_source_dir: Path) -> None:
    legacy_source = input_dir / "source_code"
    if legacy_source.exists() and legacy_source.is_dir():
        if _has_extracted_sources(legacy_source):
            logger.info("检测到 legacy source_code 目录，直接复制")
            _copy_dir_contents(legacy_source, target_source_dir)
            return
        if _try_extract_zip(legacy_source, target_source_dir):
            return
        raise ValueError(f"source_code 目录未包含源码或 zip: {legacy_source}")

    src_dir = input_dir / "src"
    if not src_dir.exists() or not src_dir.is_dir():
        raise ValueError(f"Input directory must contain 'source_code' or 'src': {input_dir}")

    if _has_extracted_sources(src_dir):
        logger.info("复制 src 目录内容到 source_code")
        _copy_dir_contents(src_dir, target_source_dir)
        return

    if _try_extract_zip(src_dir, target_source_dir):
        return

    raise ValueError(f"src 目录未包含源码或 zip: {src_dir}")

def _detect_language(source_dir: Path) -> Optional[str]:
    counts = {"python": 0, "java": 0, "cpp": 0}

    if not source_dir.exists():
        return None

    for file_path in source_dir.rglob("*"):
        if not file_path.is_file():
            continue
        ext = file_path.suffix.lower()
        if ext == ".py":
            counts["python"] += 1
        elif ext == ".java":
            counts["java"] += 1
        elif ext in {".c", ".cpp", ".h", ".hpp", ".cc"}:
            counts["cpp"] += 1

    language = max(counts, key=counts.get)
    return language if counts[language] > 0 else None
