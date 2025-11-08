# 增强SSE流式传输：Agent信息与流式输出

## Why

当前的SSE流式传输机制仅提供基础的任务级别事件（步骤开始、完成、错误），但缺少Agent执行层面的细节信息。用户需要了解：
1. 当前正在执行哪个Agent（CVE分析、Sink分析、Source分析、CodeQL生成等）
2. Agent的实时输出流（思考过程、工具调用、中间结果）
3. Agent的执行状态和进度

增强SSE事件传输以包含Agent级别的详细信息，可以提供更好的可观测性和用户体验。

**重要**: 此增强功能仅影响API模式下的SSE流式输出，不干扰CLI模式下的正常运行。所有event_callback参数均为可选，CLI模式下保持原有行为。

## What Changes

- 在 `StreamEvent` 模型中添加 `agent_name` 和 `agent_type` 字段，标识当前执行的Agent
- 新增 `StreamEventType` 枚举值：
  - `AGENT_START` - Agent开始执行
  - `AGENT_THINKING` - Agent思考过程输出
  - `AGENT_TOOL_CALL` - Agent调用工具
  - `AGENT_COMPLETE` - Agent执行完成
- 修改 `agents/` 目录下的Agent实现，在关键节点调用 `event_callback`
- 修改 `GenerateCodeQL.py` 中的 `run_agent_stream` 方法，增加Agent流式输出的事件推送
- 在 `core/pipeline.py` 中传递Agent信息到事件回调

## Impact

- **Affected specs**: 新增 `api-sse-streaming` capability
- **Affected code**:
  - `api/models.py` - 扩展 `StreamEvent` 和 `StreamEventType`
  - `agents/cve_analysis_agent.py` - 添加可选的事件回调
  - `agents/unified_sink_path_agent.py` - 添加可选的事件回调
  - `agents/unified_source_analysis_agent.py` - 添加可选的事件回调
  - `agents/codeql_gen_agents/codeql_gen_agent.py` - 添加可选的事件回调
  - `GenerateCodeQL.py` - 增强流式输出的事件推送（保持CLI兼容）
  - `core/pipeline.py` - 传递Agent上下文
- **Breaking Changes**: 无，所有变更向后兼容
- **Migration**: 
  - 现有API客户端无需修改，新事件类型为可选处理
  - CLI模式完全不受影响，继续使用原有的show_thinking机制
- **CLI兼容性保证**:
  - 所有 `event_callback` 参数为可选（默认None）
  - 当 `event_callback=None` 时，跳过所有事件推送逻辑
  - CLI模式的 `show_thinking` 和终端输出功能保持不变

