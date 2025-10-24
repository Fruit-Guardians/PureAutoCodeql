## ADDED Requirements

### Requirement: CodeQL 分析命令统一为 database analyze
系统 SHALL 使用 `codeql database analyze` 执行查询。

#### Scenario: 成功执行分析
- **WHEN** 运行查询
- **THEN** 实际调用 `codeql database analyze <database> <query-or-pack>`

### Requirement: 输出为 SARIF 2.1.0
系统 MUST 通过命令行参数指定：`--format=sarif-v2.1.0`。

#### Scenario: 正确的输出格式
- **WHEN** 执行分析
- **THEN** 输出文件格式为 SARIF 2.1.0

### Requirement: 固定输出路径与命名
系统 MUST 将结果输出到固定目录并带调用时间戳：`--output=/output/result_[工具调用时间].sarif`。

#### Scenario: 结果文件命名规范
- **WHEN** 执行分析
- **THEN** 在 `/output/` 下生成 `result_YYYYMMDD_HHMMSS.sarif` 文件（示例命名），其中时间来自工具调用时间戳
