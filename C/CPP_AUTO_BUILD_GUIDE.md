# C/C++ 自动化建库完整方案

## 一、架构说明

采用 **Docker 容器化构建** + **智能配置读取** 方案，实现跨平台（Windows/Linux）的 C/C++ 项目自动建库。

### 核心优势
1. **环境隔离**：所有编译依赖封装在 Docker 镜像中，避免宿主机环境污染
2. **依赖冲突解决**：通过 `pre-build.sh` 钩子动态调整容器内环境
3. **智能降级**：用户指定 → CMake/Make 探测 → CodeQL Autobuild 三级策略
4. **零配置使用**：配置写入 `keys.toml` 后自动生效，无需手动设置环境变量

---

## 二、使用指南

### 1. 一次性准备（已完成）

✅ Docker 镜像已构建：`pure-codeql-cpp:latest`  
✅ 配置已写入：`config/keys.toml`

```toml
[settings]
use_docker_for_cpp = true
docker_builder_image = "pure-codeql-cpp:latest"
```

### 2. 导入项目并自动建库

**命令格式**：
```powershell
python Analyze.py --import-project <项目路径> --import-case-id <案例ID> --import-language cpp --import-overwrite
```

**实际示例**（OpenSSL Heartbleed）：
```powershell
python Analyze.py `
    --import-project "C:\Users\bxx\Desktop\qwb_targets1\targets\c\CVE-2014-0160" `
    --import-case-id CVE-2014-0160 `
    --import-language cpp `
    --import-overwrite
```

### 3. 系统自动执行流程

1. **复制源码** → `projects/CVE-2014-0160/source_code`
2. **启动 Docker 容器**（挂载源码和输出目录）
3. **容器内自动执行**：
   - `codeql database init`
   - 检测并执行 `pre-build.sh`（如果存在）
   - 探测构建系统：CMake → Make → Autobuild
   - `codeql database finalize`
   - 校验 `src.zip` 大小
4. **输出结果** → `projects/CVE-2014-0160/db/cpp`

### 4. 日志与排错

- **容器日志**：`projects/<CASE>/db/docker_build.log`
- **CodeQL 日志**：`projects/<CASE>/db/codeql.log`
- **构建日志**：`projects/<CASE>/db/build.log`

---

## 三、高级用法

### 场景 1：项目需要特殊依赖

在 `source_code/` 根目录创建 `pre-build.sh`：

```bash
#!/bin/bash
# 示例：安装旧版 OpenSSL 1.0.2
apt-get remove -y libssl-dev
wget https://www.openssl.org/source/openssl-1.0.2g.tar.gz
tar -xf openssl-1.0.2g.tar.gz
cd openssl-1.0.2g
./config && make && make install
```

系统会在编译前自动执行此脚本。

### 场景 2：手动指定构建命令

```powershell
python Analyze.py `
    --import-project "路径" `
    --import-case-id CVE-XXXX `
    --import-language cpp `
    --import-build-command "./configure && make -j4"
```

### 场景 3：使用构建脚本

```powershell
python Analyze.py `
    --import-project "路径" `
    --import-case-id CVE-XXXX `
    --import-language cpp `
    --import-build-script "build.sh"
```

---

## 四、配置优先级

系统按以下顺序读取配置（后者覆盖前者）：

1. **默认值**（`api/config.py` 中的 `Field(default=...)`）
2. **`config/keys.toml` 的 `[settings]`**
3. **环境变量**（`API_USE_DOCKER_FOR_CPP`）
4. **`.env` 文件**

推荐使用 `keys.toml`，便于版本管理和团队共享。

---

## 五、故障排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 仍然走宿主机 autobuild | 配置未生效 | 检查 `keys.toml` 中 `use_docker_for_cpp = true` |
| Docker 容器无法启动 | 镜像不存在 | 运行 `docker images` 确认镜像存在 |
| 编译失败（缺少依赖） | 镜像未预装该库 | 编写 `pre-build.sh` 安装依赖 |
| `src.zip` 太小 | 构建命令未实际编译 | 检查 `docker_build.log`，调整构建命令 |
| 路径挂载错误 | Windows 路径格式问题 | 确保使用绝对路径，Docker Desktop 会自动转换 |

---

## 六、后续扩展

1. **多版本镜像**：构建 `pure-codeql-cpp:legacy`（Ubuntu 18.04 + GCC 7）支持老旧项目
2. **批量导入**：编写脚本循环调用 `Analyze.py --import-project`
3. **Web 界面**：通过 `/api/projects/import` 接口实现可视化导入
4. **CI/CD 集成**：在 GitHub Actions 中自动构建镜像并推送到私有仓库

---

## 七、命令速查

```powershell
# 导入项目（标准流程）
python Analyze.py --import-project <路径> --import-case-id <ID> --import-language cpp --import-overwrite

# 查看 Docker 日志
cat projects/<CASE>/db/docker_build.log

# 手动运行容器（调试用）
docker run --rm -v <源码路径>:/src -v <输出路径>:/out/db pure-codeql-cpp:latest

# 重新构建镜像（更新依赖）
docker build -t pure-codeql-cpp:latest ./docker
```

---

**现在，你只需要一行命令就能完成 C/C++ 项目的自动建库！**




