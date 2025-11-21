#!/usr/bin/env python3
"""
CodeQL C/C++ Containerized Builder Entrypoint
Implements the "Init -> Pre-build -> Trace -> Finalize -> Validate" workflow.
"""

import os
import sys
import subprocess
import shutil
import time

# 约定挂载点
SRC_DIR = "/src"
DB_DIR = "/out/db"

def log(msg):
    print(f"[Builder] {msg}", flush=True)

def run_cmd(cmd, cwd=None, env=None, check=True):
    """运行系统命令并实时打印输出"""
    log(f"Running: {cmd}")
    try:
        # 使用 shell=True 允许使用 &&, || 等 shell 特性
        result = subprocess.run(
            cmd, 
            shell=True, 
            cwd=cwd, 
            env=env, 
            check=check,
            stdout=sys.stdout,
            stderr=sys.stderr
        )
        return result.returncode
    except subprocess.CalledProcessError as e:
        log(f"Command failed with exit code {e.returncode}")
        raise

def main():
    # 1. 获取环境变量配置
    language = os.environ.get("CODEQL_LANG", "cpp")
    # 用户显式指定的构建命令 (如果有)
    user_build_cmd = os.environ.get("BUILD_COMMAND", "").strip()
    
    log(f"Starting build for language: {language}")
    
    # ========================================================================
    # Step 1: 初始化数据库 (Init)
    # ========================================================================
    log(">>> Step 1: Init Database")
    
    # 检查 /out 目录是否有写权限
    if not os.path.exists("/out"):
        log("Error: /out directory not mounted!")
        sys.exit(1)

    # 强制初始化：这会清空 DB_DIR 如果它已存在
    # 注意：在 Docker 挂载中，如果宿主机 DB_DIR 已存在且非空，CodeQL 可能会报错，
    # 但加上 --overwrite 应该可以覆盖。
    run_cmd(f"codeql database init --language={language} --source-root={SRC_DIR} --overwrite {DB_DIR}")

    # ========================================================================
    # Step 2: 依赖冲突/定制处理 (Pre-build Hook)
    # ========================================================================
    # 这是一个杀手锏：如果项目根目录下有 pre-build.sh，我们执行它。
    # 用户可以在这里写 apt-get install xxx 或者编译特定版本的库。
    pre_build_script = os.path.join(SRC_DIR, "pre-build.sh")
    
    if os.path.exists(pre_build_script):
        log(">>> Step 2: Found 'pre-build.sh', executing custom environment setup...")
        # 赋予执行权限
        run_cmd(f"chmod +x {pre_build_script}", cwd=SRC_DIR)
        # 执行脚本
        run_cmd(f"./pre-build.sh", cwd=SRC_DIR)
    else:
        log(">>> Step 2: No 'pre-build.sh' found, skipping custom setup.")

    # ========================================================================
    # Step 3: 构建探测与注入 (Trace)
    # ========================================================================
    log(">>> Step 3: Trace Command")
    
    cmd_to_trace = None
    working_dir = SRC_DIR

    # --- 策略 A: 用户指定 (最高优先级) ---
    if user_build_cmd:
        log(f"Using user-provided build command: {user_build_cmd}")
        cmd_to_trace = user_build_cmd
        
    # --- 策略 B: 启发式探测 (Heuristics) ---
    elif os.path.exists(os.path.join(SRC_DIR, "CMakeLists.txt")):
        log("Detected CMake project")
        # 为了防止污染源码目录，创建一个构建目录
        build_dir_name = "build_codeql_container"
        build_dir = os.path.join(SRC_DIR, build_dir_name)
        
        # 确保构建目录存在
        os.makedirs(build_dir, exist_ok=True)
        
        # 构造 CMake 命令：
        # 1. cd 到构建目录
        # 2. cmake 配置 (指定 Release 模式，跳过测试以加速构建)
        # 3. make 编译 (使用所有核心)
        # 注意：使用 sh -c 将多个命令组合成一个 trace 目标
        cmd_to_trace = f"cd {build_dir_name} && cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF && make -j$(nproc)"
        
    elif os.path.exists(os.path.join(SRC_DIR, "Makefile")):
        log("Detected Makefile project")
        # 尝试 clean 以确保全量编译，然后并行编译
        cmd_to_trace = "make clean; make -j$(nproc)"
        
    # --- 策略 C: Autobuild 兜底 (Last Resort) ---
    else:
        log("No standard build system (CMake/Make) detected and no user command.")
        log("Falling back to CodeQL autobuild...")
        # CodeQL autobuild 需要通过特殊方式调用
        # 对于 C/C++，直接用 cpp/tools/autobuild.sh
        autobuild_script = "/opt/codeql/cpp/tools/autobuild.sh"
        if os.path.exists(autobuild_script):
            cmd_to_trace = f"bash {autobuild_script}"
        else:
            # 如果 autobuild.sh 不存在，尝试直接运行 make（通用兜底）
            log("Warning: autobuild.sh not found, trying generic 'make' as last resort")
            cmd_to_trace = "make -j$(nproc) || true"  # 允许失败

    log(f"Tracing command: [{cmd_to_trace}]")
    
    # 执行 Trace
    # index-files 可以包含生成的源码
    run_cmd(
        f"codeql database trace-command {DB_DIR} --working-dir={SRC_DIR} -- {cmd_to_trace}", 
        cwd=SRC_DIR
    )

    # ========================================================================
    # Step 4: 封包 (Finalize)
    # ========================================================================
    log(">>> Step 4: Finalize Database")
    run_cmd(f"codeql database finalize {DB_DIR}")

    # ========================================================================
    # Step 5: 质量校验 (Validate)
    # ========================================================================
    log(">>> Step 5: Validate Results")
    
    src_zip = os.path.join(DB_DIR, "src.zip")
    
    # 校验 1: src.zip 是否存在
    if not os.path.exists(src_zip):
        log("!!! Critical Error: src.zip was not created.")
        log("This means CodeQL failed to finalize the database.")
        sys.exit(1)
        
    # 校验 2: src.zip 大小 (防止空包)
    size = os.path.getsize(src_zip)
    kb_size = size / 1024
    log(f"src.zip size: {kb_size:.2f} KB")
    
    if size < 1024: # 小于 1KB 通常意味着根本没打包进任何代码
        log("!!! Critical Error: src.zip is too small (< 1KB).")
        log("This implies the build command ran but didn't compile/touch any source files.")
        log("Possible causes: Project already built? Wrong build command? Missing dependencies?")
        sys.exit(1)

    log(">>> Build Success! Database is ready at /out/db")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        log(f"!!! Build Process Failed: {e}")
        sys.exit(1)

