## Why
将独立脚本 `sarif2json.py` 的核心逻辑下沉为可复用工具函数并在 CodeQL 执行成功后自动进行 SARIF→JSON 转换，有助于：
- 统一转换实现，便于其他模块复用与测试
- 在生成 SARIF 的同时产出配套 JSON，方便后续可视化或数据消费
- 将转换参数集中到 `config.py` 管理，降低硬编码与分散配置成本

## What Changes
- 提取 `sarif2json.py` 核心转换逻辑为工具函数，新增文件 `utils/sarif_utils.py`，提供：
  - `sarif_to_paths(sarif: dict, max_results: int, threadflow_index: int, rule_filter: str | None, make_relative_to: str | None) -> dict`
  - `write_paths_json(sarif_path: str, json_out: str, max_results: int, threadflow_index: int, rule_filter: str | None, relative_to: str | None) -> int`（返回写入的路径条目数）
- 在 QL 生成与执行流程（`tools/codeql_compose.py`）中：
  - 当 SARIF 成功生成时，自动调用 `write_paths_json`，在与 SARIF 同目录输出同名的 `.json` 文件（如 `xxx.sarif` → `xxx.json`）。
- 在 `config.py` 中新增 SARIF→JSON 转换配置：
  - `max_results`（默认 3）
  - `threadflow_index`（默认 0）
  - `rule_filter`（默认 None）
  - 默认值与当前 `sarif2json.py` 一致。
- 注释全部使用中文，保持代码简洁。

## Impact
- Affected specs: 新增/扩展 `codeql-compose` 能力
- Affected code:
  - 新增 `utils/sarif_utils.py`
  - 修改 `tools/codeql_compose.py` 在执行成功后触发自动转换
  - 修改 `config.py` 增加 SARIF→JSON 的配置项
  - 调整 `sarif2json.py`（保留为薄包装或迁移说明）
