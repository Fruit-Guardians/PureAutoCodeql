# 变更提案：将 Sink 和 Source 查找修改为使用 CodeQLComposeTool 工具的 run 模式

- 变更编号（change-id）：migrate-sink-source-to-codeql-run
- 相关能力：sink-analysis-agent, source-analysis-agent
- 受影响模块：
  - agents/unified_sink_path_agent.py
  - agents/unified_source_analysis_agent.py
  - tools/codeql_compose.py

## 背景与动机
当前的 Sink 和 Source 查找 Agent 使用硬编码的提示词和手动分析方法，缺乏自动化和精确性。通过集成 CodeQLComposeTool 的 run 模式，可以实现：
- 自动化生成和执行 CodeQL 查询来查找 Sink 和 Source 点
- 提高分析的准确性和一致性
- 保持与现有 SARIF 输出格式的兼容性
- 利用 CodeQL 的强大静态分析能力

## 目标
- 将 UnifiedSinkPathAgent 修改为使用 CodeQLComposeTool 的 run 模式进行 Sink 点分析
- 将 UnifiedSourceAnalysisAgent 修改为使用 CodeQLComposeTool 的 run 模式进行 Source 点分析
- 保持现有 Agent 接口不变，确保向后兼容
- 利用 run 模式返回文本结果和文件路径的特性

## 非目标
- 不修改 CodeQLComposeTool 的核心实现
- 不改变现有分析流程的高层架构
- 不影响其他 Agent 的功能

## 提议的方案（高层设计）
- 在 UnifiedSinkPathAgent 中：
  - 集成 CodeQLComposeTool 作为分析工具
  - 使用 exec_mode='run' 执行 Sink 点查询
  - 处理返回的文本结果和文件路径
- 在 UnifiedSourceAnalysisAgent 中：
  - 集成 CodeQLComposeTool 作为分析工具
  - 使用 exec_mode='run' 执行 Source 点查询
  - 处理返回的文本结果和文件路径
- 保持现有 build_prompt 方法，但将其作为 CodeQL 查询生成的上下文

## API / 返回结构
- UnifiedSinkPathAgent.analyze_paths(...) 保持现有接口
- UnifiedSourceAnalysisAgent.analyze_sources(...) 保持现有接口
- 内部调用 CodeQLComposeTool._arun(..., exec_mode='run')
- 返回结构保持兼容，但内容来源于 CodeQL 查询结果

## 验收标准（Acceptance Criteria）
- Sink 分析：
  - 使用 CodeQLComposeTool 的 run 模式成功执行 Sink 点查询
  - 返回结果包含查询结果文件路径和内容预览
  - 保持与现有接口的兼容性
- Source 分析：
  - 使用 CodeQLComposeTool 的 run 模式成功执行 Source 点查询
  - 返回结果包含查询结果文件路径和内容预览
  - 保持与现有接口的兼容性

## 影响面
- 组件：
  - agents/unified_sink_path_agent.py
  - agents/unified_source_analysis_agent.py
  - tools/codeql_compose.py（作为依赖）
- 运行环境依赖：需要本地 codeql 可执行文件和正确构建的数据库

## 风险与缓解
- CodeQL 查询生成失败：提供 fallback 机制回到原有提示词分析
- 性能影响：run 模式可能比直接提示词分析慢，但准确性更高
- 兼容性问题：确保返回结果格式与现有接口兼容

## 迁移/回滚计划
- 新增功能默认为关闭，需要显式启用
- 如出现问题，可回退到原有提示词分析方式

## 时间线（建议）
- D1：提案评审与定稿
- D2-D3：实现 Sink Agent 集成
- D4-D5：实现 Source Agent 集成
- D6：测试与验证
- D7：文档更新与评审
