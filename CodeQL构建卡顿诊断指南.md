# CodeQL 构建在 100% 后卡顿问题诊断指南

## 问题现象

在使用 Docker 容器进行 C/C++ 项目的 CodeQL 数据库构建时，编译进度显示 `[100%] Built target xxx` 后，进程看起来卡住不动，没有任何输出。

## 问题原因

编译完成后，CodeQL 还需要执行 **`database finalize`** 步骤，这个步骤会：

1. **压缩源代码**：生成 `src.zip` 文件，包含整个项目的源码
2. **创建数据库索引**：建立语义分析所需的索引结构
3. **最终处理**：完成数据库的元数据生成

对于大型项目（如 curl、linux kernel 等），这个步骤可能需要 **5-15 分钟甚至更长**，但 CodeQL 默认不输出详细的进度信息，所以看起来像是卡住了。

### 具体原因分析：

1. **源码压缩耗时长**
   - 大型项目包含大量文件（特别是测试文件）
   - 压缩算法需要遍历和打包所有文件
   - 对于 curl 这样的项目，可能有数千个文件

2. **Docker 容器资源不足**
   - 默认 Docker 内存限制可能过低
   - CPU 核心数限制影响压缩速度
   - 磁盘 I/O 性能限制

3. **数据库索引构建**
   - 需要分析所有编译信息
   - 构建 AST（抽象语法树）索引
   - 生成各种元数据

## 解决方案

### 已实施的优化

我已经对代码进行了以下优化：

#### 1. 增强日志输出（`docker/builder_entrypoint.py`）

- ✅ 添加 finalize 步骤的警告提示
- ✅ 显示当前数据库大小
- ✅ 显示内存和磁盘使用情况
- ✅ 记录每个步骤的耗时
- ✅ 启用 `--verbose` 模式

**效果**：用户可以看到进度信息，不再误以为卡死。

#### 2. 增加 Docker 容器资源（`utils/project_importer.py`）

- ✅ 内存限制：8GB（`--memory=8g`）
- ✅ CPU 核心：4核（`--cpus=4`）
- ✅ 共享内存：2GB（`--shm-size=2g`）

**效果**：finalize 步骤运行更快，减少等待时间。

### 如何使用

1. **重新构建 Docker 镜像**（如果修改了 entrypoint）：
```bash
cd docker
docker build -t codeql-builder:latest .
```

2. **运行项目导入**：
```bash
uv run Analyze.py --case "路径/到/你的/C项目"
```

3. **观察新的输出**：
```
[Builder] >>> Step 4: Finalize Database
[Builder] 警告：finalize 步骤可能需要 5-15 分钟，请耐心等待...
[Builder] 正在生成 src.zip 和数据库索引...
[Builder] 当前数据库大小: 256M    /out/db
[Builder] 内存状态: Mem:    7.8Gi   2.1Gi   3.2Gi   ...
[Builder] 磁盘空间: /dev/sda1   100G   45G   50G   48%   /
[Builder] 开始 finalize...
[Running codeql database finalize with verbose output...]
[Builder] Finalize 完成，耗时: 387.5 秒
```

### 如果仍然卡顿

#### 检查 1：确认不是真的卡死

使用 `docker stats` 查看容器资源使用：

```bash
docker stats
```

如果看到 CPU 和内存使用率较高，说明进程正在运行，只是没有输出。

#### 检查 2：查看详细日志

日志文件位于：`projects/<case_id>/db/docker_build.log`

```bash
tail -f projects/<case_id>/db/docker_build.log
```

#### 检查 3：调整资源限制

如果你的机器资源充足，可以进一步增加：

编辑 `utils/project_importer.py`：

```python
cmd = [
    "docker", "run", "--rm",
    "--memory=16g",  # 增加到 16GB
    "--cpus=8",      # 增加到 8核
    "--shm-size=4g", # 增加共享内存
    # ... 其他参数
]
```

#### 检查 4：排除特定文件/目录

对于特别大的项目，可以在源码目录创建 `.codeqlignore` 文件：

```gitignore
# 忽略测试文件
tests/
test/
**/test/**

# 忽略第三方库
third_party/
vendor/
node_modules/

# 忽略示例代码
examples/
samples/
```

这样可以显著减少 `src.zip` 的大小和 finalize 时间。

### 典型耗时参考

| 项目规模 | 文件数 | 编译时间 | Finalize 时间 | 总耗时 |
|---------|-------|---------|--------------|-------|
| 小型项目（<100 文件） | <100 | 1-2 分钟 | 30-60 秒 | ~3 分钟 |
| 中型项目（几百文件） | 200-500 | 3-5 分钟 | 2-5 分钟 | ~10 分钟 |
| 大型项目（如 curl） | 1000+ | 5-10 分钟 | 5-15 分钟 | ~25 分钟 |
| 超大项目（如 Linux） | 10000+ | 30-60 分钟 | 30-60 分钟 | 1-2 小时 |

## 常见错误信息

### 1. "Finalize failed with exit code 1"

**原因**：内存不足或磁盘空间不足

**解决**：
- 增加 Docker 内存限制
- 清理磁盘空间
- 检查 `/tmp` 目录是否已满

### 2. "src.zip too small (< 1KB)"

**原因**：编译没有正确执行，或源码提取失败

**解决**：
- 检查编译日志
- 确认构建命令正确
- 检查源码路径是否正确挂载

### 3. 容器无响应，CPU 0%

**原因**：进程可能真的卡死了（罕见）

**解决**：
```bash
# 强制停止容器
docker kill $(docker ps -q --filter ancestor=codeql-builder)

# 清理数据库，重新开始
rm -rf projects/<case_id>/db/cpp
uv run Analyze.py --case "..." --overwrite
```

## 性能优化建议

### 1. 使用 SSD 存储
CodeQL 数据库构建涉及大量磁盘 I/O，SSD 可以显著提升速度。

### 2. 充足的内存
建议至少 8GB RAM，大型项目建议 16GB+。

### 3. 多核 CPU
finalize 步骤可以利用多核并行处理，建议至少 4 核。

### 4. 预编译过滤
如果只关注特定模块，可以修改构建脚本只编译需要的部分。

## 监控脚本

我已经创建了一个 Python 监控脚本 `monitor_build.py`，可以实时监控构建进度。

### 功能特性：

- ✅ 实时显示数据库大小
- ✅ 显示关键文件（src.zip、codeql-database.yml）状态
- ✅ 显示最新构建日志
- ✅ 监控 Docker 容器资源使用（CPU、内存）
- ✅ 自动检测项目语言
- ✅ 显示已运行时间
- ✅ 跨平台支持（Windows/Linux/Mac）

### 使用方法：

**开始监控**：
```bash
# 基本用法
python monitor_build.py CVE-2018-14618

# 指定刷新间隔（秒）
python monitor_build.py CVE-2018-14618 10

# 或者使用 uv
uv run monitor_build.py CVE-2018-14618
```

**示例输出**：
```
🔍 监控项目: CVE-2018-14618
📝 语言: C/C++
📂 数据库路径: projects/CVE-2018-14618/db/cpp
⏱️  刷新间隔: 5 秒
============================================================
按 Ctrl+C 停止监控
============================================================

[17:24:15] 已运行: 5分23秒
  📦 数据库大小: 256.5MB
  ✅ src.zip: 128.3MB
  ⏳ codeql-database.yml: 等待中...
  📄 构建日志: 2.1MB
  💬 最新日志: [Builder] Finalize 完成，耗时: 387.5 秒
  🐳 Docker 容器: 运行中
     CPU: 85.32% | 内存: 3.2GiB / 8GiB (40.25%)
```

### 同时运行构建和监控

**方式 1：使用两个终端窗口**

终端 1 - 启动构建：
```bash
uv run Analyze.py --case "C:\path\to\project"
```

终端 2 - 启动监控：
```bash
python monitor_build.py CVE-2018-14618
```

**方式 2：使用后台运行（Linux/Mac）**

```bash
# 后台运行构建
uv run Analyze.py --case "path/to/project" &

# 前台运行监控
python monitor_build.py CVE-2018-14618
```

## 总结

**请耐心等待！** 如果你看到：
- ✅ `[100%] Built target xxx` - 编译已完成
- ✅ 容器 CPU/内存使用率较高 - 正在处理
- ✅ 数据库目录大小持续增长 - 进度正常

那么系统正在正常工作，只是 finalize 步骤需要较长时间。

更新后的代码会显示详细的进度信息和预估时间，帮助你了解构建状态。

