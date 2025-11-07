# Tasks for migrate-sink-source-to-codeql-run

## 1. 需求分析与设计
- [x] 1.1 分析 UnifiedSinkPathAgent 的现有功能和接口
- [x] 1.2 分析 UnifiedSourceAnalysisAgent 的现有功能和接口
- [x] 1.3 确定 CodeQLComposeTool 的集成点
- [x] 1.4 设计 run 模式的调用流程

## 2. Sink Agent 集成
- [x] 2.1 在 UnifiedSinkPathAgent 中集成 CodeQLComposeTool
- [x] 2.2 修改 analyze_paths 方法使用 CodeQLComposeTool 的 run 模式
- [x] 2.3 构建适合 Sink 分析的 CodeQL 查询需求描述
- [x] 2.4 处理 CodeQL 查询结果（文本内容和文件路径）
- [x] 2.5 实现 fallback 机制（查询失败时回退到原有方法）
- [x] 2.6 测试 Sink 分析功能

## 3. Source Agent 集成
- [ ] 3.1 在 UnifiedSourceAnalysisAgent 中集成 CodeQLComposeTool
- [ ] 3.2 修改 analyze_sources 方法使用 CodeQLComposeTool 的 run 模式
- [ ] 3.3 构建适合 Source 分析的 CodeQL 查询需求描述
- [ ] 3.4 处理 CodeQL 查询结果（文本内容和文件路径）
- [ ] 3.5 实现 fallback 机制（查询失败时回退到原有方法）
- [ ] 3.6 测试 Source 分析功能

## 4. 集成测试
- [ ] 4.1 测试 Sink 和 Source Agent 的接口兼容性
- [ ] 4.2 验证返回结果格式与现有系统兼容
- [ ] 4.3 测试 fallback 机制的有效性
- [ ] 4.4 性能测试和优化

## 5. 文档与验证
- [x] 5.1 更新相关文档
- [x] 5.2 运行 `openspec validate migrate-sink-source-to-codeql-run --strict` 并修复问题
- [ ] 5.3 提交评审，等待批准后实施合并
