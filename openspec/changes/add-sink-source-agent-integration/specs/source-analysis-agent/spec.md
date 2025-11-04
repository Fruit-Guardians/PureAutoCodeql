## MODIFIED Requirements

### Requirement: 统一Source分析Agent
系统 SHALL 提供一个 `UnifiedSourceAnalysisAgent` 类，统一支持Java、Python和C/C++源代码中的Source点分析，并集成CodeQLComposeTool进行语义分析。

#### Scenario: 接收相同的输入数据
- **WHEN** Source分析Agent被调用
- **THEN** 接收与 `UnifiedSinkPathAgent`（Sink分析Agent）相同的输入：CVE分析结果、文件路径列表和编程语言类型

#### Scenario: 使用CodeQLComposeTool进行分析
- **WHEN** Agent分析源代码查找Source点
- **THEN** 优先使用CodeQLComposeTool的`exec_mode='run'`模式执行语义查询
- **AND** 直接从工具返回的文本结果中提取Source点信息
- **AND** 利用文本格式结果进行详细的Source点分析

#### Scenario: 分析Source点
- **WHEN** Agent分析源代码
- **THEN** 识别并定位可能的数据来源点（Source），如用户输入、外部数据源、配置文件等
- **AND** 支持Java、Python和C/C++三种编程语言
- **AND** 利用CodeQL的语义分析能力提高查找精度

#### Scenario: 使用相同的工具和配置
- **WHEN** 初始化Source分析Agent
- **THEN** 复用现有的MultiAgentAnalyzer配置，使用相同的LLM和MCP工具
- **AND** 集成CodeQLComposeTool进行查询执行

#### Scenario: 构建专门的分析提示词
- **WHEN** 构建分析提示词
- **THEN** 通过 `SourcePromptManager` 动态选择针对不同语言的优化提示词
- **AND** 提示词明确要求使用CodeQLComposeTool进行Source点查找

#### Scenario: 错误处理和回退机制
- **WHEN** CodeQL查询生成或执行失败
- **THEN** 自动回退到原有的文件工具分析方式
- **AND** 确保在任何情况下都能生成有效的Source分析结果

## ADDED Requirements

### Requirement: CodeQL文本结果解析和报告生成
系统 SHALL 提供从CodeQL查询文本结果中提取Source点信息并生成报告的能力。

#### Scenario: 解析查询文本结果
- **WHEN** CodeQLComposeTool返回`exec_mode='run'`的文本结果
- **THEN** 直接从返回的文本内容中提取Source点位置和信息
- **AND** 解析包含文件路径、行号、函数名等关键信息的结果

#### Scenario: 生成兼容格式的报告
- **WHEN** 基于CodeQL文本结果生成Source分析报告
- **THEN** 输出格式与现有文件工具方式完全一致
- **AND** 包含Source点的文件路径、行号、函数名等关键信息
