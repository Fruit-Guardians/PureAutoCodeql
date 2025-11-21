# C/C++ 自动化建库最终落地方案

基于之前的讨论，结合依赖冲突解决策略，这是最终的**C/C++ 自动化建库标准方案**。

---

## 1. 核心架构：Host-Controller + Docker-Runner

采用 **"控制与执行分离"** 架构。
- **宿主机 (Python)**：负责项目管理、路径映射、任务调度。
- **Docker 容器**：负责脏活累活（环境配置、依赖安装、编译构建）。

### 为什么这是最终解？
1. **解决依赖地狱**：所有 C++ 乱七八糟的依赖都被封装在容器里，不污染宿主机。
2. **解决依赖冲突**：通过 `pre-build.sh` 钩子在容器内动态调整环境。
3. **解决构建不稳**：容器环境一致，且可以随时重置。

---

## 2. 实施组件

我们需要创建以下三个核心文件：

### 2.1. Dockerfile (胖镜像定义)

创建一个能覆盖 90% 场景的胖镜像。

```dockerfile
# 基础镜像：选择较新的 LTS 版本，平衡新旧项目
FROM mcr.microsoft.com/cbl-mariner/base/core:2.0 AS base
# 或者使用 Ubuntu，更通用
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# 1. 安装核心构建工具
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    cmake \
    automake \
    libtool \
    pkg-config \
    wget \
    curl \
    unzip \
    python3 \
    python3-pip \
    # 常用构建系统
    ninja-build \
    maven \
    gradle \
    ant

# 2. 安装 CodeQL CLI (通常下载最新版)
RUN wget https://github.com/github/codeql-cli-binaries/releases/download/v2.16.4/codeql-linux64.zip -O codeql.zip && \
    unzip codeql.zip -d /opt && \
    rm codeql.zip
ENV PATH="/opt/codeql:${PATH}"

# 3. 安装高频 C++ 依赖库 (Fat Image 策略)
# 这能解决绝大多数 "missing library" 错误
RUN apt-get install -y \
    libssl-dev \
    zlib1g-dev \
    libboost-all-dev \
    libsqlite3-dev \
    libmysqlclient-dev \
    libpcap-dev \
    libxml2-dev \
    libxslt1-dev \
    libyaml-dev

# 4. 准备工作目录
WORKDIR /work
COPY builder_entrypoint.py /usr/local/bin/builder_entrypoint.py
RUN chmod +x /usr/local/bin/builder_entrypoint.py

ENTRYPOINT ["python3", "/usr/local/bin/builder_entrypoint.py"]
```

### 2.2. 容器内构建脚本 (`builder_entrypoint.py`)

这是运行在容器内部的“大脑”，执行 **Init -> Pre-build -> Trace -> Finalize -> Validate** 流程。

```python
import os
import sys
import subprocess
import shutil

# 约定挂载点
SRC_DIR = "/src"  # 源码只读挂载? 最好是读写，因为编译会产生中间文件
DB_DIR = "/out/db"

def run_cmd(cmd, cwd=None, env=None):
    print(f"[+] Running: {cmd}")
    ret = subprocess.run(cmd, shell=True, cwd=cwd, env=env)
    if ret.returncode != 0:
        raise Exception(f"Command failed: {cmd}")

def main():
    language = os.environ.get("CODEQL_LANG", "cpp")
    build_cmd = os.environ.get("BUILD_COMMAND") # 用户指定的命令
    
    # 1. 初始化数据库
    print(">>> Step 1: Init Database")
    # 确保目录为空或覆盖
    run_cmd(f"codeql database init --language={language} --source-root={SRC_DIR} --overwrite {DB_DIR}")

    # 2. 依赖冲突/定制处理 (Pre-build Hook)
    # 如果项目根目录有 pre-build.sh，执行它来安装特殊依赖或卸载冲突依赖
    pre_build_script = os.path.join(SRC_DIR, "pre-build.sh")
    if os.path.exists(pre_build_script):
        print(f">>> Step 2: Found pre-build.sh, executing...")
        run_cmd(f"chmod +x {pre_build_script} && {pre_build_script}", cwd=SRC_DIR)
    else:
        print(">>> Step 2: No pre-build.sh found, skipping custom deps.")

    # 3. 构建探测与注入 (Trace)
    print(">>> Step 3: Trace Command")
    
    cmd_to_trace = None
    
    # 策略 A: 用户指定
    if build_cmd:
        print(f"Using user command: {build_cmd}")
        cmd_to_trace = build_cmd
        
    # 策略 B: 启发式探测
    elif os.path.exists(os.path.join(SRC_DIR, "CMakeLists.txt")):
        print("Detected CMake project")
        # 创建构建目录，防止污染源码根
        build_dir = os.path.join(SRC_DIR, "build_codeql")
        os.makedirs(build_dir, exist_ok=True)
        # 构造 CMake 命令
        cmd_to_trace = f"cd {build_dir} && cmake .. -DCMAKE_BUILD_TYPE=Release && make -j$(nproc)"
        
    elif os.path.exists(os.path.join(SRC_DIR, "Makefile")):
        print("Detected Makefile project")
        cmd_to_trace = f"make clean; make -j$(nproc)" # 尝试 clean 保证全量编译
        
    # 策略 C: Autobuild 兜底
    else:
        print("No build system detected, falling back to autobuild")
        cmd_to_trace = "codeql-autobuild"

    # 执行 Trace
    # 注意：这里需要把环境传进去
    run_cmd(f"codeql database trace-command {DB_DIR} -- {cmd_to_trace}", cwd=SRC_DIR)

    # 4. 封包 (Finalize)
    print(">>> Step 4: Finalize")
    run_cmd(f"codeql database finalize {DB_DIR}")

    # 5. 质量校验 (Validate)
    print(">>> Step 5: Validate")
    # 检查 src.zip 是否存在且有大小
    src_zip = os.path.join(DB_DIR, "src.zip")
    if not os.path.exists(src_zip) or os.path.getsize(src_zip) < 1024:
        print("!!! Error: src.zip is too small or missing. Build might have failed silently.")
        sys.exit(1)
        
    print(">>> Success! Database created.")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"!!! Build Failed: {e}")
        sys.exit(1)
```

### 2.3. 宿主机集成 (`utils/project_importer.py`)

在宿主机代码中，不需要复杂的逻辑，只需要启动 Docker。

```python
def _create_codeql_database_in_docker(source_dir, db_path, build_command=None):
    # 1. 准备 Docker 命令
    # 假设镜像名为 pure-codeql-cpp:v1
    
    # 路径映射：Windows 路径需要转换为 Docker 挂载格式
    abs_source = str(source_dir.resolve())
    abs_db_parent = str(db_path.parent.resolve()) # 挂载 db 的父目录，因为 db 目录本身由 codeql 创建
    
    cmd = [
        "docker", "run", "--rm",
        "-v", f"{abs_source}:/src",          # 源码挂载到 /src
        "-v", f"{abs_db_parent}:/out",       # 输出挂载到 /out
        "-e", "CODEQL_LANG=cpp",
    ]
    
    if build_command:
        cmd.extend(["-e", f"BUILD_COMMAND={build_command}"])
        
    cmd.append("pure-codeql-cpp:v1")
    
    # 2. 执行
    subprocess.run(cmd, check=True)
    
    # 3. 结果回落
    # Docker 里的 /out/db 会生成在宿主机的 abs_db_parent/db 下
    # 需要确保权限正确（Windows上通常没问题，Linux要注意 chown）
```

---

## 3. 依赖冲突解决方案操作流

当默认环境编译失败（依赖冲突）时，用户的操作流程：

1. **用户发现报错**：Docker 日志显示 `libssl-dev` 版本不兼容。
2. **编写钩子脚本**：用户在项目源码根目录下创建一个名为 `pre-build.sh` 的文件。
3. **脚本内容**：
   ```bash
   #!/bin/bash
   # 卸载系统自带的高版本库
   apt-get remove -y libssl-dev
   
   # 安装特定版本，或者从源码编译安装
   wget http://openssl.org/source/openssl-1.0.2g.tar.gz
   tar -xvf openssl-1.0.2g.tar.gz
   cd openssl-1.0.2g
   ./config && make && make install
   ```
4. **重新运行建库**：Docker 里的 `builder_entrypoint.py` 会自动检测到这个文件，并在 `trace-command` 之前执行它，从而修复环境。

---

## 4. 总结

| 模块 | 方案 | 作用 |
| :--- | :--- | :--- |
| **环境隔离** | **Docker** | 彻底解决 C++ 编译环境污染和缺失问题 |
| **流程控制** | **Python Entrypoint** | 实现 Init -> Pre-build -> Trace -> Finalize 闭环 |
| **依赖冲突** | **pre-build.sh** | 给用户留的后门，允许在容器内动态魔改环境 |
| **兜底策略** | **Autobuild** | 最后的防线 |

您可以先按此方案创建 Dockerfile 和 entrypoint 脚本，构建镜像，然后我们再修改 Python 代码来对接 Docker。
