## ADDED Requirements

### Requirement: Multi-Agent Architecture Support
系统 SHALL 支持多 Agent 架构，用于复杂的漏洞分析工作流。

#### Scenario: CVE 分析与 Java 路径解析协作
- **WHEN** 启动包含 CVE JSON 文件和反编译 Java 源码的分析流程
- **THEN** 第一个 Agent 分析 CVE JSON 并生成 Markdown 报告
- **AND** 第二个 Agent 使用 `find_path_from_java_file` 函数处理 Java 文件路径
- **AND** 两个 Agent 的输出被组合用于综合分析

### Requirement: Agent 配置管理
系统 SHALL 提供可复用的 Agent 配置和创建机制。

#### Scenario: 一致的 Agent 设置
- **WHEN** 需要创建多个 Agent
- **THEN** 它们 SHALL 使用一致的 LLM 和 MCP 客户端配置
- **AND** 通过抽象最小化配置重复

### Requirement: Agent 间数据流
系统 SHALL 支持顺序工作流中 Agent 间的数据传递。

#### Scenario: 顺序 Agent 执行
- **WHEN** 工作流中有多个 Agent
- **THEN** 第一个 Agent 完成分析后，其输出 SHALL 可用作后续 Agent 的输入
- **AND** 数据流 SHALL 保持上下文完整性

### Requirement: Java 源码路径分析
系统 SHALL 集成针对反编译源码的 Java 文件路径分析能力。

#### Scenario: Java 路径解析
- **WHEN** 处理 `h5-vsan-service.jar_Decompiler.com` 目录中的 Java 文件
- **THEN** Java 路径分析 Agent SHALL 使用正确的 source root 调用 `find_path_from_java_file` 函数
- **AND** 返回 Java 类的规范路径

### Requirement: 可扩展 Agent 框架
系统 SHALL 提供可扩展的框架用于添加新的分析 Agent。

#### Scenario: 添加新 Agent 类型
- **WHEN** 需要额外的分析能力
- **THEN** 新 Agent SHALL 无缝集成到现有的多 Agent 框架中
- **AND** 保持一致的接口和数据流模式

### Requirement: 向后兼容性
系统 SHALL 保持与现有 CVE 分析功能的向后兼容性。

#### Scenario: 现有功能保持不变
- **WHEN** 实现多 Agent 架构
- **THEN** 现有的 CVE JSON 到 Markdown 转换 SHALL 继续正常工作
- **AND** 不 SHALL 引入破坏性变更到公共接口