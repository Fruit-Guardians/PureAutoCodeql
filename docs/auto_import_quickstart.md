# PureAutoCodeQL 自动导入快速指南

本文档介绍如何使用主流程直接接入新的题目目录结构（`src/` + `patch/`），并让系统自动完成项目导入、补丁同步与 CodeQL 建库。

---

## 1. 环境前提
- 已安装 Python 及项目依赖（`pip install -r requirements.txt` 或使用项目默认环境）
- CodeQL CLI 可执行并已加入 `PATH`
- 新题目目录满足以下结构：
  ```
  CVE-XXXX-XXXX/
  ├── CVE-XXXX-XXXX.json
  ├── patch/
  │   └── *.patch  (兼容历史 *.diff)
  └── src/ 或 source_code/
      ├── <源码目录>  # 也可以是一个源码 zip 包
  ```

---

## 2. 运行命令

```powershell
python Analyze.py --case "C:\Targets\CVE-2025-54381"
```

### 命令说明
- `--case` 现在可以直接接受 **题目目录的绝对路径**。系统会自动：
  1. 将目录导入到 `projects/<CVE>/`
  2. 复制 `src/` / `source_code/`，在发现 zip 包时自动解压
  3. 若 `source_code/` 只有单个发行目录（如 `security_monkey-0.7.0/`），会自动把该子目录作为 CodeQL 的 `--source-root`
  3. 重命名并同步 `patch/*.patch`（或 `.diff`）到 `db/` 与 `inputs/`
  4. 尝试创建 CodeQL 数据库
  5. 完成后继续执行标准分析流程

如果传入的不是路径，而是 `CVE-XXXX-XXXX`（已导入过），则直接复用项目进行分析。

---

## 3. 常用可选参数

- `--provider <name>`：指定 LLM 提供商
- `--think-model / --chat-model`：覆盖默认模型
- `--refresh-intel`：强制刷新情报数据
- `--output <file>`：自定义结果输出文件

示例：

```powershell
python Analyze.py --case "D:\cases\CVE-2024-12345" `
    --provider deepseek `
    --output report.md `
    --refresh-intel
```

---

## 4. 手动导入模式（可选）

如果只想导入而不立即分析，可继续使用：

```powershell
python Analyze.py --import-project "C:\Targets\CVE-2025-54381" --import-overwrite
```

随后再运行 `python Analyze.py --case CVE-2025-54381` 进行分析。

---

## 5. 常见问题

| 问题 | 解决方案 |
| --- | --- |
| 目录没有 `src/` 或 `source_code/` | 确保源码正确放入 `src/`，或解压到 `source_code/` |
| 有多个 zip 包 | 默认使用第一个；建议保留单个源码包 |
| Patch 没带 CVE 前缀 | 系统会自动重命名为 `CVE-XXXX-XXXX-patch-XX.patch` |
| CodeQL 构建失败 | 查看 `projects/<CVE>/db/codeql.log` 与 `build.log`，修复依赖后重试 |

如需更多细节，请查看 `自动化编译建库project_import.md`。

