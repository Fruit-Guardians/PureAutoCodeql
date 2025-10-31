## ADDED Requirements

### Requirement: Standardized CodeQL Generation for Source Agents
系统 SHALL 在 Source 分析相关 Agent 中统一使用 `tools/codeql_compose.py` 的 `CodeQLComposeTool` 作为 CodeQL 生成入口，替代分散或缺失的生成工具调用。

#### Scenario: Python 源码分析统一生成
- **WHEN** `agents/python_source_analysis_agent.py` 中的 `PythonSourceAnalysisAgent.generate_source_codeql_query(cve_analysis)` 被调用
- **THEN** 通过 `CodeQLComposeTool(analyzer=self.analyzer, database_path=self.database_path, language="python")` 创建工具并调用 `await tool._arun(requirement)` 生成查询

#### Scenario: C/C++ 源码分析统一生成
- **WHEN** `agents/c_source_analysis_agent.py` 中的 `CSourceAnalysisAgent.generate_source_codeql_query(cve_analysis)` 被调用
- **THEN** 通过 `CodeQLComposeTool(analyzer=self.analyzer, database_path=self.database_path, language="cpp")` 创建工具并调用 `await tool._arun(requirement)` 生成查询

#### Scenario: 结果抽取一致性
- **WHEN** 工具返回文本包含 CodeQL 代码
- **THEN** 系统从 ```ql 代码块或 `<codeql></codeql>` 标签中提取最终查询内容以供后续执行/分析
