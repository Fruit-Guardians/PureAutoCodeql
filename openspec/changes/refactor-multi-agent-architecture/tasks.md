## 1. 架构重构
- [x] 1.1 重构 `Analyze.py` 的代码结构，将单一 Agent 逻辑拆分为可复用的组件
- [x] 1.2 创建 Agent 配置和创建的抽象函数，避免重复的 LLM 和 MCP 客户端初始化代码
- [x] 1.3 实现 Agent 间数据传递机制，支持第一个 Agent 的输出作为第二个 Agent 的输入
- [x] 1.4 优化函数命名和代码组织，提升代码可读性和可维护性

## 2. Java 路径分析 Agent
- [x] 2.1 集成现有的 `find_path_from_java_file` 函数到新的 Agent 工作流中
- [x] 2.2 配置 `source_root` 参数为 `h5-vsan-service.jar_Decompiler.com`
- [x] 2.3 实现 Java 文件路径获取逻辑，处理反编译后的 Java 代码结构
- [x] 2.4 为新 Agent 预留 prompt 配置接口（可使用占位符或空字符串）

## 3. 多 Agent 协作流程
- [x] 3.1 设计第一个 Agent（CVE JSON 分析）到第二个 Agent（Java 路径分析）的数据流
- [x] 3.2 实现将 CVE 分析结果、Java 文件路径和 diff 路径组合传递给第二个 Agent
- [x] 3.3 确保两个 Agent 能够顺序执行，第二个 Agent 能接收到完整的上下文信息
- [x] 3.4 保持现有 CVE 分析功能的向后兼容性

## 4. 代码质量和扩展性
- [x] 4.1 添加适当的错误处理，确保 Agent 链式调用的健壮性
- [x] 4.2 优化代码注释和文档字符串，保持代码风格的一致性
- [x] 4.3 设计可扩展的架构，为后续添加更多 Agent 提供基础
- [x] 4.4 确保重构后的代码与现有依赖和运行环境兼容

## 5. 验证和测试
- [x] 5.1 验证重构后的 CVE 分析功能仍然正常工作
- [x] 5.2 测试 Java 路径分析 Agent 能正确处理 `h5-vsan-service.jar_Decompiler.com` 目录
- [x] 5.3 验证多 Agent 协作流程的完整性和数据传递的正确性
- [x] 5.4 确保异常情况下的错误处理和用户反馈清晰明确