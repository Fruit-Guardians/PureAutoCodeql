## Why
当前代码中存在多种 CodeQL 生成路径：
- `agents/python_source_analysis_agent.py` 与 `agents/c_source_analysis_agent.py` 依赖缺失的 `CodeQLGeneratorTool`。
- `Analyze.py` 使用 `CodeQLGeneratorAgent` 进行生成，与工具化能力不一致。
- `tools/__init__.py` 与测试已引入 `CodeQLComposeTool`，但未在主流程中统一使用。

为消除分散与失配、避免缺失模块导致运行时错误、并统一为可迭代“生成+校验”路径，需将所有“生成 QL”的调用统一切换为 `tools/codeql_compose.py` 的 `CodeQLComposeTool`。

## What Changes
- 统一“生成 QL”架构：所有调用点改为通过 `CodeQLComposeTool` 执行生成（并内置校验执行）。
- 为各语言在创建工具时传入 `analyzer`、`database_path`、`language`，调用异步 `_arun(requirement)` 生成查询。
- 结果抽取：从返回文本中提取 ```ql 代码块或 `<codeql></codeql>` 包裹内容；供后续执行或分析复用。
- 调用点覆盖：
  - `agents/python_source_analysis_agent.PythonSourceAnalysisAgent.generate_source_codeql_query`
  - `agents/c_source_analysis_agent.CSourceAnalysisAgent.generate_source_codeql_query`
  - `Analyze.py/run_multi_agent_analysis` 的“CodeQL Query Generation”阶段
  - `GenerateCodeQL.py` 脚本
- 保持其他工具与 Agent 不变（不修改 `CodeQLComposeTool` 本身；`CodeQLRunnerAgent/Tool` 可按需保留用于独立执行或分析）。

## Impact
- Affected specs: `source-analysis-agent`（MODIFIED），新增 `codeql-compose-integration` 能力规范（ADDED）。
- Affected code: `agents/python_source_analysis_agent.py`、`agents/c_source_analysis_agent.py`、`Analyze.py`、`GenerateCodeQL.py`；删除或弃用对缺失 `tools.codeql_generator_tool` 的引用。
- Risk: 需要在结果抽取与后续执行之间达成一致（Compose 工具已自带执行校验，但仍可保留 Runner 以维持既有分析输出流程）。
