"""Create CodeQL databases, including Docker-based builds."""

from __future__ import annotations

import logging
import os
import subprocess
from pathlib import Path
from typing import Optional

from pure_auto_codeql.api.config import get_config

from ..dependency_installer import DependencyInstaller
from .filesystem import _safe_rmtree
from .models import BuildPlan
from .process import _run_process

logger = logging.getLogger(__name__)


def _create_codeql_database(
    source_root: Path,
    db_path: Path,
    language: str,
    *,
    build_plan: Optional[BuildPlan],
) -> None:
    config = get_config()

    # C/C++ 项目的智能构建策略
    if language == "cpp":
        # 策略1：优先本地两步走构建（如果启用）
        if config.prefer_local_cpp_build and not config.use_docker_for_cpp:
            logger.info("=" * 60)
            logger.info("策略1：尝试本地两步走构建（支持自动依赖安装）")
            logger.info("=" * 60)

            # 创建依赖安装器（使用配置中的设置）
            dep_installer = DependencyInstaller(
                auto_install=config.auto_install_dependencies,
                max_retries=config.auto_install_max_retries
            )
            log_path = db_path.parent / "codeql.log"

            def build_func():
                """构建函数，供依赖安装器调用"""
                # 清理旧数据库
                if db_path.exists():
                    logger.info("清理旧数据库: %s", db_path)
                    _safe_rmtree(db_path)

                # 执行本地构建
                cmd = [
                    "codeql",
                    "database",
                    "create",
                    str(db_path),
                    "--language",
                    language,
                    "--overwrite",
                ]

                if not build_plan:
                    raise ValueError("C/C++ 项目必须提供构建命令")

                if build_plan.mode == "autobuild":
                    cmd.append("--build-mode=autobuild")
                    cmd.extend(["--source-root", str(source_root)])
                else:
                    if not build_plan.command:
                        raise ValueError("Manual build mode requires a command.")
                    cmd.extend(["--command", build_plan.command])
                    if build_plan.working_dir:
                        cmd.extend(["--working-dir", str(build_plan.working_dir)])

                _run_process(cmd, cwd=None, log_path=log_path)

                # 验证数据库
                src_zip = db_path / "src.zip"
                if src_zip.exists() and src_zip.stat().st_size > 1024:
                    return True
                else:
                    logger.warning("本地构建完成，但 src.zip 太小或不存在")
                    raise RuntimeError("Local build produced invalid database")

            try:
                # 使用自动依赖安装功能进行构建
                success, error = dep_installer.try_build_with_auto_deps(
                    build_func=build_func,
                    log_path=log_path,
                )

                if success:
                    logger.info("=" * 60)
                    logger.info("✅ 本地两步走构建成功！")
                    if dep_installer.installed_packages:
                        logger.info("📦 自动安装的依赖: %s", ", ".join(dep_installer.installed_packages))
                    logger.info("=" * 60)
                    return
                else:
                    raise RuntimeError(f"Local build failed after auto-installing dependencies: {error}")

            except Exception as e:
                logger.warning("=" * 60)
                logger.warning("⚠️  本地构建失败: %s", e)
                logger.warning("=" * 60)

                # 策略2：尝试 --build-mode=none（不编译，仅分析源码）
                logger.info("=" * 60)
                logger.info("策略2：尝试 --build-mode=none（仅分析源码，不编译）")
                logger.info("=" * 60)

                try:
                    # 清理失败的数据库
                    if db_path.exists():
                        _safe_rmtree(db_path)

                    # 使用 --build-mode=none 创建数据库
                    cmd = [
                        "codeql",
                        "database",
                        "create",
                        str(db_path),
                        "--language",
                        language,
                        "--overwrite",
                        "--build-mode=none",
                        "--source-root",
                        str(source_root),
                    ]

                    none_log_path = db_path.parent / "codeql_none_mode.log"
                    _run_process(cmd, cwd=None, log_path=none_log_path)

                    logger.info("=" * 60)
                    logger.info("✅ --build-mode=none 创建数据库成功！")
                    logger.info("⚠️  注意：未编译项目，分析结果可能不完整")
                    logger.info("=" * 60)
                    return

                except Exception as none_error:
                    logger.warning("--build-mode=none 也失败了: %s", none_error)

                    # 策略3：如果配置了 Docker，回退到 Docker autobuild
                    if config.docker_builder_image:
                        logger.info("=" * 60)
                        logger.info("策略3：最后回退到 Docker autobuild")
                        logger.info("=" * 60)

                        try:
                            # 清理失败的数据库
                            if db_path.exists():
                                _safe_rmtree(db_path)

                            _run_docker_build(
                                source_root=source_root,
                                db_path=db_path,
                                image_name=config.docker_builder_image,
                                build_plan=None  # Docker 内部会自动探测
                            )
                            logger.info("=" * 60)
                            logger.info("✅ Docker autobuild 构建成功！")
                            logger.info("=" * 60)
                            return
                        except Exception as docker_error:
                            logger.error("Docker autobuild 也失败了: %s", docker_error)
                            raise RuntimeError(f"All build strategies failed. Local: {e}, None mode: {none_error}, Docker: {docker_error}")
                    else:
                        # 没有 Docker 配置
                        raise RuntimeError(f"Build failed. Local: {e}, None mode: {none_error}")

        # 策略2：直接使用 Docker 构建（如果强制启用）
        elif config.use_docker_for_cpp:
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

        # 策略3：纯本地构建（无 Docker 回退）
        else:
            logger.info("Using local build without Docker fallback")
            cmd = [
                "codeql",
                "database",
                "create",
                str(db_path),
                "--language",
                language,
                "--overwrite",
            ]

            if not build_plan:
                raise ValueError("C/C++ 项目必须提供构建命令")

            if build_plan.mode == "autobuild":
                cmd.append("--build-mode=autobuild")
                cmd.extend(["--source-root", str(source_root)])
            else:
                if not build_plan.command:
                    raise ValueError("Manual build mode requires a command.")
                cmd.extend(["--command", build_plan.command])
                if build_plan.working_dir:
                    cmd.extend(["--working-dir", str(build_plan.working_dir)])

            log_path = db_path.parent / "codeql.log"
            _run_process(cmd, cwd=None, log_path=log_path)
            logger.info("CodeQL database created at %s", db_path)
            return

    # 非 C/C++ 项目的常规处理
    cmd = [
        "codeql",
        "database",
        "create",
        str(db_path),
        "--language",
        language,
        "--overwrite",
    ]

    if language == "java":
        # Java 不需要编译追踪，使用 --build-mode=none
        logger.info("Java项目使用 --build-mode=none (无需编译)")
        cmd.extend(["--source-root", str(source_root)])
        cmd.append("--build-mode=none")
    else:
        # Python 等其他语言
        cmd.extend(["--source-root", str(source_root)])

    log_path = db_path.parent / "codeql.log"
    _run_process(cmd, cwd=None, log_path=log_path)
    logger.info("CodeQL database created at %s", db_path)

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
    container_user = f"{getattr(os, 'getuid', lambda: 65534)()}:{getattr(os, 'getgid', lambda: 65534)()}"

    # 清理旧的数据库目录（避免 Docker 内 CodeQL 检测到残留文件）
    if abs_db_path.exists():
        logger.info("清理旧数据库目录: %s", abs_db_path)
        _safe_rmtree(abs_db_path)

    # 确保数据库目录的父目录存在
    abs_db_path.parent.mkdir(parents=True, exist_ok=True)

    # 2. 构造 Docker 命令
    cmd = [
        "docker", "run", "--rm",
        "--user", container_user,
        "--read-only",
        "--network=none",
        "--security-opt=no-new-privileges",
        "--cap-drop=ALL",
        "--pids-limit=512",
        "--memory=8g",
        "--cpus=4",
        "--shm-size=2g",
        "--tmpfs=/tmp:rw,noexec,nosuid,size=1g",
        "--tmpfs=/work:rw,nosuid,size=4g",
        # 挂载源码
        "-v", f"{abs_source}:/src:ro",
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
