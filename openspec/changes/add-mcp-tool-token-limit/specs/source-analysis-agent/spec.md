## ADDED Requirements
### Requirement: MCP工具输出Token限制
系统 SHALL 在所有MCP工具返回数据给Agent之前，对输出内容进行Token数量限制，确保不超过8000 tokens。

#### Scenario: Token计数
- **WHEN** MCP工具执行完成并返回输出
- **THEN** 系统使用tiktoken库计算输出的Token数量
- **AND** 如果tiktoken导入失败，使用字符数除以4作为估算

#### Scenario: Token截断
- **WHEN** 工具输出Token数量超过8000
- **THEN** 按Token截断内容，保留前40%和后60%的Token
- **AND** 在截断处添加省略标记
- **AND** 在输出开头添加反馈信息说明Token数量和截断情况

#### Scenario: 小输出不截断
- **WHEN** 工具输出Token数量在8000以内
- **THEN** 不进行任何截断，原样返回给Agent

#### Scenario: 适用所有MCP工具
- **WHEN** 初始化MultiAgentAnalyzer并获取MCP工具列表
- **THEN** 为所有工具（filesystem、ripgrep等）添加Token限制包装
- **AND** 包装后的工具保留原有的错误处理属性
