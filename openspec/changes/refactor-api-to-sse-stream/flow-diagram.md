# Agent执行流程和事件采集点

## 当前执行流程

```
API Request (POST /api/analysis/start)
    ↓
TaskManager.create_task()
    ↓
TaskManager.start_task()  (后台执行)
    ↓
TaskManager._run_analysis()
    ↓
AnalysisOrchestrator.analyze_case()
    ↓
AnalysisPipeline.execute(context)
    ↓
┌─────────────────────────────────────────────────┐
│ Pipeline Steps (串行执行):                       │
├─────────────────────────────────────────────────┤
│ 1. CVEAnalysisStep                              │
│    ├─ CVEAnalysisAgent.analyze_cve()            │
│    ├─ MultiAgentAnalyzer.run_agent()            │
│    └─ agent.astream_events() [事件采集点]       │
├─────────────────────────────────────────────────┤
│ 2. SinkAnalysisStep                             │
│    ├─ UnifiedSinkPathAgent.analyze_paths()      │
│    ├─ MultiAgentAnalyzer.run_agent()            │
│    └─ agent.astream_events() [事件采集点]       │
├─────────────────────────────────────────────────┤
│ 3. SourceAnalysisStep                           │
│    ├─ UnifiedSourceAnalysisAgent.analyze_sources()│
│    ├─ MultiAgentAnalyzer.run_agent()            │
│    └─ agent.astream_events() [事件采集点]       │
├─────────────────────────────────────────────────┤
│ 4. CodeQLGenerationStep                         │
│    ├─ CodeQLComposeTool._arun()                 │
│    ├─ MultiAgentAnalyzer.run_agent_stream()     │
│    └─ agent.astream_events() [事件采集点]       │
└─────────────────────────────────────────────────┘
    ↓
AnalysisResult返回
```

## 关键代码位置

### 1. 事件源头：`services/llm_service.py:110-188`

```python
async def run_agent(self, prompt: str, show_thinking: bool = True) -> AgentResult:
    async for event in agent.astream_events(...):
        event_name = event.get("event")
        
        # 当前：直接print输出
        if show_thinking:
            if event_name == "on_agent_action":
                print(f"🤔 AI思考: 决定使用工具 '{action.tool}'")
            elif event_name == "on_tool_start":
                print(f"🔧 工具执行: {tool_name}")
            elif event_name == "on_tool_end":
                print(f"✅ 工具完成: {tool_name}")
            
        # 改造：添加事件推送
        # if event_callback:
        #     await event_callback(StreamEvent(...))
```

### 2. Agent层：`agents/*.py`

```python
# agents/cve_analysis_agent.py:76-97
async def analyze_cve(self, json_path: Path, *, intel_prompt=None, show_thinking=True):
    prompt = self.build_prompt(...)
    return await self.analyzer.run_agent(prompt, show_thinking=show_thinking)
    # 需要添加: event_callback参数传递
```

### 3. Pipeline层：`core/pipeline.py:37-92`

```python
# CVEAnalysisStep.execute()
async def execute(self, context: AnalysisContext) -> Any:
    analyzer = MultiAgentAnalyzer()
    cve_agent = CVEAnalysisAgent(analyzer)
    result = await cve_agent.analyze_cve(
        ...,
        show_thinking=context.show_thinking
        # 需要添加: event_callback=context.event_callback
    )
```

### 4. Orchestrator层：`core/orchestrator.py:25-90`

```python
async def analyze_case(self, case_id: str) -> AnalysisResult:
    context = AnalysisContext(
        ...,
        show_thinking=self.config.show_thinking
        # 需要添加: event_callback=self.config.event_callback
    )
    result = await pipeline.execute(context)
```

### 5. TaskManager层：`api/task_manager.py:55-96`

```python
async def _run_analysis(self, task_id: str, case_id: str, config: dict):
    # 创建事件队列
    event_queue = asyncio.Queue()
    self._event_queues[task_id] = event_queue
    
    # 创建事件回调
    async def event_callback(event: StreamEvent):
        await event_queue.put(event)
        # 记录到任务历史
        self._task_events[task_id].append(event)
    
    # 配置传递callback
    analysis_config = AnalysisConfig(
        ...,
        event_callback=event_callback
    )
    
    orchestrator = AnalysisOrchestrator(analysis_config)
    result = await orchestrator.analyze_case(case_id)
```

## SSE端点：事件消费端

```python
# api/analysis_routes.py
@router.get("/{task_id}/stream")
async def stream_task_output(task_id: str):
    task_manager = get_task_manager()
    queue = task_manager._event_queues.get(task_id)
    
    if not queue:
        raise HTTPException(404, "任务不存在")
    
    async def event_generator():
        try:
            while True:
                event = await queue.get()
                yield f"event: {event.type}\n"
                yield f"data: {event.model_dump_json()}\n\n"
                
                if event.type == StreamEventType.COMPLETED:
                    break
        except asyncio.CancelledError:
            pass
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
    )
```

## 事件类型映射

| LangChain事件 | SSE事件类型 | 说明 |
|--------------|-------------|-----|
| on_agent_action | AGENT_ACTION | Agent决定使用工具 |
| on_tool_start | TOOL_START | 工具开始执行 |
| on_tool_end | TOOL_OUTPUT | 工具执行完成，包含输出 |
| on_chat_model_stream | AGENT_OUTPUT | Agent流式输出内容 |
| Pipeline step start | STEP_START | 分析步骤开始 |
| Pipeline step complete | STEP_COMPLETE | 分析步骤完成 |
| Task complete | COMPLETED | 任务完成 |
| Exception | ERROR | 错误发生 |

## 事件流示例

```
客户端连接 → GET /api/analysis/task-123/stream

服务器推送：
event: step_start
data: {"type":"step_start","step_name":"cve_analysis","timestamp":"..."}

event: agent_action
data: {"type":"agent_action","message":"决定使用工具 'read_text_file'","timestamp":"..."}

event: tool_start
data: {"type":"tool_start","message":"工具执行: read_text_file","timestamp":"..."}

event: tool_output
data: {"type":"tool_output","message":"工具完成: read_text_file","data":{"output":"..."},"timestamp":"..."}

event: agent_output
data: {"type":"agent_output","message":"分析CVE信息...","timestamp":"..."}

event: step_complete
data: {"type":"step_complete","step_name":"cve_analysis","timestamp":"..."}

...

event: completed
data: {"type":"completed","message":"分析任务完成","timestamp":"..."}

[连接关闭]
```

## 数据流向

```
Agent执行 → LangChain Events → event_callback → Queue → SSE Generator → HTTP Response → Client
                                      ↓
                                Task Event History (内存，限制1000条)
```

## 实现要点

1. **非侵入性**: 在现有print语句旁边添加callback调用，不删除print（CLI仍需要）
2. **向后兼容**: `event_callback`为可选参数，默认None，不影响CLI使用
3. **异步安全**: 使用`asyncio.Queue`保证线程安全
4. **资源清理**: 任务完成后清理事件队列，防止内存泄漏
5. **错误隔离**: callback失败不影响任务执行

## 修改清单

| 文件 | 修改内容 |
|------|----------|
| `core/context.py` | AnalysisConfig添加event_callback字段 |
| `core/context.py` | AnalysisContext添加event_callback传递 |
| `services/llm_service.py` | run_agent添加event_callback参数和调用 |
| `GenerateCodeQL.py` | run_agent_stream添加event_callback参数和调用 |
| `agents/cve_analysis_agent.py` | analyze_cve传递event_callback |
| `agents/unified_sink_path_agent.py` | analyze_paths传递event_callback |
| `agents/unified_source_analysis_agent.py` | analyze_sources传递event_callback |
| `core/pipeline.py` | Steps传递context.event_callback |
| `core/orchestrator.py` | 传递config.event_callback到context |
| `api/task_manager.py` | 创建事件队列和callback |
| `api/models.py` | 添加StreamEvent和StreamEventType |
| `api/analysis_routes.py` | 添加SSE端点 |

