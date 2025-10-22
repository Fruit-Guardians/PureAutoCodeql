## MODIFIED Requirements
### Requirement: Java Source Analysis Agent
系统 SHALL 提供一个新的 `JavaSourceAnalysisAgent` 类，专门用于分析Java源代码中的Source点（数据来源点），与现有的Sink分析Agent形成完整的数据流分析能力。

#### Scenario: 接收相同的输入数据
- **WHEN** Source分析Agent被调用
- **THEN** 接收与 `JavaPathAnalysisAgent`（Sink分析Agent）相同的输入：CVE分析结果和Java文件路径列表

#### Scenario: 自主CodeQL工具使用
- **WHEN** Agent分析Java源代码
- **THEN** 能够自主调用codeql_generator工具生成CodeQL查询
- **AND** 能够自主调用codeql_runner工具执行查询
- **AND** 基于查询结果识别和定位Source点

#### Scenario: 工具集成优化
- **WHEN** Agent执行Source分析
- **THEN** 直接使用集成的CodeQL工具进行查询生成和执行
- **AND** 将查询结果整合到分析过程中
- **AND** 确保工具调用的可靠性和错误处理

#### Scenario: 使用相同的工具和配置
- **WHEN** 初始化Source分析Agent
- **THEN** 复用现有的MultiAgentAnalyzer配置，使用相同的LLM和MCP工具

#### Scenario: 构建专门的分析提示词
- **WHEN** 构建分析提示词
- **THEN** 针对Source点分析优化提示词，指导Agent识别数据流的起始点

## MODIFIED Requirements
### Requirement: 分析结果输出功能
系统 SHALL 提供一个 `write_analysis_output` 函数，将Source和Sink两个Agent的分析结果整合输出到统一文件。

#### Scenario: 整合分析结果
- **WHEN** Source和Sink分析完成
- **THEN** 将两个Agent的分析结果整合到 `output.md` 文件中

#### Scenario: 结构化输出格式
- **WHEN** 写入输出文件
- **THEN** 使用清晰的Markdown格式，分别展示Source分析和Sink分析的结果

## MODIFIED Requirements
### Requirement: 多Agent工作流集成
系统 SHALL 更新 `run_multi_agent_analysis` 函数，集成第三个Source分析Agent到现有工作流中。

#### Scenario: 三Agent顺序执行
- **WHEN** 执行多Agent分析工作流
- **THEN** 按顺序执行：CVE分析 → Java路径分析（Sink） → Java路径分析（Source） → 结果输出

#### Scenario: 保持向后兼容
- **WHEN** 更新工作流
- **THEN** 不破坏现有的CVE分析和Sink分析功能

#### Scenario: 错误处理
- **WHEN** 任一Agent执行失败
- **THEN** 记录错误信息并继续执行其他Agent，确保部分结果仍可输出

#### Scenario: CodeQL工具错误处理
- **WHEN** CodeQL工具调用失败
- **THEN** 记录详细的错误信息并尝试降级处理
- **AND** 确保Agent能够优雅地处理工具调用异常
