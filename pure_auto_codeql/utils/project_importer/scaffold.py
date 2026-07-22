"""Create base directory structure and copy metadata into a case."""

from __future__ import annotations

import logging
import shutil
from pathlib import Path
from typing import List

from ..case import extract_cve_id

logger = logging.getLogger(__name__)


def _create_base_structure(target_dir: Path) -> None:
    for child in ["source_code", "db", "inputs", "intel"]:
        (target_dir / child).mkdir(parents=True, exist_ok=True)

def _copy_metadata_files(input_dir: Path, target_dir: Path, case_id: str) -> List[str]:
    metadata_files: List[str] = []
    db_dir = target_dir / "db"
    inputs_dir = target_dir / "inputs"
    patch_counter = 1
    input_root = input_dir.resolve()

    def iter_metadata_sources():
        yield from sorted(input_dir.glob("CVE-*.*"))
        patch_dir = input_dir / "patch"
        if patch_dir.exists() and patch_dir.is_dir():
            yield from sorted(patch_dir.glob("*.patch"))
            yield from sorted(patch_dir.glob("*.diff"))

    for file_path in iter_metadata_sources():
        suffix = file_path.suffix.lower()
        if suffix not in {".json", ".diff", ".patch"}:
            continue
        try:
            resolved_file = file_path.resolve()
        except OSError:
            logger.warning("Skipping unresolvable metadata file: %s", file_path)
            continue
        if file_path.is_symlink() and not resolved_file.is_relative_to(input_root):
            logger.warning(
                "Skipping metadata symlink outside import root: %s -> %s",
                file_path,
                resolved_file,
            )
            continue

        destination_name = file_path.name
        if suffix in {".diff", ".patch"}:
            if not extract_cve_id(destination_name):
                destination_name = f"{case_id}-patch-{patch_counter:02d}{suffix}"
                patch_counter += 1

        for destination in (db_dir, inputs_dir):
            shutil.copy2(file_path, destination / destination_name)
        metadata_files.append(destination_name)

    if not metadata_files:
        logger.warning("No CVE metadata files found under %s", input_dir)

    return metadata_files

def _ensure_readme(target_dir: Path, case_id: str, source_dir: Path) -> None:
    readme_path = target_dir / "README.md"
    if readme_path.exists():
        return
    content = f"# {case_id}\n\nImported from {source_dir}\n"
    readme_path.write_text(content, encoding="utf-8")
