"""Resolve and prepare C/C++ build plans for CodeQL database creation."""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Optional

from pure_auto_codeql.api.config import get_config

from ._constants import CPP_AUTOGEN_BUILD_DIR
from .models import BuildPlan
from .process import _format_command, _run_process_with_timeout

logger = logging.getLogger(__name__)


def _try_prepare_cpp_build(source_dir: Path, log_path: Path) -> bool:
    """
    尝试在CodeQL之外执行预备步骤（第一步：生成Makefile）

    检测顺序：
    1. buildconf -> ./buildconf
    2. configure -> ./configure
    3. autogen.sh -> ./autogen.sh
    4. CMakeLists.txt -> cmake (在 _resolve_cpp_build_plan 中处理)

    Returns:
        bool: 如果成功执行了预备步骤并生成了Makefile，返回True
    """
    config = get_config()
    timeout = config.local_build_prepare_timeout

    # 检查 buildconf
    buildconf = source_dir / "buildconf"
    if buildconf.exists():
        logger.info("检测到 buildconf，执行预备构建步骤...")
        try:
            _run_process_with_timeout(
                ["bash", "./buildconf"] if os.name != "nt" else ["sh", "./buildconf"],
                cwd=source_dir,
                log_path=log_path,
                timeout=timeout
            )
            # 检查是否生成了 configure
            if (source_dir / "configure").exists():
                logger.info("buildconf 成功，继续执行 configure...")
                _run_process_with_timeout(
                    ["bash", "./configure"] if os.name != "nt" else ["sh", "./configure"],
                    cwd=source_dir,
                    log_path=log_path,
                    timeout=timeout
                )
                return (source_dir / "Makefile").exists()
            return False
        except Exception as e:
            logger.warning("buildconf 执行失败: %s", e)
            return False

    # 检查 configure
    configure = source_dir / "configure"
    if configure.exists():
        logger.info("检测到 configure，执行预备构建步骤...")
        try:
            _run_process_with_timeout(
                ["bash", "./configure"] if os.name != "nt" else ["sh", "./configure"],
                cwd=source_dir,
                log_path=log_path,
                timeout=timeout
            )
            return (source_dir / "Makefile").exists()
        except Exception as e:
            logger.warning("configure 执行失败: %s", e)
            return False

    # 检查 autogen.sh
    autogen = source_dir / "autogen.sh"
    if autogen.exists():
        logger.info("检测到 autogen.sh，执行预备构建步骤...")
        try:
            _run_process_with_timeout(
                ["bash", "./autogen.sh"] if os.name != "nt" else ["sh", "./autogen.sh"],
                cwd=source_dir,
                log_path=log_path,
                timeout=timeout
            )
            # autogen.sh 可能生成 configure
            configure = source_dir / "configure"
            if configure.exists():
                logger.info("autogen.sh 成功，继续执行 configure...")
                _run_process_with_timeout(
                    ["bash", "./configure"] if os.name != "nt" else ["sh", "./configure"],
                    cwd=source_dir,
                    log_path=log_path,
                    timeout=timeout
                )
            return (source_dir / "Makefile").exists()
        except Exception as e:
            logger.warning("autogen.sh 执行失败: %s", e)
            return False

    return False

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

    # 新增：两步走策略 - 先尝试预备步骤
    config = get_config()
    if config.prefer_local_cpp_build:
        logger.info("优先使用本地两步走构建策略...")

        # 第一步：尝试预备步骤（configure/buildconf等）
        prepare_success = _try_prepare_cpp_build(source_dir, log_path)

        if prepare_success:
            logger.info("󰄬 预备步骤成功，Makefile已生成")
            # 第二步：返回 make 命令让 CodeQL 包裹
            command = _format_command(["make", "-j4"])
            logger.info("将使用 CodeQL 包裹 make 命令进行构建")
            return BuildPlan(command=command, working_dir=source_dir, description="configure+make")

    # CMake 项目处理
    cmake_lists = source_dir / "CMakeLists.txt"
    if cmake_lists.exists():
        build_dir = target_dir / CPP_AUTOGEN_BUILD_DIR
        # 第一步：在 CodeQL 之外运行 cmake 配置
        logger.info("检测到 CMake 项目，执行配置步骤...")
        configure_cmd = ["cmake", "-S", str(source_dir), "-B", str(build_dir)]
        try:
            _run_process_with_timeout(configure_cmd, cwd=target_dir, log_path=log_path, timeout=config.local_build_prepare_timeout)
            # 第二步：返回 cmake --build 命令让 CodeQL 包裹
            command = _format_command(["cmake", "--build", str(build_dir), "-j", "4"])
            logger.info("󰄬 CMake 配置成功，将使用 CodeQL 包裹编译命令")
            return BuildPlan(command=command, working_dir=target_dir, description="cmake")
        except Exception as e:
            logger.warning("CMake 配置失败: %s，将尝试其他构建方式", e)

    # 已存在 Makefile
    make_file = source_dir / "Makefile"
    if make_file.exists():
        command = _format_command(["make", "-j4"])
        logger.info("检测到现有 Makefile，将使用 CodeQL 包裹 make 命令")
        return BuildPlan(command=command, working_dir=source_dir, description="make")

    logger.info("未发现明确构建指令或标准构建文件，启用 CodeQL autobuild 模式")
    return BuildPlan(command=None, working_dir=source_dir, description="autobuild", mode="autobuild")
