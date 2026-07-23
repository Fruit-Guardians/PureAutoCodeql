# SSE v1

连接：

```http
GET /api/v1/analysis/{run_id}/stream
Accept: text/event-stream
Last-Event-ID: 1712345678901-0
```

Redis Streams 生成单调递增 `event_id`。客户端断线后把最后处理成功的 ID 放入
`Last-Event-ID`，服务从下一条事件继续。终态事件同时写入 PostgreSQL，因此
Redis 历史裁剪不影响权威任务结局。

每个 `data` 都满足：

```json
{
  "event_id": "1712345678901-0",
  "run_id": "d4f3...",
  "step": "codeql_generation",
  "type": "step_progress",
  "severity": "info",
  "timestamp": "2026-07-23T12:00:00Z",
  "message": "validating generated query",
  "data": {"round": 2}
}
```

线格式：

```text
id: 1712345678901-0
event: step_progress
data: {...}
```

字段不可省略；`data` 可为空对象。终态类型为 `completed`、`failed`、
`cancelled`、`timed_out`。常见非终态类型包括 `started`、`step_start`、
`step_progress`、`agent_start`、`agent_tool_call`、`agent_complete`。

浏览器原生 `EventSource` 不能设置 Authorization 或 Last-Event-ID 首次值；
需要鉴权或显式游标时使用 `fetch` 流式读取，或由同源后端代理。重连算法应只在
成功处理事件后推进游标，并对相同 `event_id` 幂等。

内存兼容模式同样提供有界历史回放，但不承诺跨 API 进程或进程重启恢复。
