## Why
当前的 `Analyze.py` 实现了单一的 CVE 分析功能，但缺乏多 Agent 协作能力。为了支持更复杂的漏洞分析工作流，需要：
1. 新增一个专门的 Java 文件路径分析 Agent，使用 `find_path_from_java_file` 函数处理反编译的 Java 代码
2. 建立多 Agent 协作机制，让第一个 Agent（CVE JSON 分析）的输出能够传递给第二个 Agent
3. 重构代码结构，提升可维护性和扩展性，为后续添加更多 Agent 做准备

## What Changes
- 重构 `Analyze.py` 为多 Agent 架构，支持 Agent 间的数据流传递
- 新增 Java 路径分析 Agent，专门处理 `h5-vsan-service.jar_Decompiler.com` 目录下的 Java 文件
- 改进代码结构：
  - 将 Agent 创建和配置抽象为可复用的组件
  - 实现 Agent 间的输出传递机制
  - 优化函数命名和代码组织
- 集成现有的 `find_path_from_java_file` 函数到新的 Agent 工作流中
- 为新 Agent 预留 prompt 配置接口（当前可为空或占位符）

## Impact
- Affected specs: `multi-agent-analysis` (新增)
- Affected code: `Analyze.py` (重构)
- 向后兼容：保持现有 CVE 分析功能不变
- 扩展性：为后续添加更多分析 Agent 提供基础架构

## Dependencies
- 依赖现有的 `find_path_from_java_file` 函数
- 需要访问 `h5-vsan-service.jar_Decompiler.com` 目录
- 需要 diff 文件路径作为输入参数