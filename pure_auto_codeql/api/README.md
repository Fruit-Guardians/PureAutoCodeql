# PureAutoCodeQL API v1

API 与 Worker 在持久化部署中完全分离。API 只校验输入、写入 PostgreSQL、向
Redis Streams 投递任务、查询/取消任务并转发事件；`python -m
pure_auto_codeql.worker` 执行分析。未配置 `DATABASE_URL` 和 `REDIS_URL` 时，
API 保留单进程内存模式，供开发和兼容测试使用。

## 启动

推荐使用完整环境：

```bash
docker compose up --build
```

本地兼容模式：

```bash
uv run uvicorn pure_auto_codeql.api.server:app --host 127.0.0.1 --port 8000
```

非回环地址必须配置 `API_AUTH_TOKEN`，否则服务拒绝启动。Token 可用逗号轮换，
也可保存为 `sha256:<hex>`；请求使用 `Authorization: Bearer <原始Token>`。

## v1 端点

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/analysis/start` | 创建并投递任务 |
| GET | `/api/v1/analysis/{run_id}/status` | 查询权威状态 |
| GET | `/api/v1/analysis/{run_id}/result` | 获取步骤、错误、manifest、产物和精选路径 |
| GET | `/api/v1/analysis/{run_id}/stream` | 可恢复 SSE |
| DELETE | `/api/v1/analysis/{run_id}` | 创建取消请求 |
| GET | `/api/v1/analysis/tasks` | 分页列出任务 |
| GET | `/health` | API、数据库、Redis 和队列健康状态 |

创建任务返回 `task_id`、同值的 `run_id`、`status=queued`、去敏后的
`effective_config` 和 `event_url`。状态集合为：

`queued / running / completed_with_findings / completed_no_findings / partial /
failed / cancelled / timed_out`。

`/api/*` 暂时作为兼容别名保留，新客户端必须使用 `/api/v1/*`。

请求参数和 CLI 共用 `AnalysisConfig`，包括 requirement、各步骤开关、
`max_rounds`、超时、断流恢复与 Source/Sink 回退。依赖不满足时返回 422；
被关闭的步骤在结果中是 `skipped`，不会伪装成空成功。

Swagger: `/docs`，ReDoc: `/redoc`，OpenAPI: `/openapi.json`。

事件恢复详见 [SSE_REFERENCE.md](./SSE_REFERENCE.md)，部署与迁移详见
[部署指南](../../docs/deployment.md) 和 [v1 迁移说明](../../docs/api_v1_migration.md)。
