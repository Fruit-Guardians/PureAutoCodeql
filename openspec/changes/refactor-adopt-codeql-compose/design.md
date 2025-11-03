## Context
- 项目存在多条“生成 QL”的实现路径：
  - `Analyze.py` 使用 `CodeQLGeneratorAgent` 直接生成 QL
  - `agents/python_source_analysis_agent.py`、`agents/c_source_analysis_agent.py` 依赖缺失的 `tools.codeql_generator_tool`
  - 测试与工具目录已引入 `tools/codeql_compose.py`（`CodeQLComposeTool`）
- 目标：不改工具实现，仅通过架构统一“生成 QL”的入口，复用 `CodeQLComposeTool` 的“生成+执行校验”迭代能力。

## Goals / Non-Goals
- Goals
  - 统一所有“生成 QL”调用点为 `CodeQLComposeTool._arun(requirement)`
  - 保持与现有多 Agent 架构兼容（`MultiAgentAnalyzer` 注入、数据库路径与语言配置）
  - 保留执行器（`CodeQLRunnerAgent`）以维持“生成/执行/分析”职责解耦
- Non-Goals
  - 不修改 `tools/codeql_compose.py` 工具本身
  - 不在此变更中实现缺失的 `tools.codeql_generator_tool`/`tools.codeql_runner_tool`

## Decisions
- Integration pattern
  - 在需要“生成 QL”的位置以依赖注入方式构造 `CodeQLComposeTool(analyzer, database_path, language, max_rounds)`
  - 仅调用 `await tool._arun(requirement)`；返回文本中的 ```ql 代码块或 `<codeql></codeql>` 内容作为最终查询
- Call-site coverage
  - `agents/python_source_analysis_agent.generate_source_codeql_query`
  - `agents/c_source_analysis_agent.generate_source_codeql_query`
  - `Analyze.py/run_multi_agent_analysis`（替换 `CodeQLGeneratorAgent` 生成步骤）
  - `GenerateCodeQL.py`（保留 RAG 构建提示词，但生成改为 ComposeTool）
- Compatibility
  - `utils/codeql.execute_codeql_query` 继续作为工具内部执行器由 Compose 触发；独立执行仍可走 `CodeQLRunnerAgent`

## Risks / Trade-offs
- 风险：测试与 `tools/__init__.py` 的导出与旧工具命名不一致（`CodeQLGeneratorTool`/`CodeQLRunnerTool` 缺失）
  - 缓解：测试迁移到 `CodeQLComposeTool`；对 Runner 相关测试延后到执行器统一阶段
- 风险：Compose 工具已内置执行校验，容易与外部 Runner 职责重叠
  - 缓解：明确“生成阶段由 Compose 完成最小可用校验；深入分析与批量执行仍走 Runner”

## Migration Plan
1) 枚举调用点并在提案任务中标注（已完成）
2) 为各调用点增加 `CodeQLComposeTool` 的构造参数规划（`analyzer`/`database_path`/`language`/`max_rounds`）
3) 生成路径改造：替换为 `await tool._arun(requirement)`（提交单独 PR）
4) 统一结果抽取逻辑（```ql 与 `<codeql>`）；对接 Runner/分析链路
5) 测试迁移与文档更新：`tools/README.md` 与 `tests`

## Open Questions
- `CodeQLRunnerTool` 是否需要在工具层补齐，以匹配 `CodeQLRunnerAgent` 的调用习惯？
- `GenerateCodeQL.py` 的 RAG 增强是否保留为可选步骤，默认直接使用 Compose？
