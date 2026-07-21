# 项目工作区

每个分析用例都位于 `projects/<case-id>/` 下，具有以下结构：

```text
projects/
└── <case-id>/
    ├── source_code/    # 反编译源码或待分析项目
    ├── queries/        # 用例特定的 CodeQL 查询或覆盖
    ├── db/             # CodeQL 数据库 (例如 db/java, db/python)
    ├── inputs/         # 手动准备的资产 (CVE JSON, diff 文件等)
    └── intel/          # 缓存的 GHSA/NVD 情报数据
```

设置流程：

1. 复制 `case-template/` 到新的 `<case-id>` 文件夹。
2. 将项目源码放入 `source_code/` (如需要可使用特定语言子文件夹)。
3. 在 `db/<语言>/` 下创建或导入 CodeQL 数据库。
4. 将至少一个 `CVE-*.json` (和可选的 `CVE-*.diff`) 文件放入 `inputs/`。
5. 运行 `Analyze.py --case <case-id>` 或 `pure-auto-codeql analyze --case <case-id>` 来初始化情报并开始分析。

GHSA 和 NVD 获取器将结果缓存在 `intel/` 中。当上游公告变更时，使用 `--refresh-intel` 标志强制重新获取。可通过 `GITHUB_TOKEN` / `NVD_API_KEY` 环境变量或相应的 CLI 标志提供凭据。

---

## Projects Workspace

Each analysis case lives under `projects/<case-id>/` with the following layout:

```text
projects/
└── <case-id>/
    ├── source_code/    # Decompiled sources or project under analysis
    ├── queries/        # Case-specific CodeQL queries or overrides
    ├── db/             # CodeQL databases (e.g. db/java, db/python)
    ├── inputs/         # Hand-prepared assets (CVE JSON, diff files, etc.)
    └── intel/          # Cached GHSA/NVD intelligence artifacts
```

Setup flow:

1. Copy `case-template/` to a new `<case-id>` folder.
2. Drop the project sources into `source_code/` (use language-specific subfolders if needed).
3. Create or import the CodeQL database under `db/<language>/`.
4. Place at least one `CVE-*.json` (and optional `CVE-*.diff`) file into `inputs/`.
5. Run `Analyze.py --case <case-id>` or `pure-auto-codeql analyze --case <case-id>` to seed intelligence and start the analysis.

GHSA and NVD fetchers cache results in `intel/`. Use the `--refresh-intel` flag to force a re-fetch when upstream advisories change. Credentials can be provided via `GITHUB_TOKEN` / `NVD_API_KEY` environment variables or the corresponding CLI flags.
