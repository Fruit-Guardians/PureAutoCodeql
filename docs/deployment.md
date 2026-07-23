# Deployment

## Docker Compose

```bash
export API_AUTH_TOKEN='replace-with-a-long-random-token'
docker compose up --build
curl http://127.0.0.1:8000/health
```

服务包括 API、Worker、PostgreSQL、Redis，以及 `--profile s3` 可启用的 MinIO。
`migrate` 在 API/Worker 前执行 Alembic。PostgreSQL 是权威状态源；Redis 只承载
至少一次任务投递和有保留上限的事件历史。
`/health` 同时验证 PostgreSQL、Alembic 版本、Redis、consumer group 和活跃
Worker 心跳；Worker 自身健康检查使用相同的迁移与队列消费者条件。

API/Worker 镜像以 UID 10001、只读根文件系统、`no-new-privileges`、全部
capability 删除、PID/CPU/内存限制运行。CodeQL C/C++ Docker 构建默认禁网、
非 root、只读根文件系统、只读源码挂载，并限制 PID、内存、CPU 和临时磁盘。
情报获取如需网络，应作为独立受控阶段部署。

常用变量：

| 变量 | 说明 |
| --- | --- |
| `DATABASE_URL` | `postgresql+asyncpg://...` |
| `REDIS_URL` | Redis Streams 地址 |
| `API_AUTH_TOKEN` | 逗号分隔轮换 Token，或 `sha256:<hex>` |
| `API_RATE_LIMIT_PER_MINUTE` | 单客户端每分钟请求上限 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP HTTP Collector |

## Kubernetes 迁移

API 与 Worker 使用相同镜像、不同 command；API Deployment 可横向扩展，Worker
按队列压力扩展。把 PostgreSQL、Redis 和 S3 替换为托管服务，使用同一环境变量
契约。运行 `alembic upgrade head` 的 Job 必须先于新版本 rollout。Worker 的
termination grace period 应大于进程组清理时间；任务租约到期后会由其他 Worker
通过 `XAUTOCLAIM` 恢复。

ArtifactStore 的数据库记录只包含哈希、大小、媒体类型和定位信息。生产使用
S3-compatible store，本地开发使用运行目录文件系统。

模板优化默认关闭；启用后只在 `temp/template_refinement_candidates/` 生成候选
unified diff，不会修改生产提示模板。
