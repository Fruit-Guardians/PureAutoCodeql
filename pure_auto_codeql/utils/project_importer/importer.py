"""Top-level project import orchestration."""

from __future__ import annotations

import logging
import re  # noqa: F401  (kept for annotations parity)
import shutil
from pathlib import Path
from typing import Optional

from pure_auto_codeql.api.config import get_config

from ..case import extract_cve_id
from ._constants import SAFE_CASE_ID_PATTERN, SUPPORTED_LANGUAGES
from .build import _resolve_cpp_build_plan
from .database import _create_codeql_database
from .filesystem import _safe_rmtree
from .models import BuildPlan, ProjectImportResult
from .scaffold import _copy_metadata_files, _create_base_structure, _ensure_readme
from .source_layout import (
    _detect_language,
    _prepare_source_code,
    _resolve_java_source_root,
    _resolve_python_source_root,
)

logger = logging.getLogger(__name__)


def import_project(
    source_path: str,
    *,
    case_id: Optional[str] = None,
    overwrite: bool = False,
    language: Optional[str] = None,
    create_codeql_db: bool = True,
    build_command: Optional[str] = None,
    build_script: Optional[str] = None,
    build_workdir: Optional[str] = None,
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
    if not SAFE_CASE_ID_PATTERN.fullmatch(inferred_case_id):
        raise ValueError(
            "Invalid case_id. Use 1-128 characters from letters, numbers, '.', '_' and '-'."
        )

    config = get_config()
    projects_root = config.projects_dir.resolve()
    target_dir = (projects_root / inferred_case_id).resolve()
    if not target_dir.is_relative_to(projects_root):
        raise ValueError(f"Target path escapes projects directory: {target_dir}")

    if target_dir.exists():
        if not overwrite:
            raise ValueError(
                f"Project {inferred_case_id} already exists. Set overwrite=True to replace it."
            )
        logger.info("Overwriting existing project %s", inferred_case_id)
        _safe_rmtree(target_dir)

    _create_base_structure(target_dir)
    source_code_dir = target_dir / "source_code"
    _prepare_source_code(input_dir, source_code_dir)

    metadata_files = _copy_metadata_files(input_dir, target_dir, inferred_case_id)
    _ensure_readme(target_dir, inferred_case_id, input_dir)

    detected_language = (language or _detect_language(target_dir / "source_code") or "python").lower()
    if detected_language not in SUPPORTED_LANGUAGES:
        logger.warning("Unsupported language '%s', defaulting to python", detected_language)
        detected_language = "python"

    codeql_created = False
    codeql_error: Optional[str] = None
    resolved_build: Optional[BuildPlan] = None
    if create_codeql_db:
        codeql_path = shutil.which("codeql")
        if not codeql_path:
            codeql_error = "CodeQL CLI not found in PATH"
            logger.warning(codeql_error)
        else:
            try:
                db_path = target_dir / "db" / detected_language
                source_root = source_code_dir
                if detected_language == "python":
                    source_root = _resolve_python_source_root(source_code_dir)
                elif detected_language == "java":
                    # Java 不需要构建计划，使用 --build-mode=none
                    logger.info("Java项目检测到，将使用 --build-mode=none 创建数据库")
                    source_root = _resolve_java_source_root(source_code_dir)
                elif detected_language == "cpp":
                    resolved_build = _resolve_cpp_build_plan(
                        target_dir=target_dir,
                        source_dir=target_dir / "source_code",
                        user_command=build_command,
                        build_script=build_script,
                        user_workdir=build_workdir,
                    )
                _create_codeql_database(
                    source_root,
                    db_path,
                    detected_language,
                    build_plan=resolved_build,
                )
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
        build_command=resolved_build.command if resolved_build else build_command,
        build_workdir=str(resolved_build.working_dir) if resolved_build else build_workdir,
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
        # 如果无法提取CVE ID，则使用目录名作为Case ID
        # 替换非法字符以确保路径安全
        import re
        safe_name = re.sub(r'[^a-zA-Z0-9\-_]', '_', input_dir.name)
        logger.info("未检测到CVE ID，使用目录名作为Case ID: %s", safe_name)
        return safe_name

    candidates.sort()
    return candidates[0]
