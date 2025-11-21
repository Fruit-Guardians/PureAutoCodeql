#!/usr/bin/env python3
"""
CodeQL C/C++ Containerized Builder Entrypoint
实现自动 fallback 机制：编译失败时自动切换到 --build-mode=none
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
        if check:
            raise
        return e.returncode

def build_with_compilation():
    """尝试正常编译建库（策略1）"""
    log("=" * 70)
    log("策略 1: 尝试编译建库 (autobuild/cmake/make)")
    log("=" * 70)
    
    # 获取环境变量配置
    language = os.environ.get("CODEQL_LANG", "cpp")
    user_build_cmd = os.environ.get("BUILD_COMMAND", "").strip()
    
    # ========================================================================
    # Step 1: 初始化数据库
    # ========================================================================
    log(">>> Step 1: Init Database")
    run_cmd(f"codeql database init --language={language} --source-root={SRC_DIR} --overwrite {DB_DIR}")

    # ========================================================================
    # Step 2: Pre-build Hook
    # ========================================================================
    pre_build_script = os.path.join(SRC_DIR, "pre-build.sh")
    if os.path.exists(pre_build_script):
        log(">>> Step 2: Found 'pre-build.sh', executing custom setup...")
        run_cmd(f"chmod +x {pre_build_script}", cwd=SRC_DIR)
        run_cmd(f"./pre-build.sh", cwd=SRC_DIR)
    else:
        log(">>> Step 2: No 'pre-build.sh' found, skipping.")

    # ========================================================================
    # Step 3: 构建探测与 Trace
    # ========================================================================
    log(">>> Step 3: Trace Command")
    
    cmd_to_trace = None
    working_dir = SRC_DIR

    # 策略 A: 用户指定
    if user_build_cmd:
        log(f"Using user-provided build command: {user_build_cmd}")
        cmd_to_trace = user_build_cmd
        
    # 策略 B: CMake
    elif os.path.exists(os.path.join(SRC_DIR, "CMakeLists.txt")):
        log("Detected CMake project")
        build_dir_name = "build_codeql_container"
        build_dir = os.path.join(SRC_DIR, build_dir_name)
        os.makedirs(build_dir, exist_ok=True)
        cmd_to_trace = f"cd {build_dir_name} && cmake .. -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF && make -j$(nproc)"
        
    # 策略 C: Makefile
    elif os.path.exists(os.path.join(SRC_DIR, "Makefile")):
        log("Detected Makefile project")
        cmd_to_trace = "make clean; make -j$(nproc)"
        
    # 策略 D: Autobuild
    else:
        log("No standard build system detected, falling back to CodeQL autobuild...")
        autobuild_script = "/opt/codeql/cpp/tools/autobuild.sh"
        if os.path.exists(autobuild_script):
            cmd_to_trace = f"bash {autobuild_script}"
        else:
            log("Warning: autobuild.sh not found, trying generic 'make'")
            cmd_to_trace = "make -j$(nproc) || true"

    log(f"Tracing command: [{cmd_to_trace}]")
    log("开始编译跟踪（这可能需要几分钟）...")
    
    # 执行 Trace（允许失败）
    trace_start = time.time()
    trace_ret = run_cmd(
        f"codeql database trace-command {DB_DIR} --working-dir={SRC_DIR} -- {cmd_to_trace}", 
        cwd=SRC_DIR,
        check=False  # 不抛异常，返回退出码
    )
    trace_elapsed = time.time() - trace_start
    log(f"编译跟踪完成，耗时: {trace_elapsed:.1f} 秒")
    
    if trace_ret != 0:
        log(f"!!! Trace command failed with exit code {trace_ret}")
        return False

    # ========================================================================
    # Step 4: Finalize
    # ========================================================================
    log(">>> Step 4: Finalize Database")
    log("警告：finalize 步骤可能需要 5-15 分钟，请耐心等待...")
    log("正在生成 src.zip 和数据库索引...")
    
    # 检查数据库大小和系统资源，给出预估
    try:
        import subprocess as sp
        result = sp.run(["du", "-sh", DB_DIR], capture_output=True, text=True)
        if result.returncode == 0:
            log(f"当前数据库大小: {result.stdout.strip()}")
        
        # 检查内存使用情况
        mem_result = sp.run(["free", "-h"], capture_output=True, text=True)
        if mem_result.returncode == 0:
            lines = mem_result.stdout.strip().split('\n')
            if len(lines) >= 2:
                log(f"内存状态: {lines[1]}")
        
        # 检查磁盘空间
        df_result = sp.run(["df", "-h", DB_DIR], capture_output=True, text=True)
        if df_result.returncode == 0:
            lines = df_result.stdout.strip().split('\n')
            if len(lines) >= 2:
                log(f"磁盘空间: {lines[1]}")
    except Exception as e:
        log(f"资源检查失败: {e}")
    
    log("开始 finalize...")
    start_time = time.time()
    finalize_ret = run_cmd(f"codeql database finalize {DB_DIR} --verbose", check=False)
    elapsed = time.time() - start_time
    log(f"Finalize 完成，耗时: {elapsed:.1f} 秒")
    
    if finalize_ret != 0:
        log(f"!!! Finalize failed with exit code {finalize_ret}")
        return False

    # ========================================================================
    # Step 5: 验证
    # ========================================================================
    log(">>> Step 5: Validate Results")
    src_zip = os.path.join(DB_DIR, "src.zip")
    
    if not os.path.exists(src_zip):
        log("!!! src.zip not created")
        return False
        
    size = os.path.getsize(src_zip)
    kb_size = size / 1024
    log(f"src.zip size: {kb_size:.2f} KB")
    
    if size < 1024:
        log("!!! src.zip too small (< 1KB)")
        return False

    log("✅ 编译建库成功！")
    return True

def build_with_none_mode():
    """使用 --build-mode=none 建库（策略2：不编译，只提取源码）"""
    log("=" * 70)
    log("策略 2: 使用 --build-mode=none (不编译，仅提取源码)")
    log("=" * 70)
    
    language = os.environ.get("CODEQL_LANG", "cpp")
    
    # 清理之前失败的数据库
    if os.path.exists(DB_DIR):
        log(f"Cleaning up previous failed database at {DB_DIR}")
        shutil.rmtree(DB_DIR, ignore_errors=True)
    
    # 使用 none 模式创建数据库
    log(">>> Creating database with --build-mode=none")
    create_ret = run_cmd(
        f"codeql database create {DB_DIR} --language={language} --source-root={SRC_DIR} --build-mode=none",
        check=False
    )
    
    if create_ret != 0:
        log(f"!!! --build-mode=none failed with exit code {create_ret}")
        return False
    
    # 验证
    src_zip = os.path.join(DB_DIR, "src.zip")
    if not os.path.exists(src_zip):
        log("!!! src.zip not created even with --build-mode=none")
        return False
        
    size = os.path.getsize(src_zip)
    kb_size = size / 1024
    log(f"src.zip size: {kb_size:.2f} KB")
    
    if size < 1024:
        log("!!! src.zip too small (< 1KB)")
        return False
    
    log("✅ none 模式建库成功！")
    return True

def main():
    log(f"Starting CodeQL database creation for language: {os.environ.get('CODEQL_LANG', 'cpp')}")
    
    # 检查挂载点
    if not os.path.exists("/out"):
        log("Error: /out directory not mounted!")
        sys.exit(1)
    
    # 策略 1: 尝试编译建库
    try:
        if build_with_compilation():
            log("=" * 70)
            log("🎉 数据库创建成功！(使用编译模式)")
            log("=" * 70)
            sys.exit(0)
    except Exception as e:
        log(f"编译建库过程出现异常: {e}")
    
    # 策略 2: Fallback 到 none 模式
    log("")
    log("⚠️  编译建库失败，自动切换到 --build-mode=none")
    log("")
    
    try:
        if build_with_none_mode():
            log("=" * 70)
            log("🎉 数据库创建成功！(使用 none 模式)")
            log("=" * 70)
            sys.exit(0)
    except Exception as e:
        log(f"none 模式建库也失败: {e}")
    
    # 两种策略都失败
    log("=" * 70)
    log("❌ 所有建库策略均失败")
    log("=" * 70)
    sys.exit(1)

if __name__ == "__main__":
    main()
