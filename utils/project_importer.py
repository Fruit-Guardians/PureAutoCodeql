"""Project import utilities."""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
from dataclasses import dataclass, field
import shlex
from pathlib import Path
from typing import List, Optional

from api.config import get_config
from utils.case import extract_cve_id

logger = logging.getLogger(__name__)

SUPPORTED_LANGUAGES = {"python", "java", "cpp"}
CPP_AUTOGEN_BUILD_DIR = "build"


@dataclass
class ProjectImportResult:
    """Information returned after importing a project."""

    case_id: str
    target_path: str
    language: Optional[str] = None
    metadata_files: List[str] = field(default_factory=list)
    codeql_created: bool = False
    codeql_error: Optional[str] = None
    build_command: Optional[str] = None
    build_workdir: Optional[str] = None


@dataclass
class BuildPlan:
    """Resolved build instructions for CodeQL database creation."""

    command: str
    working_dir: Path
    description: str = ""


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
    resolved_build: Optional[BuildPlan] = None
    if create_codeql_db:
        codeql_path = shutil.which("codeql")
        if not codeql_path:
            codeql_error = "CodeQL CLI not found in PATH"
            logger.warning(codeql_error)
        else:
            try:
                db_path = target_dir / "db" / detected_language
                if detected_language == "cpp":
                    resolved_build = _resolve_cpp_build_plan(
                        target_dir=target_dir,
                        source_dir=target_dir / "source_code",
                        user_command=build_command,
                        build_script=build_script,
                        user_workdir=build_workdir,
                    )
                _create_codeql_database(
                    target_dir / "source_code",
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


def _create_codeql_database(
    source_root: Path,
    db_path: Path,
    language: str,
    *,
    build_plan: Optional[BuildPlan],
) -> None:
    cmd = [
        "codeql",
        "database",
        "create",
        str(db_path),
        "--language",
        language,
        "--overwrite",
    ]

    if language == "cpp":
        if not build_plan:
            raise ValueError(
                "C/C++ 项目必须提供构建命令。请设置 build_command/build_script，或确保存在 CMake/Makefile。"
            )
        cmd.extend(["--command", build_plan.command])
        if build_plan.working_dir:
            cmd.extend(["--working-dir", str(build_plan.working_dir)])
    else:
        cmd.extend(["--source-root", str(source_root)])

    log_path = db_path.parent / "codeql.log"
    _run_process(cmd, cwd=None, log_path=log_path)
    logger.info("CodeQL database created at %s", db_path)


def _resolve_cpp_build_plan(
    *,
    target_dir: Path,
    source_dir: Path,
    user_command: Optional[str],
    build_script: Optional[str],
    user_workdir: Optional[str],
) -> BuildPlan:
    log_path = target_dir / "db" / "build.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)

    if user_command:
        working_dir = Path(user_workdir).expanduser().resolve() if user_workdir else target_dir
        if not working_dir.exists():
            raise ValueError(f"Build working directory does not exist: {working_dir}")
        logger.info("Using user-provided build command: %s", user_command)
        return BuildPlan(command=user_command, working_dir=working_dir, description="user")

    if build_script:
        script_path = Path(build_script)
        if not script_path.is_absolute():
            script_path = (target_dir / build_script).resolve()
        if not script_path.exists():
            raise ValueError(f"Build script not found: {script_path}")
        if script_path.suffix in (".sh", ".bash"):
            command = _format_command(["bash", str(script_path)])
        elif script_path.suffix == ".ps1":
            command = _format_command(["pwsh", "-File", str(script_path)])
        elif script_path.suffix.lower() in (".bat", ".cmd"):
            command = str(script_path)
        else:
            command = str(script_path)
        logger.info("Using build script: %s", script_path)
        return BuildPlan(command=command, working_dir=script_path.parent, description="script")

    cmake_lists = source_dir / "CMakeLists.txt"
    if cmake_lists.exists():
        build_dir = target_dir / CPP_AUTOGEN_BUILD_DIR
        configure_cmd = ["cmake", "-S", str(source_dir), "-B", str(build_dir)]
        _run_process(configure_cmd, cwd=target_dir, log_path=log_path)
        command = _format_command(["cmake", "--build", str(build_dir)])
        logger.info("Auto-detected CMake project, build dir: %s", build_dir)
        return BuildPlan(command=command, working_dir=target_dir, description="cmake")

    make_file = source_dir / "Makefile"
    if make_file.exists():
        command = _format_command(["make", "-j"])
        logger.info("Auto-detected Makefile project.")
        return BuildPlan(command=command, working_dir=source_dir, description="make")

    raise ValueError(
        "无法自动推断C/C++构建命令。请提供 build_command 或 build_script。"
    )


def _run_process(cmd: List[str], *, cwd: Optional[Path], log_path: Optional[Path]) -> None:
    display = " ".join(cmd)
    logger.info("Running command: %s (cwd=%s)", display, cwd or os.getcwd())
    result = subprocess.run(
        cmd,
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        check=False,
    )
    if log_path:
        with open(log_path, "a", encoding="utf-8") as log_file:
            log_file.write(f"$ {display}\n")
            if result.stdout:
                log_file.write(result.stdout + "\n")
            if result.stderr:
                log_file.write(result.stderr + "\n")
    if result.returncode != 0:
        stderr = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"Command failed ({display}): {stderr}")


def _format_command(parts: List[str]) -> str:
    if os.name == "nt":
        formatted = []
        for part in parts:
            if " " in part:
                formatted.append(f'"{part}"')
            else:
                formatted.append(part)
        return " ".join(formatted)
    return " ".join(shlex.quote(part) for part in parts)




__all__ = ["import_project", "ProjectImportResult"]

