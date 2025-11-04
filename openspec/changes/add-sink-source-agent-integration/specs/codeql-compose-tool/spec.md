## ADDED Requirements

### Requirement: Sink和Source点查询支持
CodeQLComposeTool SHALL 提供针对Sink和Source点查找的专门查询支持。

#### Scenario: Sink点查询生成
- **WHEN** Agent请求查找危险函数调用点（Sink点）
- **THEN** 生成针对特定语言和漏洞类型的CodeQL查询
- **AND** 查询目标包括SQL执行、命令执行、文件操作等危险函数
- **AND** 使用`exec_mode='run'`模式返回文本格式结果

#### Scenario: Source点查询生成
- **WHEN** Agent请求查找数据源点（Source点）
- **THEN** 生成针对特定语言的数据输入源查询
- **AND** 查询目标包括网络输入、文件读取、环境变量等数据源
- **AND** 使用`exec_mode='run'`模式返回文本格式结果

#### Scenario: 查询结果格式优化
- **WHEN** 使用`exec_mode='run'`模式执行Sink/Source查询
- **THEN** 返回的文本结果包含清晰的文件路径、行号和函数信息
- **AND** 结果格式便于Agent直接从返回内容中分析和提取关键信息
- **AND** 提供结果预览和完整文件路径（如需要进一步分析）

#### Scenario: 多语言Sink/Source查询支持
- **WHEN** Agent指定不同的编程语言
- **THEN** 根据语言特性生成相应的Sink/Source查询
- **AND** 支持Java、Python、C/C++三种主要语言的查询模式
- **AND** 利用语言特定的危险函数库和数据源模式
