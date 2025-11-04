# codeql-compose-tool Specification (delta)

## ADDED Requirements

### Requirement: CodeQLComposeTool 新增“常规查询（run）模式”
系统 SHALL 在 `CodeQLComposeTool` 中新增执行模式 `run`，使用 `codeql query run` 产出 BQRS，并通过 `codeql bqrs decode` 解码为可读文本。

#### Scenario: 执行方式与输出落盘
- WHEN `CodeQLComposeTool._arun(..., exec_mode='run')` 被调用
- THEN 使用 `codeql query run <query> --database <db> --output <bqrs>` 执行查询
- AND 使用 `codeql bqrs decode --format=table <bqrs>`（或等价的纯文本格式）进行解码
- AND 将结果文本写入 `./temp/search_temp/query_<timestamp>.txt`
- AND Agent 返回的字符串中包含该“结果文件路径”与“结果内容片段（预览）”信息

#### Scenario: 结果内容过长的控制
- WHEN 结果内容较长
- THEN 返回字符串中仅包含片段预览（例如前若干行）
- AND 完整内容可通过“结果文件路径”读取

#### Scenario: 空结果
- WHEN 查询无匹配结果
- THEN 仍创建文本文件并写入空结果或表头信息
- AND 返回字符串中明确标识无结果

#### Scenario: 路径与兼容性
- WHEN 在 Windows 环境执行
- THEN 路径处理应使用 `pathlib.Path` 确保兼容，并最终以字符串形式返回

#### Scenario: 超时与错误
- WHEN 子进程调用超时或失败
- THEN 返回结构中包含明确错误信息，并且不影响既有 analyze 模式

### Requirement: 维持 analyze→SARIF 模式行为不变
系统 SHALL 保持 `exec_mode='analyze'` 的默认行为不变，继续使用 `codeql database analyze` 输出 SARIF（并在成功后按现有策略进行 SARIF→JSON 自动转换）。

#### Scenario: 向后兼容
- WHEN 未显式传入 `exec_mode` 或显式传入 `exec_mode='analyze'`
- THEN 维持原有返回字符串结构，包含 SARIF 路径与（可选）路径 JSON 信息
- AND 不修改现有的自动转换配置与实现
