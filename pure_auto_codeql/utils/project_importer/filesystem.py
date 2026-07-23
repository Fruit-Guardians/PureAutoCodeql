"""Filesystem helpers: safe delete, copy, and zip extraction."""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Set

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class ImportLimits:
    max_files: int = 50_000
    max_total_bytes: int = 5 * 1024**3
    max_compressed_bytes: int = 2 * 1024**3
    max_single_file_bytes: int = 512 * 1024**2
    max_compression_ratio: float = 200.0
    max_path_depth: int = 40
    max_nested_archives: int = 0


DEFAULT_IMPORT_LIMITS = ImportLimits()


def _validate_import_tree(source: Path, limits: ImportLimits = DEFAULT_IMPORT_LIMITS) -> None:
    files = 0
    total_bytes = 0
    for entry in source.rglob("*"):
        if entry.is_symlink() or not entry.is_file():
            continue
        files += 1
        size = entry.stat().st_size
        total_bytes += size
        if files > limits.max_files:
            raise ValueError(f"Import contains more than {limits.max_files} files")
        if size > limits.max_single_file_bytes:
            raise ValueError(f"Import file exceeds size limit: {entry}")
        if total_bytes > limits.max_total_bytes:
            raise ValueError("Import exceeds total uncompressed size limit")
        if len(entry.relative_to(source).parts) > limits.max_path_depth:
            raise ValueError(f"Import path nesting is too deep: {entry}")


def _safe_rmtree(path: Path) -> None:
    """
    Windows 兼容的目录删除函数，处理 CodeQL 创建的符号链接。

    Args:
        path: 要删除的目录路径
    """
    if not path.exists():
        return

    # Windows 上需要特殊处理符号链接
    if sys.platform == "win32":
        try:
            # 先尝试删除所有符号链接
            for root, dirs, files in os.walk(path, topdown=False):
                root_path = Path(root)

                # 处理文件和目录中的符号链接
                for name in dirs + files:
                    item_path = root_path / name
                    if item_path.is_symlink():
                        try:
                            item_path.unlink()
                            logger.debug(f"删除符号链接: {item_path}")
                        except Exception as e:
                            logger.warning(f"无法删除符号链接 {item_path}: {e}")
                            # 尝试使用 Windows 命令
                            if item_path.is_dir():
                                subprocess.run(["cmd", "/c", "rmdir", str(item_path)],
                                             check=False, capture_output=True)
                            else:
                                subprocess.run(["cmd", "/c", "del", "/f", str(item_path)],
                                             check=False, capture_output=True)
        except Exception as e:
            logger.warning(f"清理符号链接时出错: {e}")

    # 使用标准方法删除目录
    try:
        shutil.rmtree(path)
    except Exception as e:
        logger.error(f"删除目录失败 {path}: {e}")
        # Windows 最后的杀手锏：使用 cmd 强制删除
        if sys.platform == "win32":
            try:
                subprocess.run(["cmd", "/c", "rmdir", "/s", "/q", str(path)],
                             check=True, capture_output=True)
                logger.info(f"使用 Windows cmd 成功删除目录: {path}")
            except subprocess.CalledProcessError as cmd_error:
                raise OSError(f"无法删除目录 {path}: {e}") from cmd_error
        else:
            raise

def _reset_directory(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)

def _copy_dir_contents(src: Path, dst: Path) -> None:
    _validate_import_tree(src)
    _reset_directory(dst)
    source_root = src.resolve()
    for entry in src.iterdir():
        _copy_entry_safely(entry, dst / entry.name, source_root, {source_root})

def _copy_entry_safely(
    entry: Path,
    destination: Path,
    source_root: Path,
    seen_dirs: Set[Path],
) -> None:
    """Copy source files without following symlinks outside the import root."""

    if entry.is_symlink():
        try:
            resolved_entry = entry.resolve()
        except OSError:
            logger.warning("Skipping broken symlink during import: %s", entry)
            return
        if not resolved_entry.is_relative_to(source_root):
            logger.warning("Skipping symlink outside import root: %s -> %s", entry, resolved_entry)
            return

        if resolved_entry.is_dir():
            if resolved_entry in seen_dirs:
                logger.warning("Skipping recursive symlink during import: %s -> %s", entry, resolved_entry)
                return
            destination.mkdir(parents=True, exist_ok=True)
            next_seen = seen_dirs | {resolved_entry}
            for child in resolved_entry.iterdir():
                _copy_entry_safely(child, destination / child.name, source_root, next_seen)
        elif resolved_entry.is_file():
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(resolved_entry, destination)
        return

    try:
        resolved_entry = entry.resolve()
    except OSError:
        logger.warning("Skipping unresolvable path during import: %s", entry)
        return

    if not resolved_entry.is_relative_to(source_root):
        logger.warning("Skipping path outside import root: %s -> %s", entry, resolved_entry)
        return

    if entry.is_dir():
        if resolved_entry in seen_dirs:
            logger.warning("Skipping recursive directory during import: %s", entry)
            return
        destination.mkdir(parents=True, exist_ok=True)
        next_seen = seen_dirs | {resolved_entry}
        for child in entry.iterdir():
            _copy_entry_safely(child, destination / child.name, source_root, next_seen)
    elif entry.is_file():
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(entry, destination)

def _safe_extract_zip(
    zip_path: Path,
    dst: Path,
    limits: ImportLimits = DEFAULT_IMPORT_LIMITS,
) -> None:
    if zip_path.stat().st_size > limits.max_compressed_bytes:
        raise ValueError("Compressed archive exceeds size limit")
    _reset_directory(dst)
    with zipfile.ZipFile(zip_path, "r") as archive:
        members = archive.infolist()
        if len(members) > limits.max_files:
            raise ValueError(f"Archive contains more than {limits.max_files} entries")
        total_size = sum(member.file_size for member in members)
        compressed_size = sum(member.compress_size for member in members)
        if total_size > limits.max_total_bytes:
            raise ValueError("Archive exceeds total uncompressed size limit")
        ratio = total_size / max(compressed_size, 1)
        if ratio > limits.max_compression_ratio:
            raise ValueError("Archive compression ratio exceeds safety limit")
        nested_archives = 0
        for member in members:
            member_path = Path(member.filename)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError(f"Unsafe path detected in zip archive: {member.filename}")
            if len(member_path.parts) > limits.max_path_depth:
                raise ValueError(f"Archive path nesting is too deep: {member.filename}")
            if member.file_size > limits.max_single_file_bytes:
                raise ValueError(f"Archive member exceeds size limit: {member.filename}")
            if member_path.suffix.lower() in {".zip", ".jar", ".war", ".ear"}:
                nested_archives += 1
                if nested_archives > limits.max_nested_archives:
                    raise ValueError(f"Nested archives are not allowed: {member.filename}")
            file_type = (member.external_attr >> 16) & 0o170000
            if file_type == 0o120000:
                raise ValueError(f"Symlink entries are not allowed in zip archive: {member.filename}")
        archive.extractall(dst)

    extracted_root = dst.resolve()
    for extracted in dst.rglob("*"):
        try:
            if not extracted.resolve().is_relative_to(extracted_root):
                raise ValueError(f"Unsafe extracted path detected: {extracted}")
        except OSError as exc:
            raise ValueError(f"Unable to validate extracted path: {extracted}") from exc
    logger.info("解压源码包: %s -> %s", zip_path, dst)

def _try_extract_zip(container_dir: Path, target_source_dir: Path) -> bool:
    zip_candidates = sorted(
        child for child in container_dir.iterdir() if child.is_file() and child.suffix.lower() == ".zip"
    )
    if not zip_candidates:
        return False
    if len(zip_candidates) > 1:
        logger.warning("检测到多个zip源码包，仅处理第一个: %s", zip_candidates[0])
    _safe_extract_zip(zip_candidates[0], target_source_dir)
    return True
