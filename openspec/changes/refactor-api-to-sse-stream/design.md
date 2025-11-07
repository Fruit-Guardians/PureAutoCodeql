# 设计文档：SSE流式输出架构

## Context

当前系统使用多Agent流水线执行分析任务：
- `AnalysisOrchestrator` 协调整体流程
- `AnalysisPipeline` 管理分析步骤（CVE分析、Sink分析、Source分析、CodeQL生成）
- 每个步骤使用 `MultiAgentAnalyzer` 执行agent推理
- `MultiAgentAnalyzer.run_agent_stream` 使用 LangChain 的 `astream_events` 流式处理agent输出

目前agent输出直接通过print语句输出到控制台，无法被API层捕获和推送给客户端。

## Goals / Non-Goals

### Goals
1. 实现SSE流式输出，让客户端实时接收agent执行进度
2. 删除直接操作LangChain工具的API端点，简化架构
3. 在不破坏现有分析流程的前提下，添加事件采集机制
4. 支持多客户端同时订阅同一任务的输出

### Non-Goals
1. 不改变agent的执行逻辑和输出内容
2. 不添加任务持久化存储（仍使用内存队列）
3. 不实现任务历史回放功能（只支持实时订阅）

## Decisions

### 决策1：事件采集点 - MultiAgentAnalyzer.run_agent_stream

**位置**: `GenerateCodeQL.py` 中的 `MultiAgentAnalyzer.run_agent_stream` 方法

**原因**:
- 这是所有agent执行的统一入口
- 已有完整的事件处理逻辑（on_agent_action, on_tool_start, on_tool_end, on_chat_model_stream）
- 可以捕获所有粒度的agent输出

**实现方式**:
```python
async def run_agent_stream(self, prompt: str, output_callback=None, show_thinking: bool = True, event_callback=None):
    # ... 现有代码 ...
    
    async for event in agent.astream_events(...):
        event_name = event.get("event")
        
        # 推送事件到回调
        if event_callback:
            await event_callback({
                "type": "agent_event",
                "event_name": event_name,
                "data": event.get("data", {}),
                "timestamp": datetime.now().isoformat()
            })
        
        # 原有的print逻辑保持不变（用于CLI）
        if show_thinking:
            # ... 现有print代码 ...
```

### 决策2：事件传递路径 - Callback链式传递

**路径**: TaskManager → Pipeline → Steps → Agent → MultiAgentAnalyzer

**实现**:
1. `TaskManager._run_analysis` 创建事件队列和回调函数
2. 通过 `AnalysisContext` 传递回调给 Pipeline
3. Pipeline 在执行每个 Step 时传递回调
4. Step 在创建 Agent 时传递回调
5. Agent 调用 `MultiAgentAnalyzer.run_agent_stream` 时传递回调

```python
# TaskManager
async def _run_analysis(self, task_id: str, case_id: str, config: dict):
    event_queue = asyncio.Queue()
    
    async def event_callback(event):
        await event_queue.put(event)
        # 同时存储到任务的事件历史
        if task_id not in self._task_events:
            self._task_events[task_id] = []
        self._task_events[task_id].append(event)
    
    analysis_config = AnalysisConfig(
        # ... 现有配置 ...
        event_callback=event_callback
    )
    
    self._event_queues[task_id] = event_queue
```

### 决策3：SSE端点实现 - 使用 FastAPI StreamingResponse

**端点**: `GET /api/analysis/{task_id}/stream`

**实现**:
```python
from fastapi.responses import StreamingResponse

@router.get("/{task_id}/stream")
async def stream_task_output(task_id: str):
    task_manager = get_task_manager()
    
    if task_id not in task_manager._event_queues:
        raise HTTPException(status_code=404, detail="任务不存在")
    
    async def event_generator():
        queue = task_manager._event_queues[task_id]
        try:
            while True:
                event = await queue.get()
                
                # SSE格式
                yield f"event: {event['type']}\n"
                yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"
                
                # 如果是完成事件，结束流
                if event.get('type') == 'completed':
                    break
        except asyncio.CancelledError:
            pass
    
    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        }
    )
```

### 决策4：事件类型定义

定义统一的事件格式，支持不同类型的agent输出：

```python
class StreamEventType(str, Enum):
    STEP_START = "step_start"           # 分析步骤开始
    STEP_PROGRESS = "step_progress"     # 步骤进度更新
    AGENT_ACTION = "agent_action"       # Agent决定使用工具
    TOOL_START = "tool_start"           # 工具开始执行
    TOOL_OUTPUT = "tool_output"         # 工具输出
    AGENT_OUTPUT = "agent_output"       # Agent输出（流式）
    STEP_COMPLETE = "step_complete"     # 步骤完成
    ERROR = "error"                     # 错误
    COMPLETED = "completed"             # 任务完成

class StreamEvent(BaseModel):
    type: StreamEventType
    timestamp: datetime
    step_name: Optional[str] = None     # 当前步骤名称
    message: Optional[str] = None        # 消息内容
    data: Optional[Dict[str, Any]] = None  # 附加数据
```

## Risks / Trade-offs

### Risk 1: 内存消耗
- **风险**: 大量事件存储在内存队列中可能导致内存溢出
- **缓解**: 
  1. 限制每个任务的事件历史数量（最多1000条）
  2. 任务完成后清理事件队列
  3. 对长时间运行的任务，只保留最近的事件

### Risk 2: 多客户端订阅的复杂性
- **风险**: 多个客户端同时订阅可能导致事件丢失或重复
- **缓解**:
  1. 使用 `asyncio.Queue` 支持多消费者
  2. 每个客户端维护独立的队列订阅
  3. 实现事件广播机制（一对多）

### Risk 3: 连接断开处理
- **风险**: 客户端断开后，事件仍在推送，浪费资源
- **缓解**:
  1. 使用 `asyncio.CancelledError` 捕获断开
  2. 清理断开的订阅者
  3. 任务继续执行不受影响

### Trade-off: 实时性 vs 完整性
- **Trade-off**: 如果客户端晚加入，将错过早期事件
- **决策**: 
  1. 不实现历史回放（简化实现）
  2. 客户端应在启动任务后立即订阅
  3. 可通过 `/status` 端点查询任务状态

## Migration Plan

### Phase 1: 删除LangChain路由
1. 删除 `api/langchain_routes.py`
2. 移除 `api/server.py` 中的路由注册
3. 删除 `api/models.py` 中的相关模型
4. 验证API启动正常

### Phase 2: 实现事件采集
1. 在 `AnalysisContext` 添加 `event_callback`
2. 修改 `MultiAgentAnalyzer.run_agent_stream` 添加事件推送
3. 在 `Pipeline` 的每个 Step 中传递 callback
4. 验证事件能够正确采集

### Phase 3: 实现SSE端点
1. 在 `TaskManager` 添加事件队列管理
2. 在 `api/models.py` 添加 `StreamEvent` 模型
3. 在 `api/analysis_routes.py` 添加 SSE 端点
4. 实现事件广播给多订阅者
5. 测试SSE连接和事件推送

### Phase 4: 测试和优化
1. 测试完整的分析流程
2. 测试多客户端订阅
3. 测试连接断开和重连
4. 性能测试和内存优化

### Rollback Plan
如果出现问题，可以：
1. 快速恢复 `langchain_routes.py`（从git历史）
2. SSE功能作为可选特性，不影响现有API
3. 保持CLI模式的print输出不变

## Open Questions

1. ~~是否需要实现任务历史事件回放？~~ → 不需要，简化实现
2. ~~事件存储在内存还是数据库？~~ → 内存，配合任务生命周期
3. ~~是否支持客户端过滤事件类型？~~ → 暂不支持，客户端自行过滤
4. 是否需要心跳机制保持连接？→ 待定，可能需要30秒心跳

