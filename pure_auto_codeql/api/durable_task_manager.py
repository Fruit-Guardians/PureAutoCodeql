"""API-side task service for PostgreSQL + Redis deployments."""

from __future__ import annotations

import os
import uuid
from dataclasses import asdict
from datetime import UTC, datetime
from typing import AsyncIterator

from redis.asyncio import Redis

from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.platform.db import create_engine_and_sessions
from pure_auto_codeql.platform.events import RunEvent
from pure_auto_codeql.platform.queue import RedisStreamBroker, StreamBroker
from pure_auto_codeql.platform.repository import RunRepository, SqlRunRepository

from .models import AnalysisResult, AnalysisTaskInfo, TaskStatus


class DurableTaskManager:
    def __init__(self, repository: RunRepository, broker: StreamBroker):
        self.repository = repository
        self.broker = broker
        self._initialized = False

    async def initialize(self) -> None:
        if not self._initialized:
            await self.broker.initialize()
            self._initialized = True

    async def health(self) -> dict[str, bool]:
        repository_ok = await self.repository.ping()
        migrations_ok = await self.repository.schema_ready()
        redis_client = getattr(self.broker, "redis", None)
        redis_ok = bool(await redis_client.ping()) if redis_client else True
        workers_ok = await self.broker.has_live_workers()
        return {
            "database": repository_ok,
            "migrations": migrations_ok,
            "redis": redis_ok,
            "queue": self._initialized,
            "worker": workers_ok,
        }

    @staticmethod
    def _safe_config(config: AnalysisConfig) -> dict:
        payload = asdict(config)
        # Request payloads never persist credentials. Workers resolve their own
        # secrets from config/keys.toml or environment at execution time.
        payload["api_key"] = None
        payload["event_callback"] = None
        return payload

    async def create_task(self, case_id: str, config: AnalysisConfig) -> AnalysisTaskInfo:
        await self.initialize()
        run_id = str(uuid.uuid4())
        effective = self._safe_config(config)
        record = await self.repository.create_run(run_id, case_id, effective)
        await self.broker.enqueue(run_id, {"case_id": case_id})
        return self.to_task_info(record)

    @staticmethod
    def to_task_info(record: dict) -> AnalysisTaskInfo:
        error = record.get("error")
        return AnalysisTaskInfo(
            task_id=record["id"],
            run_id=record["id"],
            case_id=record["case_id"],
            status=TaskStatus(record["status"]),
            created_at=record.get("created_at") or datetime.now(UTC),
            started_at=record.get("started_at"),
            completed_at=record.get("completed_at"),
            progress=record["status"],
            error=error.get("message") if isinstance(error, dict) else error,
            effective_config=record.get("config"),
            event_url=f"/api/v1/analysis/{record['id']}/stream",
        )

    async def get_task_status(self, run_id: str) -> AnalysisTaskInfo | None:
        record = await self.repository.get_run(run_id)
        return self.to_task_info(record) if record else None

    async def get_task_result(self, run_id: str) -> AnalysisResult | None:
        record = await self.repository.get_run(run_id)
        if not record or not record.get("result"):
            return None
        result = record["result"]
        return AnalysisResult(
            task_id=run_id,
            case_id=record["case_id"],
            status=TaskStatus(record["status"]),
            cve_analysis=None,
            sink_analysis=None,
            source_analysis=None,
            codeql_query=None,
            query_results=None,
            outcome=record.get("outcome"),
            output_dir=result.get("output_directory"),
            steps=result.get("steps", {}),
            manifest=result.get("manifest"),
            artifacts=result.get("artifacts", []),
        )

    async def cancel_task(self, run_id: str) -> bool:
        return await self.repository.request_cancellation(run_id, requested_by="api")

    async def list_tasks(
        self,
        status: TaskStatus | None,
        limit: int,
        offset: int,
    ) -> tuple[list[AnalysisTaskInfo], int]:
        records, total = await self.repository.list_runs(
            status.value if status else None,
            limit,
            offset,
        )
        return [self.to_task_info(record) for record in records], total

    async def events(self, run_id: str, after_id: str) -> AsyncIterator[RunEvent]:
        terminal = await self.repository.get_terminal_event(run_id)
        if terminal is not None:
            if terminal.event_id != after_id:
                yield terminal
            return
        async for event in self.broker.events(run_id, after_id):
            yield event
            if event.type in {"completed", "failed", "cancelled", "timed_out"}:
                return


_durable_manager: DurableTaskManager | None = None
_durable_engine = None
_durable_redis: Redis | None = None


def get_durable_task_manager() -> DurableTaskManager | None:
    global _durable_engine, _durable_manager, _durable_redis
    database_url = os.getenv("DATABASE_URL")
    redis_url = os.getenv("REDIS_URL")
    if not database_url or not redis_url:
        return None
    if _durable_manager is None:
        _durable_engine, sessions = create_engine_and_sessions(database_url)
        _durable_redis = Redis.from_url(redis_url)
        _durable_manager = DurableTaskManager(
            SqlRunRepository(sessions),
            RedisStreamBroker(_durable_redis),
        )
    return _durable_manager


async def close_durable_task_manager() -> None:
    global _durable_engine, _durable_manager, _durable_redis
    if _durable_redis is not None:
        await _durable_redis.aclose()
    if _durable_engine is not None:
        await _durable_engine.dispose()
    _durable_manager = None
    _durable_engine = None
    _durable_redis = None
