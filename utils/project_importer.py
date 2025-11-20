"""Project import utilities."""

from __future__ import annotations

import logging
import shutil
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional

from api.config import get_config
from utils.case import extract_cve_id

logger = logging.getLogger(__name__)

SUPPORTED_LANGUAGES = {"python", "java", "cpp"}


@dataclass
class ProjectImportResult:
    """Information returned after importing a project."""

    case_id: str
    target_path: str
    language: Optional[str] = None
    metadata_files: List[str] = field(default_factory=list)
    codeql_created: bool = False
    codeql_error: Optional[str] = None


def import_project(
    source_path: str,
    *,
    case_id: Optional[str] = None,
    overwrite: bool = False,
    language: Optional[str] = None,
    create_codeql_db: bool = True,
) -> ProjectImportResult:
    """Import a CVE project directory into the workspace."""

    input_dir = Path(source_path).expanduser().resolve()
    if not input_dir.exists():
        raise FileNotFoundError(f"Input path does not exist: {input_dir}")
    if not input_dir.is_dir():
        raise ValueError(f"Input path must be a directory: {input_dir}")

    inferred_case_id = case_id or _infer_case_id(input_dir)
    if not inferred_case_id:
        raise ValueError("Unable to determine CVE ID. Provide 'case_id' explicitly.")

    config = get_config()
    target_dir = (config.projects_dir / inferred_case_id).resolve()

    if target_dir.exists():
        if not overwrite:
            raise ValueError(
                f"Project {inferred_case_id} already exists. Set overwrite=True to replace it."
            )
        logger.info("Overwriting existing project %s", inferred_case_id)
        shutil.rmtree(target_dir)

    _create_base_structure(target_dir)

    source_dir = _resolve_source_dir(input_dir)
    _copy_tree(source_dir, target_dir / "source_code")

    metadata_files = _copy_metadata_files(input_dir, target_dir)
    _ensure_readme(target_dir, inferred_case_id, input_dir)

    detected_language = (language or _detect_language(target_dir / "source_code") or "python").lower()
    if detected_language not in SUPPORTED_LANGUAGES:
        logger.warning("Unsupported language '%s', defaulting to python", detected_language)
        detected_language = "python"

    codeql_created = False
    codeql_error: Optional[str] = None
    if create_codeql_db:
        codeql_path = shutil.which("codeql")
        if not codeql_path:
            codeql_error = "CodeQL CLI not found in PATH"
            logger.warning(codeql_error)
        else:
            try:
                db_path = target_dir / "db" / detected_language
                _create_codeql_database(target_dir / "source_code", db_path, detected_language)
                codeql_created = True
            except Exception as exc:  # pylint: disable=broad-except
                codeql_error = str(exc)
                logger.error("Failed to create CodeQL database: %s", exc)

    return ProjectImportResult(
        case_id=inferred_case_id,
        target_path=str(target_dir),
        language=detected_language,
        metadata_files=metadata_files,
        codeql_created=codeql_created,
        codeql_error=codeql_error,
    )


def _infer_case_id(input_dir: Path) -> Optional[str]:
    candidates = []

    name_match = extract_cve_id(input_dir.name)
    if name_match:
        candidates.append(name_match)

    for file_path in input_dir.glob("CVE-*.*"):
        match = extract_cve_id(file_path.name)
        if match:
            candidates.append(match)

    if not candidates:
        return None

    candidates.sort()
    return candidates[0]


def _resolve_source_dir(input_dir: Path) -> Path:
    source_dir = input_dir / "source_code"
    if not source_dir.exists() or not source_dir.is_dir():
        raise ValueError(f"Input directory must contain 'source_code': {input_dir}")
    return source_dir


def _create_base_structure(target_dir: Path) -> None:
    for child in ["source_code", "db", "inputs", "intel"]:
        (target_dir / child).mkdir(parents=True, exist_ok=True)


def _copy_tree(src: Path, dst: Path) -> None:
    shutil.copytree(src, dst, dirs_exist_ok=True)


def _copy_metadata_files(input_dir: Path, target_dir: Path) -> List[str]:
    metadata_files: List[str] = []
    db_dir = target_dir / "db"
    inputs_dir = target_dir / "inputs"

    for file_path in sorted(input_dir.glob("CVE-*.*")):
        if file_path.suffix.lower() not in {".json", ".diff", ".patch"}:
            continue
        for destination in (db_dir, inputs_dir):
            shutil.copy2(file_path, destination / file_path.name)
        metadata_files.append(file_path.name)

    if not metadata_files:
        logger.warning("No CVE metadata files found under %s", input_dir)

    return metadata_files


def _ensure_readme(target_dir: Path, case_id: str, source_dir: Path) -> None:
    readme_path = target_dir / "README.md"
    if readme_path.exists():
        return
    content = f"# {case_id}\n\nImported from {source_dir}\n"
    readme_path.write_text(content, encoding="utf-8")


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


def _create_codeql_database(source_root: Path, db_path: Path, language: str) -> None:
    cmd = [
        "codeql",
        "database",
        "create",
        str(db_path),
        f"--language={language}",
        f"--source-root={source_root}",
        "--overwrite",
    ]

    logger.info("Running CodeQL command: %s", " ".join(cmd))
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)

    if result.returncode != 0:
        stderr = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"CodeQL database creation failed: {stderr}")

    logger.info("CodeQL database created at %s", db_path)


__all__ = ["import_project", "ProjectImportResult"]

