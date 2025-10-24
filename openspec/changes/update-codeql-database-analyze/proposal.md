## Why
当前 CodeQL 查询执行方式未标准化为官方推荐的 `codeql database analyze`，且未统一输出为 SARIF 2.1.0。为对接安全平台与审计工具，需要产出标准化的 SARIF 文件并固定输出路径与命名规范。

## What Changes
- 更新 `utils/codeql.py` 的查询执行逻辑为：`codeql database analyze`。
- 更新 `tools/codeql_runner_tool.py` 的调用逻辑，传递：
  - `--format=sarif-v2.1.0`
  - `--output=/output/result_[工具调用时间].sarif`
- 约定“工具调用时间”为本地时间戳（例如 `YYYYMMDD_HHMMSS`），用于结果文件命名。
- 保持现有功能行为不变，仅更改执行命令与输出格式/路径。

## Impact
- Affected specs: 新增能力 `codeql-runner`（统一 CodeQL 执行与输出规范）
- Affected code: `utils/codeql.py`, `tools/codeql_runner_tool.py`
- Downstream: 标准化的 SARIF 输出便于集成到 CI、安全平台与审计流程。
