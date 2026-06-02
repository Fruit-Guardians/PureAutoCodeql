## 导入 CVE 项目指南

本文档介绍如何把外部的 CVE 目录自动整理到 `projects/` 中，并在需要时创建 CodeQL 数据库。当前支持 **REST API** 与 **命令行 CLI** 两种入口。

### 🚀 快速开始（推荐）

**Java 和 Python 项目最简单使用方式**：

```bash
# Java 项目 - 一条命令完成导入、建库、分析！
python Analyze.py --case "C:\Targets\java\CVE-2023-51444"

# Python 项目 - 同样简单
python Analyze.py --case "C:\Targets\python\CVE-2022-26129"
```

✨ **无需额外参数！** 系统会自动：
- 📦 导入项目到 `projects/`
- 🏗️ 创建 CodeQL 数据库（Java 使用 `--build-mode=none`）
- 🚀 启动 AI 漏洞分析

💡 **C/C++ 项目**：如果没有标准构建文件（CMake/Makefile），需要先用 `--import-project` 指定构建命令。

---

### 1. 目录准备要求

- 外部目录建议以 `CVE-YYYY-XXXXX` 命名，例如：`C:\Users\bxx\Desktop\qwb_targets1\targets\python\CVE-2025-54381`
- 目录结构统一为：

```
CVE-YYYY-XXXXX/
├── CVE-YYYY-XXXXX.json        # 元数据
├── patch/                     # 若干 .patch（或历史 .diff）文件
└── src/ 或 source_code/       # 解压后的源码目录，或源码 zip 包
```

- `src/` 或 `source_code/` 下若直接是源码目录，会被完整复制到 `projects/<CVE>/source_code/`
- `src/` 或 `source_code/` 下若只有 zip 包，系统会自动解压到 `source_code/`
- 如果展开后的 `source_code/` 只剩单个发行目录（例如 `security_monkey-0.7.0/`），系统会自动把该目录作为 CodeQL `--source-root`
- `patch/` 内的 `.patch`（兼容 `.diff`）文件会自动命名为 `CVE-YYYY-XXXXX-*.patch`，并同步到 `db/` 与 `inputs/`
- 其余自定义资料可以保留，稍后可再手动放入 `inputs/`

---

### 2. 通过 API 调用

接口：`POST /api/projects/import`

#### 示例 1：Java 项目导入（最简单）

```json
{
  "source_path": "C:\\Users\\bxx\\Desktop\\qwb_targets1\\targets\\java\\CVE-2023-51444",
  "case_id": "CVE-2023-51444",
  "overwrite": true,
  "language": "java",
  "skip_codeql": false
}
```

#### 示例 2：C/C++ 项目导入（需要构建命令）

```json
{
  "source_path": "C:\\Users\\bxx\\Desktop\\qwb_targets1\\targets\\cpp\\CVE-2025-50000",
  "case_id": "CVE-2025-50000",
  "overwrite": true,
  "language": "cpp",
  "skip_codeql": false,
  "build_command": "cmake --build build",
  "build_workdir": "C:\\Users\\bxx\\Desktop\\PureAutoCodeql\\projects\\CVE-2025-50000"
}
```

参数说明：
- `source_path` **(必填)**：外部目录的绝对路径
- `case_id`：手动指定项目 ID；不填则根据目录名或 `CVE-*.json` 自动推断
- `overwrite`：目标目录已存在时是否覆盖
- `language`：强制指定 CodeQL 语言（默认自动检测，支持 `python`/`java`/`cpp`）
- `skip_codeql`：设为 `true` 时跳过 `codeql database create`
- `build_command`：**仅 C/C++ 需要**（除非命中自动策略），等价于 `codeql database create --command`
  - **Java 和 Python 项目会忽略此参数**
- `build_script`：构建脚本路径（仅 C/C++ 使用，相对于导入后项目根或绝对路径）
- `build_workdir`：执行构建命令的工作目录（仅 C/C++ 使用，默认为项目根）

响应中会返回：
- `case_id` / `target_path` / `language`
- `metadata_files`：成功复制的 `CVE-*.json/.diff/.patch`
- `codeql_created` 与 `codeql_error`
- `project`：与 `GET /projects/{case_id}` 相同的项目详情

---

### 3. 通过 CLI (`Analyze.py`)

#### Java 项目命令示例（Windows）：

```powershell
python Analyze.py --import-project "C:\Users\bxx\Desktop\qwb_targets1\targets\java\CVE-2023-51444" `
    --import-case-id CVE-2023-51444 `
    --import-overwrite `
    --import-language java
```

#### C/C++ 项目命令示例（Windows）：

```powershell
python Analyze.py --import-project "C:\Users\bxx\Desktop\qwb_targets1\targets\cpp\CVE-2025-50000" `
    --import-case-id CVE-2025-50000 `
    --import-overwrite `
    --import-language cpp `
    --import-build-command "cmake --build build" `
    --import-build-dir "C:\Users\bxx\Desktop\PureAutoCodeql\projects\CVE-2025-50000"
```

常用参数：
- `--import-project PATH`：必填，指定要导入的目录
- `--import-case-id ID`：自定义项目名称
- `--import-overwrite`：允许覆盖同名项目
- `--import-language LANG`：指定语言（`python`/`java`/`cpp`）
- `--import-skip-codeql`：跳过 CodeQL 数据库创建
- `--import-build-command CMD`：C/C++ 构建命令（传给 `codeql database create --command`）
- `--import-build-script PATH`：构建脚本（相对项目根或绝对路径，支持 `.sh/.ps1/.bat`）
- `--import-build-dir DIR`：构建命令的工作目录（默认项目根）

导入成功后，终端会输出案例 ID、目标路径、复制的元数据以及 CodeQL 构建结果。

---

### 3.1 直接在主流程中输入目录（推荐方式）

**最简单的使用方式**：直接将外部目录的绝对路径传给 `--case` 参数，系统会自动导入、建库并启动分析：

#### Java 项目示例（最简单）：

```powershell
# 一条命令完成：导入 -> 建库 -> 分析
python Analyze.py --case "C:\Targets\java\CVE-2023-51444"
```

**执行流程**：
1. 🔍 检测到外部目录（包含路径分隔符）
2. 📦 自动导入到 `projects/CVE-2023-51444/`
3. 🏗️ 使用 `--build-mode=none` 创建Java数据库（无需编译）
4. 🚀 启动完整的AI漏洞分析流程

#### C/C++ 项目示例：

```powershell
python Analyze.py --case "C:\Targets\cpp\CVE-2025-50000"
```

⚠️ **注意**：C/C++ 项目如果没有 `CMakeLists.txt` 或 `Makefile`，建库可能会失败。建议先用 `--import-project` 手动指定构建命令。

#### Python 项目示例：

```powershell
python Analyze.py --case "C:\Targets\python\CVE-2025-54381"
```

**特点**：
- ✅ 该模式会默认覆盖同名项目
- ✅ 自动创建 CodeQL 数据库
- ✅ 建库完成后立即执行全量分析
- ✅ **Java 和 Python 无需任何额外参数，一条命令搞定！**

---

### 4. 语言特定的自动建库说明

#### 4.1 Java 自动建库

- Java 项目**不需要编译追踪**，自动使用 `--build-mode=none`
- 无需提供构建命令（`build_command`、`build_script` 参数会被忽略）
- 自动检测并深入单一子目录（如解压后的发行版目录）
- 建库命令示例：
  ```bash
  codeql database create db/java --language=java --source-root=source_code --build-mode=none
  ```
- CodeQL 输出：`projects/<CVE>/db/codeql.log`

#### 4.2 C/C++ 自动建库

- **优先级**：`build_command` > `build_script` > 自动检测
- **自动检测**：
  - 发现 `CMakeLists.txt` 时，导入器会执行 `cmake -S source_code -B build`，并使用 `cmake --build build` 创建数据库
  - 发现 `Makefile` 时，默认命令为 `make -j`
- 构建日志：`projects/<CVE>/db/build.log`
- CodeQL 输出：`projects/<CVE>/db/codeql.log`
- 如自动检测失败，请显式提供 `--import-build-command` 或 `--import-build-script`

#### 4.3 Python 自动建库

- Python 项目**不需要编译**，直接分析源代码
- 自动检测并深入单一子目录
- 无需提供构建命令

---

### 5. 导入后的目录结构

```
projects/
└── CVE-2025-54381/
    ├── source_code/   # 完整源代码
    ├── db/            # CVE JSON/Diff + <language>/CodeQL DB
    ├── inputs/        # 同步保存的 JSON/Diff 及其他资料
    ├── intel/
    └── README.md      # 记录来源
```

可以通过以下方式验证：
1. `python Analyze.py --case CVE-2025-54381` 启动分析
2. `GET /api/projects/CVE-2025-54381` 获取详情

---

### 6. 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `source_code` 不存在 | 确保外部目录已解压出 `source_code/`；暂不支持从根目录推断 |
| CodeQL CLI 未安装 | 使用 `--import-skip-codeql` 或在 API 请求中设 `skip_codeql=true` |
| Java 项目建库失败 | 检查 `db/codeql.log`，确认是否有 `.java` 文件；Java 使用 `--build-mode=none`，无需编译 |
| 未提供 C/C++ 构建命令 | 追加 `build_command` 或 `build_script`；若使用 CMake/Make，请确保相应文件存在 |
| 构建失败 | 查看 `db/build.log` 与 `db/codeql.log`，修复依赖后重试导入或仅重建数据库 |
| 目录已存在但不想覆盖 | 不要加 `--import-overwrite` / `overwrite: true`，系统会报错提醒 |
| 语言检测错误 | 手动通过 `language`/`--import-language` 指定 (`python`/`java`/`cpp`) |

如需批量导入，可在脚本中循环调用 API 或 CLI。欢迎根据实际流程继续扩展。


