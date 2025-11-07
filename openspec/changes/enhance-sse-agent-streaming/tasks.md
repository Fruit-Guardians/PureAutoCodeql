# Implementation Tasks

## Phase 1: 扩展事件模型
- [ ] 1.1 在 `api/models.py` 的 `StreamEvent` 模型添加 `agent_name` 字段（可选）
- [ ] 1.2 在 `api/models.py` 的 `StreamEvent` 模型添加 `agent_type` 字段（可选）
- [ ] 1.3 在 `StreamEventType` 枚举添加 `AGENT_START` 类型
- [ ] 1.4 在 `StreamEventType` 枚举添加 `AGENT_THINKING` 类型
- [ ] 1.5 在 `StreamEventType` 枚举添加 `AGENT_TOOL_CALL` 类型
- [ ] 1.6 在 `StreamEventType` 枚举添加 `AGENT_COMPLETE` 类型

## Phase 2: Agent基础集成
- [ ] 2.1 修改 `agents/cve_analysis_agent.py` 的 `analyze` 方法接收 `event_callback` 参数
- [ ] 2.2 在CVE分析开始时推送 `AGENT_START` 事件
- [ ] 2.3 在CVE分析完成时推送 `AGENT_COMPLETE` 事件
- [ ] 2.4 修改 `agents/unified_sink_path_agent.py` 的 `analyze` 方法接收 `event_callback` 参数
- [ ] 2.5 在Sink分析开始和完成时推送相应事件
- [ ] 2.6 修改 `agents/unified_source_analysis_agent.py` 的 `analyze` 方法接收 `event_callback` 参数
- [ ] 2.7 在Source分析开始和完成时推送相应事件

## Phase 3: Agent流式输出集成
- [ ] 3.1 修改 `GenerateCodeQL.py` 的 `run_agent_stream` 方法，识别Agent思考输出
- [ ] 3.2 当检测到思考标记时，推送 `AGENT_THINKING` 事件
- [ ] 3.3 当检测到工具调用时，推送 `AGENT_TOOL_CALL` 事件
- [ ] 3.4 将Agent名称信息添加到事件数据中
- [ ] 3.5 确保流式输出的完整性（不丢失内容）

## Phase 4: Pipeline层传递Agent上下文
- [ ] 4.1 修改 `core/pipeline.py` 的 `PipelineStep` 添加 `agent_name` 属性
- [ ] 4.2 在创建各个Step时指定 `agent_name`（CVE、Sink、Source、CodeQL等）
- [ ] 4.3 在Step执行时将 `agent_name` 传递给Agent的 `analyze` 方法
- [ ] 4.4 确保 `event_callback` 能访问到Agent上下文信息

## Phase 5: CodeQL生成Agent集成
- [ ] 5.1 修改 `agents/codeql_gen_agents/codeql_gen_agent.py` 接收 `event_callback` 参数
- [ ] 5.2 在CodeQL生成开始时推送 `AGENT_START` 事件
- [ ] 5.3 在生成过程中推送 `AGENT_THINKING` 事件（如有思考过程）
- [ ] 5.4 在CodeQL生成完成时推送 `AGENT_COMPLETE` 事件
- [ ] 5.5 在CodeQL错误分析Agent中也添加相应事件

## Phase 6: 测试和验证
- [ ] 6.1 单元测试：验证新事件类型的序列化和反序列化
- [ ] 6.2 集成测试：验证完整分析流程的Agent事件推送
- [ ] 6.3 测试SSE客户端能正确接收和解析Agent事件
- [ ] 6.4 验证思考过程输出的完整性
- [ ] 6.5 验证工具调用事件的准确性
- [ ] 6.6 性能测试：确保事件推送不影响Agent执行效率

## Phase 7: 文档和示例
- [ ] 7.1 更新API文档，说明新增的事件类型
- [ ] 7.2 创建Agent事件监听的客户端示例
- [ ] 7.3 添加事件数据结构说明文档
- [ ] 7.4 更新README，说明Agent流式输出功能

