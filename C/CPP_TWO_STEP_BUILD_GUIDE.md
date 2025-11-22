# C/C++ 两步走自动建库完整指南

## 一、核心思想

基于"先确保能手动 make 成功，再让 CodeQL 去执行 make"的最佳实践，实现了以下智能构建策略：

### 两步走策略详解

**第一步：预备动作（在 CodeQL 之外完成）**
- 目标：生成 Makefile，确保环境就绪
- 自动检测并执行：
  1. `./buildconf` → `./configure`
  2. `./configure`
  3. `./autogen.sh` → `./configure`
  4. `cmake -S . -B build`

**第二步：正式动作（用 CodeQL 包裹编译命令）**
- 目标：拦截编译器调用，提取数据
- 命令：`codeql database create ... --command="make -j4"`
- 自动清理旧数据库，避免干扰

---

## 二、三级智能降级策略

系统会按照以下顺序自动尝试：

```
┌─────────────────────────────────────┐
│  策略1：本地两步走构建（默认）      │
│  - 自动检测 configure/buildconf     │
│  - 预先生成 Makefile               │
│  - CodeQL 包裹 make -j4            │
└──────────┬──────────────────────────┘
           │ 失败？
           ↓
┌─────────────────────────────────────┐
│  策略2：Docker Autobuild（回退）   │
│  - 容器内自动探测构建系统          │
│  - 完整的依赖环境                  │
│  - 自动 fallback 到 none 模式      │
└──────────┬──────────────────────────┘
           │ 失败？
           ↓
┌─────────────────────────────────────┐
│  策略3：报错并提供诊断信息         │
└─────────────────────────────────────┘
```

---

## 三、快速开始

### 1. 配置文件设置（推荐）

编辑 `config/keys.toml`，添加以下配置：

```toml
[settings]
# 启用本地两步走构建（默认开启）
prefer_local_cpp_build = true

# 预备阶段超时时间（秒）
local_build_prepare_timeout = 300

# Docker 回退配置
use_docker_for_cpp = false  # 不强制使用 Docker
docker_builder_image = "pure-codeql-cpp:latest"
```

### 2. 导入项目并自动建库

```powershell
python Analyze.py `
    --import-project "C:\path\to\project" `
    --import-case-id CVE-2024-XXXX `
    --import-language cpp `
    --import-overwrite
```

### 3. 观察构建日志

系统会自动打印每个步骤的执行情况：

```
============================================================
策略1：尝试本地两步走构建
============================================================
[INFO] 优先使用本地两步走构建策略...
[INFO] 检测到 configure，执行预备构建步骤...
[INFO] Running command with timeout=300s: bash ./configure
...
[INFO] ✅ 预备步骤成功，Makefile已生成
[INFO] 将使用 CodeQL 包裹 make 命令进行构建
...
============================================================
✅ 本地两步走构建成功！
============================================================
```

---

## 四、支持的项目类型

| 项目类型 | 检测方式 | 预备步骤 | 构建命令 |
|---------|---------|---------|---------|
| **Autotools** | 存在 `configure` | `./configure` | `make -j4` |
| **Autotools (源码)** | 存在 `buildconf` | `./buildconf && ./configure` | `make -j4` |
| **Autotools (Git)** | 存在 `autogen.sh` | `./autogen.sh && ./configure` | `make -j4` |
| **CMake** | 存在 `CMakeLists.txt` | `cmake -S . -B build` | `cmake --build build -j 4` |
| **纯 Makefile** | 存在 `Makefile` | 无 | `make -j4` |
| **其他** | 无标准构建系统 | 无 | `autobuild` 或 Docker |

---

## 五、典型场景示例

### 场景 1：cURL 项目（需要 buildconf）

```powershell
# 项目结构：
# curl/
#   ├── buildconf       ← 生成 configure 脚本
#   ├── configure.ac
#   └── src/

python Analyze.py --import-project curl --import-case-id CVE-2024-CURL --import-language cpp

# 系统自动执行：
# 1. 检测到 buildconf
# 2. 执行 ./buildconf（生成 configure）
# 3. 执行 ./configure（生成 Makefile）
# 4. codeql database create --command="make -j4"
```

### 场景 2：OpenSSL 项目（需要 configure）

```powershell
# 项目结构：
# openssl/
#   ├── Configure       ← Perl 配置脚本
#   ├── config          ← Shell 包装脚本
#   └── crypto/

python Analyze.py --import-project openssl --import-case-id CVE-2014-0160 --import-language cpp

# 系统自动执行：
# 1. 检测到 configure
# 2. 执行 ./configure
# 3. codeql database create --command="make -j4"
```

### 场景 3：CMake 项目

```powershell
# 项目结构：
# myproject/
#   ├── CMakeLists.txt
#   └── src/

python Analyze.py --import-project myproject --import-case-id CVE-2024-CMAKE --import-language cpp

# 系统自动执行：
# 1. 检测到 CMakeLists.txt
# 2. 执行 cmake -S . -B build
# 3. codeql database create --command="cmake --build build -j 4"
```

### 场景 4：复杂项目（需要自定义命令）

```powershell
# 如果项目需要特殊的配置参数
python Analyze.py `
    --import-project myproject `
    --import-case-id CVE-2024-CUSTOM `
    --import-language cpp `
    --import-build-command "./configure --enable-debug --with-ssl && make -j4"
```

---

## 六、配置选项详解

### 本地构建配置

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|-------|------|
| `prefer_local_cpp_build` | bool | `true` | 是否优先使用本地两步走构建 |
| `local_build_prepare_timeout` | int | `300` | 预备阶段超时时间（秒） |

### Docker 回退配置

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|-------|------|
| `use_docker_for_cpp` | bool | `false` | 是否强制使用 Docker（跳过本地构建） |
| `docker_builder_image` | str | `pure-codeql-cpp:latest` | Docker 镜像名称 |

### 配置优先级

系统按以下顺序读取配置（后者覆盖前者）：

1. **代码默认值**（`api/config.py` 中的 `Field(default=...)`）
2. **`config/keys.toml` 的 `[settings]`**（推荐）
3. **环境变量**（如 `API_PREFER_LOCAL_CPP_BUILD`）
4. **`.env` 文件**

---

## 七、工作原理深度解析

### 1. 预备步骤检测逻辑

```python
def _try_prepare_cpp_build(source_dir: Path, log_path: Path) -> bool:
    # 检测顺序（按实际需求排序）：
    
    # 1. buildconf（生成 configure）
    if (source_dir / "buildconf").exists():
        run("./buildconf")
        run("./configure")
        return check_makefile_exists()
    
    # 2. configure（直接使用）
    if (source_dir / "configure").exists():
        run("./configure")
        return check_makefile_exists()
    
    # 3. autogen.sh（生成 configure）
    if (source_dir / "autogen.sh").exists():
        run("./autogen.sh")
        run("./configure")
        return check_makefile_exists()
    
    return False
```

### 2. 构建计划解析

```python
def _resolve_cpp_build_plan(...) -> BuildPlan:
    # 优先级1：用户指定命令
    if user_command:
        return BuildPlan(command=user_command, ...)
    
    # 优先级2：用户指定脚本
    if build_script:
        return BuildPlan(command=script_path, ...)
    
    # 优先级3：两步走自动构建
    if config.prefer_local_cpp_build:
        if _try_prepare_cpp_build(...):
            return BuildPlan(command="make -j4", description="configure+make")
    
    # 优先级4：CMake
    if (source_dir / "CMakeLists.txt").exists():
        run("cmake -S . -B build")
        return BuildPlan(command="cmake --build build -j 4", ...)
    
    # 优先级5：现有 Makefile
    if (source_dir / "Makefile").exists():
        return BuildPlan(command="make -j4", ...)
    
    # 优先级6：autobuild
    return BuildPlan(mode="autobuild", ...)
```

### 3. 智能回退机制

```python
def _create_codeql_database(...):
    if language == "cpp":
        # 策略1：本地两步走
        if config.prefer_local_cpp_build:
            try:
                execute_local_build()
                validate_database()
                return  # ✅ 成功
            except Exception as e:
                logger.warning("本地构建失败: %s", e)
                
                # 策略2：Docker 回退
                if config.docker_builder_image:
                    try:
                        execute_docker_build()
                        return  # ✅ 成功
                    except Exception as docker_error:
                        raise RuntimeError(f"Both builds failed")
```

---

## 八、故障排查

### 问题 1：configure 失败

**现象**：
```
[ERROR] Command failed with exit code 1: bash ./configure
configure: error: cannot find required library
```

**原因**：缺少编译依赖

**解决方案**：

方案 A（推荐）：让系统回退到 Docker
```toml
# config/keys.toml
[settings]
prefer_local_cpp_build = true  # 先尝试本地
docker_builder_image = "pure-codeql-cpp:latest"  # 失败后用 Docker
```

方案 B：安装本地依赖
```powershell
# Ubuntu/Debian
sudo apt-get install build-essential libssl-dev zlib1g-dev

# Windows (WSL)
wsl sudo apt-get install build-essential
```

---

### 问题 2：预备步骤超时

**现象**：
```
[ERROR] Command timed out after 300 seconds: bash ./configure
```

**解决方案**：增加超时时间
```toml
# config/keys.toml
[settings]
local_build_prepare_timeout = 600  # 增加到10分钟
```

---

### 问题 3：Makefile 未生成

**现象**：
```
[WARNING] configure 执行失败: ...
[INFO] 未发现明确构建指令或标准构建文件，启用 CodeQL autobuild 模式
```

**诊断**：
1. 检查日志文件：`projects/<CASE>/db/build.log`
2. 手动测试配置命令：
   ```bash
   cd projects/<CASE>/source_code
   bash ./configure
   ls -la Makefile  # 检查是否生成
   ```

**解决方案**：
- 如果是配置参数问题，使用自定义命令：
  ```powershell
  python Analyze.py ... --import-build-command "./configure --enable-shared && make"
  ```

---

### 问题 4：想跳过本地构建，直接用 Docker

**解决方案**：
```toml
# config/keys.toml
[settings]
prefer_local_cpp_build = false  # 禁用本地构建
use_docker_for_cpp = true       # 强制使用 Docker
```

或者临时使用环境变量：
```powershell
$env:API_PREFER_LOCAL_CPP_BUILD="false"
$env:API_USE_DOCKER_FOR_CPP="true"
python Analyze.py ...
```

---

## 九、性能优化建议

### 1. 并行编译

默认使用 `-j4`（4个并行任务），可根据 CPU 核心数调整：

```toml
# 在 keys.toml 中不直接支持，需修改代码或使用自定义命令
```

临时方案：
```powershell
python Analyze.py ... --import-build-command "make -j8"
```

### 2. 跳过不必要的构建步骤

如果只需要分析特定模块：
```powershell
python Analyze.py ... --import-build-command "cd src/target && make -j4"
```

### 3. 使用 ccache 加速重复编译

```bash
# 安装 ccache
sudo apt-get install ccache

# 在配置命令中启用
python Analyze.py ... --import-build-command "CC='ccache gcc' CXX='ccache g++' ./configure && make -j4"
```

---

## 十、与旧版本的区别

| 特性 | 旧版本 | 新版本（两步走） |
|-----|--------|----------------|
| **构建方式** | 直接 Docker 或本地 autobuild | 本地两步走 + Docker 回退 |
| **configure 支持** | ❌ 需要手动处理 | ✅ 自动检测执行 |
| **buildconf 支持** | ❌ | ✅ 自动检测执行 |
| **CMake 预配置** | ❌ | ✅ 在 CodeQL 外执行 |
| **失败回退** | ❌ | ✅ 自动回退到 Docker |
| **日志可见性** | 较差 | ✅ 分步骤清晰输出 |
| **配置灵活性** | 低 | ✅ 多级配置 + 环境变量 |

---

## 十一、命令速查表

```powershell
# ===== 基本导入 =====
python Analyze.py --import-project <路径> --import-case-id <ID> --import-language cpp --import-overwrite

# ===== 自定义构建命令 =====
python Analyze.py ... --import-build-command "./configure --enable-debug && make -j4"

# ===== 自定义工作目录 =====
python Analyze.py ... --import-build-command "make -j4" --import-workdir "src"

# ===== 使用构建脚本 =====
python Analyze.py ... --import-build-script "build.sh"

# ===== 强制使用 Docker =====
$env:API_USE_DOCKER_FOR_CPP="true"
python Analyze.py ...

# ===== 禁用本地构建 =====
$env:API_PREFER_LOCAL_CPP_BUILD="false"
python Analyze.py ...

# ===== 查看构建日志 =====
# 预备步骤 + CodeQL 日志
cat projects/<CASE>/db/build.log

# CodeQL 创建日志
cat projects/<CASE>/db/codeql.log

# Docker 日志（如果使用了 Docker）
cat projects/<CASE>/db/docker_build.log
```

---

## 十二、最佳实践总结

### ✅ 推荐做法

1. **默认配置即可**：新版本的默认配置已经很智能，大部分项目无需额外配置
2. **保留 Docker 镜像**：即使优先本地构建，也建议保留 Docker 作为回退
3. **查看日志**：构建失败时，优先查看 `build.log` 和 `codeql.log`
4. **增量调试**：先让系统自动尝试，失败后再根据日志定制命令

### ❌ 避免的错误

1. **不要在 Windows 本地强制执行 Unix 脚本**：如果项目是 Linux 专用，应该使用 Docker 或 WSL
2. **不要同时设置 `use_docker_for_cpp=true` 和 `prefer_local_cpp_build=true`**：前者会跳过后者
3. **不要盲目增加超时时间**：如果 configure 一直卡住，可能是配置本身有问题

---

## 十三、后续扩展计划

- [ ] 支持 Meson 构建系统
- [ ] 支持 Bazel 构建系统
- [ ] 自动检测并安装缺失的依赖（基于错误日志）
- [ ] 构建缓存机制（避免重复 configure）
- [ ] Web UI 可视化构建过程

---

**现在，你只需要一行命令 + 零配置，就能自动完成 95% 的 C/C++ 项目建库！**

**核心理念**："先确保能手动 make 成功，再让 CodeQL 去执行 make。" 💪

