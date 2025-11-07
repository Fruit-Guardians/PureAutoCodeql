# Implementation Tasks

## Phase 1: 删除LangChain工具路由
- [x] 1.1 删除 `api/langchain_routes.py` 文件
- [x] 1.2 删除 `api/models.py` 中的 `CodeQLComposeRequest` 和 `CodeQLComposeResponse`
- [x] 1.3 移除 `api/server.py` 中的 `langchain_router` 导入和注册（约170-176行）
- [x] 1.4 检查并移除 `examples/langchain_tools_usage.py` 中的相关示例
- [x] 1.5 验证API服务器能正常启动

## Phase 2: 添加事件模型和队列管理
- [x] 2.1 在 `api/models.py` 添加 `StreamEventType` 枚举
- [x] 2.2 在 `api/models.py` 添加 `StreamEvent` 模型
- [x] 2.3 在 `api/task_manager.py` 添加 `_event_queues` 字典存储队列
- [x] 2.4 在 `api/task_manager.py` 添加 `_task_events` 字典存储事件历史（限制1000条/任务）
- [x] 2.5 在 `TaskManager.__init__` 初始化事件相关字段

## Phase 3: 实现事件采集机制
- [x] 3.1 在 `core/context.py` 的 `AnalysisConfig` 添加 `event_callback` 参数
- [x] 3.2 在 `core/context.py` 的 `AnalysisContext` 添加 `event_callback` 字段
- [x] 3.3 修改 `GenerateCodeQL.py` 的 `MultiAgentAnalyzer.run_agent_stream` 添加 `event_callback` 参数
- [x] 3.4 在 `run_agent_stream` 的事件循环中调用 `event_callback` 推送事件
- [x] 3.5 修改 `core/pipeline.py` 在创建Steps时传递 `event_callback`
- [x] 3.6 修改各个Agent（CVE/Sink/Source）的 `analyze_*` 方法接收并传递 `event_callback`

## Phase 4: TaskManager集成事件采集
- [x] 4.1 修改 `TaskManager._run_analysis` 创建事件队列
- [x] 4.2 实现事件回调函数，将事件推送到队列
- [x] 4.3 在分析开始时推送 `STEP_START` 事件
- [x] 4.4 在分析完成时推送 `COMPLETED` 事件
- [x] 4.5 在分析失败时推送 `ERROR` 事件
- [x] 4.6 在任务取消时清理事件队列

## Phase 5: 实现SSE流式端点
- [x] 5.1 在 `api/analysis_routes.py` 添加 `stream_task_output` 函数
- [x] 5.2 实现事件生成器（event_generator），从队列读取事件
- [x] 5.3 使用 `StreamingResponse` 返回SSE格式的响应
- [x] 5.4 处理任务不存在的404错误
- [x] 5.5 处理任务已结束的410错误
- [x] 5.6 实现连接断开时的清理逻辑（asyncio.CancelledError）
- [x] 5.7 添加适当的CORS和缓存控制头

## Phase 6: 支持多客户端订阅
- [ ] 6.1 在 `TaskManager` 添加订阅者管理（每个任务可有多个订阅者）
- [ ] 6.2 实现事件广播机制（复制事件到所有订阅者队列）
- [ ] 6.3 实现订阅者注册和注销
- [ ] 6.4 在客户端断开时自动注销订阅者

## Phase 7: 测试和验证
- [ ] 7.1 单元测试：测试事件队列的创建和管理
- [ ] 7.2 集成测试：测试完整的分析流程和事件推送
- [ ] 7.3 测试SSE连接建立和事件接收
- [ ] 7.4 测试多客户端同时订阅同一任务
- [ ] 7.5 测试客户端断开后重连
- [ ] 7.6 测试任务完成后新客户端订阅（返回410）
- [ ] 7.7 测试错误场景（任务不存在、任务失败）
- [ ] 7.8 性能测试：长时间运行任务的内存占用

## Phase 8: 文档和示例
- [ ] 8.1 更新API文档（/docs），添加SSE端点说明
- [ ] 8.2 创建客户端使用示例（Python、JavaScript）
- [ ] 8.3 更新README，说明breaking changes
- [ ] 8.4 创建迁移指南，说明如何从LangChain端点迁移到SSE
- [ ] 8.5 添加事件类型说明文档

