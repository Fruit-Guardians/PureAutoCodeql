## ADDED Requirements

### Requirement: Unified CodeQL Composition Tool Integration
系统 SHALL 统一采用 `tools/codeql_compose.py` 的 `CodeQLComposeTool` 作为“生成 QL”的唯一入口，覆盖全局调用点并保持与现有多 Agent 架构兼容。

#### Scenario: Analyze 主流程统一生成
- **WHEN** `Analyze.py` 的 `run_multi_agent_analysis` 进入“CodeQL Query Generation”阶段
- **THEN** 使用 `CodeQLComposeTool(analyzer, database_path=db_path, language="java")` 并调用 `await tool._arun(requirement)` 生成查询；生成阶段不再通过 `CodeQLGeneratorAgent.generate_codeql`

#### Scenario: 独立脚本统一生成
- **WHEN** `GenerateCodeQL.py` 需要生成 CodeQL 查询
- **THEN** 仍可使用 RAG 丰富提示词，但最终通过 `CodeQLComposeTool._arun(requirement)` 完成生成与执行校验

#### Scenario: 结果格式与抽取
- **WHEN** 工具返回包含查询代码
- **THEN** 支持从 ```ql 代码块与 `<codeql></codeql>` 标签中提取最终查询文本，以对接后续执行/分析组件

#### Scenario: 与执行器兼容
- **WHEN** 需要分离“生成/执行”职责
- **THEN** 允许继续使用 `CodeQLRunnerAgent` 或相关工具对查询进行独立执行与分析，不改变其接口与职责
