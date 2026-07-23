# API v1 migration

- 基础路径从 `/api` 改为 `/api/v1`；旧路径暂时兼容。
- 创建响应新增 `run_id`、`effective_config`、`event_url`，初始状态为 `queued`。
- `completed` 拆为 `completed_with_findings` 和 `completed_no_findings`；另有
  `partial`、`cancelled`、`timed_out`。
- 结果改为结构化步骤、错误、manifest、Artifact 清单和精选路径。
- 被关闭或不支持的步骤为 `skipped`。
- SSE 新增 `id:` 行和统一事件对象；重连传 `Last-Event-ID`。
- `requirement`、`max_rounds`、所有步骤开关、超时与恢复选项现在都会进入有效
  配置并改变实际执行。
- 多副本部署必须配置 PostgreSQL 与 Redis，并独立启动 Worker。
- `Analyze.py` 仍转发新 CLI，不在 v1 移除。
