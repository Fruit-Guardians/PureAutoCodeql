## 1. 优化CodeQL工具自主调用
- [ ] 1.1 优化 `generate_source_codeql_query` 方法，确保Agent能自主生成查询
- [ ] 1.2 优化 `execute_source_codeql_query` 方法，确保Agent能自主执行查询
- [ ] 1.3 简化 `analyze_java_sources` 方法的调用流程
- [ ] 1.4 确保工具调用的错误处理和日志记录

## 2. 改进提示词构建
- [ ] 2.1 优化 `build_prompt` 方法，强调Agent自主使用CodeQL工具
- [ ] 2.2 优化 `build_prompt_with_codeql_results` 方法，更好地利用查询结果
- [ ] 2.3 确保提示词指导Agent正确使用codeql_generator和codeql_runner工具

## 3. 简化分析流程
- [ ] 3.1 移除不必要的复杂性，专注于工具集成
- [ ] 3.2 确保Agent能够独立完成整个分析过程
- [ ] 3.3 保持输出格式的一致性和可预测性

## 4. 错误处理和日志
- [ ] 4.1 添加CodeQL工具调用的详细日志记录
- [ ] 4.2 实现优雅的错误处理和恢复机制
- [ ] 4.3 确保工具调用失败时的降级处理

## 5. 测试和验证
- [ ] 5.1 创建单元测试验证工具集成
- [ ] 5.2 测试不同CVE场景下的分析效果
- [ ] 5.3 验证Agent能够自主完成CodeQL分析流程
