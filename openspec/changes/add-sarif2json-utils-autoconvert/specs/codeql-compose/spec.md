## ADDED Requirements

### Requirement: SARIF 转 JSON 工具函数
系统 SHALL 提供一个工具模块 `utils/sarif_utils.py`，对原有 `sarif2json.py` 的核心逻辑进行抽取与封装，形成稳定复用接口。

#### Scenario: 提供核心转换函数
- **WHEN** 其他模块需要将 SARIF 转为路径 JSON
- **THEN** 可调用 `sarif_to_paths(sarif, max_results, threadflow_index, rule_filter, make_relative_to)` 返回 `{ "dataFlowPath": [...] }` 结构

#### Scenario: 提供文件写入便捷函数
- **WHEN** 需要直接从 SARIF 文件写出 JSON 文件
- **THEN** 可调用 `write_paths_json(sarif_path, json_out, max_results, threadflow_index, rule_filter, relative_to)` 将结果写入磁盘

### Requirement: SARIF 生成后自动输出同名 JSON
系统 SHALL 在 CodeQL 查询成功执行并产出 SARIF 后，自动输出与 SARIF 同名的 JSON 文件到同一目录（如 `xxx.sarif` → `xxx.json`）。

#### Scenario: 成功路径
- **WHEN** CodeQL 执行成功并提供 `sarif_path`
- **THEN** 系统调用工具函数将其转换并写出 `同名.json`（目录与 SARIF 相同）

#### Scenario: 失败兜底
- **WHEN** 转换或写入 JSON 失败
- **THEN** SHALL 记录错误但不影响已成功的 SARIF 产出

### Requirement: 参数集中于配置
系统 SHALL 在 `config.py` 中提供 SARIF→JSON 转换参数配置，默认值与现有 `sarif2json.py` 一致。

#### Scenario: 可配置参数
- **WHEN** 需要调整转换行为
- **THEN** 可在 `config.py` 中配置：`max_results`（默认 3）、`threadflow_index`（默认 0）、`rule_filter`（默认 None）

#### Scenario: 保持向后兼容
- **WHEN** 未显式配置
- **THEN** SHALL 使用默认参数，行为等同于现有脚本默认
