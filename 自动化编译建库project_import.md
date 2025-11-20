## 导入 CVE 项目指南

本文档介绍如何把外部的 CVE 目录自动整理到 `projects/` 中，并在需要时创建 CodeQL 数据库。当前支持 **REST API** 与 **命令行 CLI** 两种入口。

---

### 1. 目录准备要求

- 外部目录建议以 `CVE-YYYY-XXXXX` 命名，例如：`C:\Users\bxx\Desktop\qwb_targets1\targets\python\CVE-2025-54381`
- 必须包含 `source_code/` 子目录（源代码会被复制到 `projects/<CVE>/source_code/`）
- 同级的 `CVE-*.json`、`CVE-*.diff/.patch` 文件会被复制一份到 `db/`，一份到 `inputs/`
- 其余自定义资料可以保留，稍后可再手动放入 `inputs/`

---

### 2. 通过 API 调用

接口：`POST /api/projects/import`

示例请求体：
```json
{
  "source_path": "C:\\Users\\bxx\\Desktop\\qwb_targets1\\targets\\python\\CVE-2025-54381",
  "case_id": "CVE-2025-54381",
  "overwrite": true,
  "language": "python",
  "skip_codeql": false
}
```

参数说明：
- `source_path` **(必填)**：外部目录的绝对路径
- `case_id`：手动指定项目 ID；不填则根据目录名或 `CVE-*.json` 自动推断
- `overwrite`：目标目录已存在时是否覆盖
- `language`：强制指定 CodeQL 语言（默认自动检测，仅 Python/Java/CPP）
- `skip_codeql`：设为 `true` 时跳过 `codeql database create`

响应中会返回：
- `case_id` / `target_path` / `language`
- `metadata_files`：成功复制的 `CVE-*.json/.diff/.patch`
- `codeql_created` 与 `codeql_error`
- `project`：与 `GET /projects/{case_id}` 相同的项目详情

---

### 3. 通过 CLI (`Analyze.py`)

命令示例（Windows）：
```powershell
python Analyze.py --import-project "C:\Users\bxx\Desktop\qwb_targets1\targets\python\CVE-2025-54381" `
    --import-case-id CVE-2025-54381 `
    --import-overwrite `
    --import-language python
```

常用参数：
- `--import-project PATH`：必填，指定要导入的目录
- `--import-case-id ID`：自定义项目名称
- `--import-overwrite`：允许覆盖同名项目
- `--import-language LANG`：指定语言（`python`/`java`/`cpp`）
- `--import-skip-codeql`：跳过 CodeQL 数据库创建

导入成功后，终端会输出案例 ID、目标路径、复制的元数据以及 CodeQL 构建结果。

---

### 4. 导入后的目录结构

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

### 5. 常见问题

| 问题 | 处理方式 |
| --- | --- |
| `source_code` 不存在 | 确保外部目录已解压出 `source_code/`；暂不支持从根目录推断 |
| CodeQL CLI 未安装 | 使用 `--import-skip-codeql` 或在 API 请求中设 `skip_codeql=true` |
| 目录已存在但不想覆盖 | 不要加 `--import-overwrite` / `overwrite: true`，系统会报错提醒 |
| 语言检测错误 | 手动通过 `language`/`--import-language` 指定 |

如需批量导入，可在脚本中循环调用 API 或 CLI。欢迎根据实际流程继续扩展。


