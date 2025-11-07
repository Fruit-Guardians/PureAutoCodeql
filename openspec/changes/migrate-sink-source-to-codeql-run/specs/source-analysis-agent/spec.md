## MODIFIED Requirements
### Requirement: 统一Source分析Agent
系统 SHALL 提供一个 `UnifiedSourceAnalysisAgent` 类，支持使用 CodeQLComposeTool 的 run 模式进行 Source 点分析。

#### Scenario: 使用 CodeQLComposeTool 进行 Source 分析
- **WHEN** Source 分析 Agent 被调用
- **THEN** 使用 CodeQLComposeTool 的 run 模式生成和执行 Source 点查询
- **AND** 返回查询结果文件路径和内容预览

#### Scenario: 保持向后兼容
- **WHEN** Agent 接口被调用
- **THEN** 保持与现有接口完全兼容
- **AND** 返回格式与之前一致

#### Scenario: fallback 机制
- **WHEN** CodeQL 查询生成或执行失败
- **THEN** 回退到原有的基于提示词的分析方法
- **AND** 返回相应的错误信息

## ADDED Requirements
### Requirement: CodeQLComposeTool 集成
系统 SHALL 在 UnifiedSourceAnalysisAgent 中集成 CodeQLComposeTool 以提供自动化的 Source 点分析能力。

#### Scenario: 初始化 CodeQLComposeTool
- **WHEN** UnifiedSourceAnalysisAgent 被初始化
- **THEN** 同时初始化 CodeQLComposeTool 实例
- **AND** 配置正确的数据库路径和语言设置

#### Scenario: 执行 Source 点查询
- **WHEN** analyze_sources 方法被调用
- **THEN** 构建适合 Source 分析的 CodeQL 查询需求描述
- **AND** 调用 CodeQLComposeTool 的 run 模式执行查询
- **AND** 处理返回的文本结果和文件路径
