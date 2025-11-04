## 实现任务清单

### 1. 提示词模板更新
- [ ] 1.1 分析现有Sink和Source Agent的提示词模板
- [ ] 1.2 更新sink提示词模板，要求使用codeql_compose工具
- [ ] 1.3 更新source提示词模板，要求使用codeql_compose工具
- [ ] 1.4 在提示词中添加`exec_mode='run'`结果提取和分析指导

### 2. Agent集成修改
- [ ] 2.1 修改UnifiedSinkPathAgent，集成CodeQLComposeTool调用
- [ ] 2.2 修改UnifiedSourceAnalysisAgent，集成CodeQLComposeTool调用
- [ ] 2.3 添加从`exec_mode='run'`返回结果中直接提取信息的逻辑
- [ ] 2.4 实现基于文本结果的报告生成

### 3. 错误处理和回退机制
- [ ] 3.1 添加CodeQL查询生成失败的异常处理
- [ ] 3.2 实现原有文件工具的回退机制
- [ ] 3.3 添加文本结果解析错误的处理逻辑
- [ ] 3.4 确保在任何情况下都能生成有效输出

### 4. 测试和验证
- [ ] 4.1 单元测试：验证Sink Agent使用CodeQLComposeTool
- [ ] 4.2 单元测试：验证Source Agent使用CodeQLComposeTool
- [ ] 4.3 集成测试：验证完整的分析流程
- [ ] 4.4 输出格式验证：确保与现有格式100%兼容

### 5. 性能优化和文档
- [ ] 5.1 监控CodeQL查询执行性能
- [ ] 5.2 优化提示词以减少查询生成时间
- [ ] 5.3 更新相关文档和使用说明
- [ ] 5.4 添加配置选项控制是否使用CodeQLComposeTool
