# 分析用例模板

使用此目录作为新分析用例的起始模板。

## 设置说明

1. **重命名父文件夹**：从 `case-template` 改为您的用例 ID（例如 `CVE-2025-54802`）。
2. **复制项目源码**：放入 `source_code/` 目录。
3. **导入或创建 CodeQL 数据库**：在 `db/<语言>/` 目录下。
4. **放置 CVE JSON / diff 文件**：放入 `inputs/` 目录。
5. **保持 `intel/` 目录为空**：运行编排器后会自动填充。

## 目录结构

```text
case-template/                    # 模板目录
├── source_code/                  # 项目源码
├── db/                          # CodeQL 数据库
│   └── <language>/             # 特定语言数据库
├── inputs/                     # CVE 文件和补丁
├── intel/                      # 生成的情报数据
└── README.md                   # 本文件
```

---

## Case Template

Use this directory as a starting point for new analysis cases.

## Setup Instructions

1. **Rename the parent folder** from `case-template` to your case ID (for example `CVE-2025-54802`).
2. **Copy project sources** into `source_code/`.
3. **Import or create CodeQL databases** under `db/<language>/`.
4. **Place CVE JSON / diff files** in `inputs/`.
5. **Leave `intel/` empty**; it will be populated automatically after running the orchestrator.

## Directory Structure

```text
case-template/                    # Template directory
├── source_code/                  # Project source code
├── db/                          # CodeQL databases
│   └── <language>/             # Language-specific databases
├── inputs/                     # CVE files and patches
├── intel/                      # Generated intelligence
└── README.md                   # This file
```
