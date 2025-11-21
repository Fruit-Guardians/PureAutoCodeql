"""Project import utilities."""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import zipfile
import sys
from dataclasses import dataclass, field
import shlex
from pathlib import Path
from typing import List, Optional

from api.config import get_config
from utils.case import extract_cve_id

logger = logging.getLogger(__name__)

SUPPORTED_LANGUAGES = {"python", "java", "cpp"}
CPP_AUTOGEN_BUILD_DIR = "build"


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

    command: Optional[str]
    working_dir: Path
    description: str = ""
    mode: str = "manual"


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
                if detected_language == "cpp":
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
        return None

    candidates.sort()
    return candidates[0]


def _reset_directory(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def _copy_dir_contents(src: Path, dst: Path) -> None:
    _reset_directory(dst)
    for entry in src.iterdir():
        dest_path = dst / entry.name
        if entry.is_dir():
            shutil.copytree(entry, dest_path, dirs_exist_ok=True)
        else:
            shutil.copy2(entry, dest_path)


def _safe_extract_zip(zip_path: Path, dst: Path) -> None:
    _reset_directory(dst)
    with zipfile.ZipFile(zip_path, "r") as archive:
        for member in archive.infolist():
            member_path = Path(member.filename)
            if member_path.is_absolute() or ".." in member_path.parts:
                raise ValueError(f"Unsafe path detected in zip archive: {member.filename}")
        archive.extractall(dst)
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


def _create_base_structure(target_dir: Path) -> None:
    for child in ["source_code", "db", "inputs", "intel"]:
        (target_dir / child).mkdir(parents=True, exist_ok=True)


def _copy_metadata_files(input_dir: Path, target_dir: Path, case_id: str) -> List[str]:
    metadata_files: List[str] = []
    db_dir = target_dir / "db"
    inputs_dir = target_dir / "inputs"
    patch_counter = 1

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
    config = get_config()
    
    # 检查是否启用 Docker 构建 (仅限 C/C++)
    use_docker = language == "cpp" and config.use_docker_for_cpp
    
    if use_docker:
        logger.info("Switching to Dockerized build for C/C++ project (Image: %s)...", config.docker_builder_image)
        try:
            _run_docker_build(
                source_root=source_root,
                db_path=db_path,
                image_name=config.docker_builder_image,
                build_plan=build_plan
            )
            return
        except Exception as e:
            logger.error("Docker build failed: %s", e)
            raise RuntimeError(f"Docker build failed: {e}")

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

        if build_plan.mode == "autobuild":
            cmd.append("--build-mode=autobuild")
            cmd.extend(["--source-root", str(source_root)])
        else:
            if not build_plan.command:
                raise ValueError("Manual build mode requires a command.")
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

    logger.info("未发现明确构建指令或标准构建文件，启用 CodeQL autobuild 模式")
    return BuildPlan(command=None, working_dir=source_dir, description="autobuild", mode="autobuild")


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


def _run_docker_build(
    source_root: Path, 
    db_path: Path, 
    image_name: str,
    build_plan: Optional[BuildPlan]
) -> None:
    """运行 Docker 容器进行构建"""
    
    # 1. 路径准备
    # source_root: 源代码目录 -> 挂载为 /src
    # db_path: 数据库目录 (e.g. projects/CVE-xxx/db/cpp)
    
    abs_source = source_root.resolve()
    abs_db_path = db_path.resolve()
    
    # 清理旧的数据库目录（避免 Docker 内 CodeQL 检测到残留文件）
    if abs_db_path.exists():
        logger.info("清理旧数据库目录: %s", abs_db_path)
        _safe_rmtree(abs_db_path)
    
    # 确保数据库目录的父目录存在
    abs_db_path.parent.mkdir(parents=True, exist_ok=True)

    # 2. 构造 Docker 命令
    cmd = [
        "docker", "run", "--rm",
        # 挂载源码
        "-v", f"{abs_source}:/src",
        # 挂载数据库目录：直接挂载到 /out/db
        # 这样容器内写入 /out/db/... 就会直接写入宿主机的 db_path/...
        "-v", f"{abs_db_path}:/out/db",
        # 环境变量
        "-e", "CODEQL_LANG=cpp",
    ]
    
    # 传递用户指定的构建命令 (如果有)
    # 注意：如果是自动探测的 CMake/Make，我们不传命令，让容器内脚本自己探测
    # 只有当用户显式指定了 command (mode='manual' or 'user') 时才传递
    if build_plan and build_plan.mode in ("manual", "user") and build_plan.command:
        cmd.extend(["-e", f"BUILD_COMMAND={build_plan.command}"])
        
    cmd.append(image_name)
    
    logger.info("Executing Docker build command: %s", " ".join(cmd))
    
    # 3. 执行
    log_path = db_path.parent / "docker_build.log"
    
    with open(log_path, "w", encoding="utf-8") as log_file:
        try:
            # 使用 Popen 实时流式输出日志
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace"
            )
            
            # 实时打印并写入日志
            if process.stdout:
                for line in process.stdout:
                    print(line, end="") # 打印到控制台
                    log_file.write(line) # 写入日志
                
            return_code = process.wait()
            
            if return_code != 0:
                raise RuntimeError(f"Docker container exited with code {return_code}")
                
        except Exception as e:
            raise RuntimeError(f"Docker execution failed: {e}")

    # 4. 结果验证
    # 检查数据库是否成功创建 (检查 codeql-database.yml 或 src.zip)
    if not (db_path / "codeql-database.yml").exists():
         # 尝试检查子目录 (防止挂载错位)
         # 但根据 -v abs_db_path:/out/db，应该就在根下
         raise RuntimeError("Docker finished but database was not found (codeql-database.yml missing). Check docker_build.log.") 


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

