## 1. Agent 构造函数参数化
- [x] 1.1 修改 `JavaSourceAnalysisAgent.__init__()` 方法，添加 `source_root` 参数
  - 添加 `source_root: str = "h5-vsan-service.jar_Decompiler.com"` 参数
  - 更新 `self.source_root = source_root` 赋值逻辑
  - 保持向后兼容性，确保无参数调用仍然有效
- [x] 1.2 修改 `JavaPathAnalysisAgent.__init__()` 方法，添加 `source_root` 参数
  - 添加 `source_root: str = "h5-vsan-service.jar_Decompiler.com"` 参数
  - 更新 `self.source_root = source_root` 赋值逻辑
  - 保持向后兼容性，确保无参数调用仍然有效

## 2. 主分析函数参数化
- [x] 2.1 修改 `run_multi_agent_analysis()` 函数签名
  - 添加 `source_root: str = "h5-vsan-service.jar_Decompiler.com"` 参数
  - 保持现有的 `json_path` 和 `diff_path` 参数
  - 更新函数文档字符串，说明新参数的用途
- [x] 2.2 更新 Agent 实例化代码
  - 修改 `sink_agent = JavaPathAnalysisAgent(analyzer)` 为 `sink_agent = JavaPathAnalysisAgent(analyzer, source_root)`
  - 修改 `source_agent = JavaSourceAnalysisAgent(analyzer)` 为 `source_agent = JavaSourceAnalysisAgent(analyzer, source_root)`
  - 保持 `cve_agent = CVEAnalysisAgent(analyzer)` 不变

## 3. 主入口点简化
- [x] 3.1 简化 `main()` 函数参数设置
  - 在函数内部直接设置参数变量：
    - `source_root = "h5-vsan-service.jar_Decompiler.com"`  # 可根据需要修改
    - `json_path = "CVE-2021-21985.json"`  # 可根据需要修改
    - `diff_path = "CVE-2021-21985.diff"`  # 可根据需要修改
  - 添加注释说明如何修改这些参数进行调试
- [x] 3.2 实现简化的参数传递
  - 将设置的参数直接传递给 `run_multi_agent_analysis()` 函数
  - 确保参数传递链路简单明了
  - 保持向后兼容性

## 4. 参数验证和错误处理
- [ ] 4.1 实现路径参数验证
  - 检查 `source_root` 目录是否存在
  - 检查 `json_path` 文件是否存在且可读
  - 检查 `diff_path` 文件是否存在且可读
- [ ] 4.2 添加错误处理逻辑
  - 对于不存在的 `source_root`，记录警告但继续执行
  - 对于不存在的文件，提供清晰的错误信息
  - 实现优雅的错误恢复机制

## 5. 向后兼容性保证
- [ ] 5.1 验证现有调用方式
  - 确保 `await run_multi_agent_analysis()` 无参数调用仍然有效
  - 确保 `main()` 函数无参数调用使用默认值
  - 验证所有默认参数值与现有硬编码值一致
- [ ] 5.2 测试兼容性
  - 运行现有的分析流程，确保结果一致
  - 验证 Agent 初始化和执行逻辑正确
  - 确保没有引入破坏性变更

## 6. 配置集中化
- [ ] 6.1 简化配置管理
  - 确保所有参数在 `main()` 函数中集中设置
  - 添加清晰的注释说明每个参数的作用
  - 为调试提供便利的参数修改方式
- [ ] 6.2 优化参数组织
  - 将相关参数组织在一起，便于查找和修改
  - 添加参数说明和使用示例
  - 确保参数设置直观易懂

## 7. 文档和示例更新
- [ ] 7.1 更新代码注释
  - 在 `main()` 函数中添加参数设置的详细注释
  - 说明如何修改参数进行不同场景的调试
  - 提供参数修改的具体示例
- [ ] 7.2 更新函数文档
  - 为新增的参数添加详细的文档字符串
  - 更新函数和类的文档，说明参数化功能
  - 添加使用示例和最佳实践说明

## 8. 测试和验证
- [ ] 8.1 功能测试
  - 测试使用默认参数的分析流程
  - 测试使用自定义参数的分析流程
  - 验证不同参数组合的正确性
- [ ] 8.2 错误场景测试
  - 测试不存在的路径和文件的错误处理
  - 验证参数验证逻辑的正确性
  - 确保错误信息清晰且有用
- [ ] 8.3 性能和稳定性测试
  - 确保参数化不影响现有性能
  - 验证内存使用和资源管理
  - 测试长时间运行的稳定性