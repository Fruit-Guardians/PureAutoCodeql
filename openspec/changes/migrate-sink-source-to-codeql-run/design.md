# 设计文档：将 Sink 和 Source 查找修改为使用 CodeQLComposeTool 工具的 run 模式

## 背景
当前的 Sink 和 Source 查找 Agent 使用基于提示词的分析方法，虽然灵活但缺乏精确性。通过集成 CodeQLComposeTool 的 run 模式，我们可以利用 CodeQL 强大的静态分析能力来提高分析的准确性。

## 目标架构
```
UnifiedSinkPathAgent
├── CodeQLComposeTool (exec_mode='run')
├── Sink 点查询生成
└── 结果处理与返回

UnifiedSourceAnalysisAgent
├── CodeQLComposeTool (exec_mode='run')
├── Source 点查询生成
└── 结果处理与返回
```

## 技术决策

### 1. 为什么选择 run 模式
- run 模式返回文本结果和文件路径，更适合直接处理
- 与现有的 analyze 模式保持分离，避免影响现有功能
- 提供更直接的结果查看方式

### 2. 集成策略
- 保持现有 Agent 接口不变
- 在内部使用 CodeQLComposeTool 替代部分逻辑
- 提供 fallback 机制确保稳定性

### 3. 错误处理
- 查询生成失败时回退到原有提示词分析
- 执行失败时提供清晰的错误信息
- 保持原有返回格式确保兼容性

## 实现细节

### UnifiedSinkPathAgent 修改
1. 添加 CodeQLComposeTool 实例
2. 修改 analyze_paths 方法：
   - 构建适合 Sink 分析的查询需求描述
   - 调用 CodeQLComposeTool._arun(exec_mode='run')
   - 处理返回结果
3. 保留原有逻辑作为 fallback

### UnifiedSourceAnalysisAgent 修改
1. 添加 CodeQLComposeTool 实例
2. 修改 analyze_sources 方法：
   - 构建适合 Source 分析的查询需求描述
   - 调用 CodeQLComposeTool._arun(exec_mode='run')
   - 处理返回结果
3. 保留原有逻辑作为 fallback

## 数据流
1. Agent 接收分析请求
2. 构建 CodeQL 查询需求描述
3. 调用 CodeQLComposeTool 的 run 模式
4. CodeQLComposeTool 生成并执行查询
5. 返回文本结果和文件路径
6. Agent 处理结果并返回给调用方

## 风险与缓解

### 风险1：性能影响
- 缓解：提供配置选项控制是否启用 CodeQL 分析

### 风险2：查询生成失败
- 缓解：实现 fallback 机制，失败时回退到原有方法

### 风险3：结果格式不兼容
- 缓解：确保返回结果格式与现有接口一致

## 测试策略
1. 单元测试：验证 CodeQLComposeTool 调用逻辑
2. 集成测试：验证完整的分析流程
3. 兼容性测试：确保与现有系统兼容
4. 性能测试：评估对分析速度的影响
