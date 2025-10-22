## 1. Implementation
- [ ] 1.1 在 `Analyze.py` 中新增 `JavaSourceAnalysisAgent` 类，复用现有的 `MultiAgentAnalyzer` 配置
- [ ] 1.2 实现 `build_prompt` 方法，构造专门用于Source点分析的提示词
- [ ] 1.3 实现 `analyze_java_sources` 方法，接收CVE分析结果和Java文件路径，执行Source点分析
- [ ] 1.4 新增 `write_analysis_output` 函数，将Source和Sink分析结果写入 `output.md` 文件
- [ ] 1.5 更新 `run_multi_agent_analysis` 函数，集成第三个Source分析Agent到工作流中
- [ ] 1.6 确保代码风格与现有代码保持一致，仅在函数上添加docstring注释

## 2. Integration
- [ ] 2.1 确保 `JavaSourceAnalysisAgent` 与现有的 `JavaPathAnalysisAgent` 使用相同的输入数据格式
- [ ] 2.2 验证三个Agent的执行顺序：CVE分析 → Sink分析 → Source分析 → 结果输出
- [ ] 2.3 实现适当的错误处理，确保单个Agent失败不影响其他Agent的执行
- [ ] 2.4 确保输出文件格式清晰，能够区分Source和Sink的分析结果

## 3. Validation
- [ ] 3.1 运行完整的多Agent分析工作流，确认三个Agent都能正常执行
- [ ] 3.2 验证 `output.md` 文件生成正确，包含Source和Sink的分析结果
- [ ] 3.3 测试异常情况：Agent执行失败、文件写入权限等，确认错误处理正常
- [ ] 3.4 确认新功能不破坏现有的CVE分析和Sink分析功能