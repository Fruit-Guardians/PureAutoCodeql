"""Independent durable analysis worker."""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import socket
from contextlib import suppress
from dataclasses import fields
from pathlib import Path
from typing import Any

from redis.asyncio import Redis

from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator
from pure_auto_codeql.observability import configure_telemetry, run_recoveries
from pure_auto_codeql.platform.db import create_engine_and_sessions
from pure_auto_codeql.platform.events import RunEvent
from pure_auto_codeql.platform.queue import RedisStreamBroker, StreamBroker, StreamMessage
from pure_auto_codeql.platform.repository import (
    TERMINAL_STATUSES,
    RunRepository,
    SqlRunRepository,
)
from pure_auto_codeql.services.process_control import ProcessScope, bind_process_scope


class AnalysisWorker:
    def __init__(
        self,
        repository: RunRepository,
        broker: StreamBroker,
        *,
        worker_id: str,
        lease_seconds: int = 60,
        heartbeat_seconds: int = 15,
    ):
        self.repository = repository
        self.broker = broker
        self.worker_id = worker_id
        self.lease_seconds = lease_seconds
        self.heartbeat_seconds = heartbeat_seconds
        self.stopping = asyncio.Event()

    async def _heartbeat(self, run_id: str) -> None:
        while not self.stopping.is_set():
            await asyncio.sleep(self.heartbeat_seconds)
            if not await self.repository.heartbeat(run_id, self.worker_id, self.lease_seconds):
                return

    async def _service_heartbeat(self) -> None:
        while not self.stopping.is_set():
            await self.broker.heartbeat_worker(
                self.worker_id,
                max(self.heartbeat_seconds * 3, 30),
            )
            await asyncio.sleep(self.heartbeat_seconds)

    async def _event(self, run_id: str, event: dict[str, Any]) -> None:
        normalized = RunEvent.from_legacy(run_id, event)
        stored = await self.broker.publish_event(normalized)
        terminal = normalized.type in {"completed", "failed", "cancelled", "timed_out"}
        await self.repository.save_event_checkpoint(stored, terminal=terminal)

    async def _watch_cancellation(self, run_id: str, analysis_task: asyncio.Task) -> None:
        while not analysis_task.done():
            if await self.repository.cancellation_requested(run_id):
                analysis_task.cancel()
                return
            await asyncio.sleep(1)

    async def process(self, message: StreamMessage) -> None:
        run_id = message.run_id
        if not await self.repository.claim_run(run_id, self.worker_id, self.lease_seconds):
            existing = await self.repository.get_run(run_id)
            if not existing or existing["status"] in TERMINAL_STATUSES:
                await self.broker.acknowledge(message.message_id)
            return
        run = await self.repository.get_run(run_id)
        if not run:
            await self.broker.acknowledge(message.message_id)
            return
        if run.get("attempt_count", 0) > 1:
            run_recoveries.add(1)

        allowed = {item.name for item in fields(AnalysisConfig)}
        config = AnalysisConfig(**{key: value for key, value in run["config"].items() if key in allowed})
        config.validate()
        heartbeat = asyncio.create_task(self._heartbeat(run_id))
        process_scope = ProcessScope()
        try:
            await self._event(
                run_id,
                {"type": "started", "message": "worker claimed run", "data": {"worker_id": self.worker_id}},
            )
            with bind_process_scope(process_scope):
                if await self.repository.cancellation_requested(run_id):
                    raise asyncio.CancelledError
                effective = AnalysisConfig(
                    **{
                        **{item.name: getattr(config, item.name) for item in fields(config)},
                        "event_callback": lambda event: self._event(run_id, event),
                    }
                )
                analysis_task = asyncio.create_task(
                    AnalysisOrchestrator(effective).analyze_case(
                        run["case_id"],
                        language=config.language,
                    )
                )
                cancellation = asyncio.create_task(self._watch_cancellation(run_id, analysis_task))
                try:
                    result = await asyncio.wait_for(analysis_task, timeout=config.task_timeout)
                finally:
                    cancellation.cancel()
                    with suppress(asyncio.CancelledError):
                        await cancellation
            status = result.outcome.value
            api_result = {
                "outcome": status,
                "output_directory": result.output_directory,
                "steps": {key: value.to_dict() for key, value in result.step_results.items()},
                "artifacts": [artifact.to_dict() for artifact in result.artifacts],
            }
            manifest_artifact = next(
                (artifact for artifact in result.artifacts if artifact.name == "manifest.json"),
                None,
            )
            if manifest_artifact:
                try:
                    api_result["manifest"] = json.loads(
                        Path(manifest_artifact.path).read_text(encoding="utf-8")
                    )
                except (OSError, json.JSONDecodeError):
                    api_result["manifest"] = None
            for step_name, step_result in result.step_results.items():
                await self.repository.add_step_attempt(
                    run_id,
                    step_name,
                    1,
                    step_result.status.value,
                    {
                        "error": (
                            step_result.error_detail.to_dict()
                            if step_result.error_detail
                            else None
                        ),
                        "warnings": step_result.warnings,
                        "metrics": step_result.metrics,
                    },
                )
            await self.repository.add_artifacts(run_id, api_result["artifacts"])
            await self.repository.finish_run(run_id, status, result=api_result)
            await self._event(run_id, {"type": "completed", "message": "analysis completed", "data": api_result})
            await self.broker.acknowledge(message.message_id)
        except asyncio.TimeoutError:
            process_scope.terminate_all()
            await self.repository.finish_run(
                run_id,
                "timed_out",
                error={"code": "task_timeout", "message": f"exceeded {config.task_timeout} seconds"},
            )
            await self._event(run_id, {"type": "timed_out", "severity": "error", "message": "analysis timed out"})
            await self.broker.acknowledge(message.message_id)
        except asyncio.CancelledError:
            process_scope.terminate_all()
            await self.repository.finish_run(run_id, "cancelled")
            await self._event(run_id, {"type": "cancelled", "severity": "warning", "message": "analysis cancelled"})
            await self.broker.acknowledge(message.message_id)
        except Exception as exc:
            process_scope.terminate_all()
            await self.repository.finish_run(
                run_id,
                "failed",
                error={"code": "worker_exception", "message": str(exc)},
            )
            await self._event(run_id, {"type": "failed", "severity": "error", "message": str(exc)})
            await self.broker.acknowledge(message.message_id)
        finally:
            process_scope.terminate_all()
            heartbeat.cancel()
            with suppress(asyncio.CancelledError):
                await heartbeat

    async def run_forever(self) -> None:
        await self.broker.initialize()
        service_heartbeat = asyncio.create_task(self._service_heartbeat())
        try:
            while not self.stopping.is_set():
                reclaimed = await self.broker.reclaim(
                    self.worker_id,
                    min_idle_ms=self.lease_seconds * 1000,
                )
                messages = reclaimed or await self.broker.consume(self.worker_id)
                for message in messages:
                    await self.process(message)
        finally:
            service_heartbeat.cancel()
            with suppress(asyncio.CancelledError):
                await service_heartbeat


async def _main() -> None:
    parser = argparse.ArgumentParser(description="PureAutoCodeQL durable worker")
    parser.add_argument("--worker-id", default=f"{socket.gethostname()}-{os.getpid()}")
    parser.add_argument("--healthcheck", action="store_true")
    args = parser.parse_args()
    configure_telemetry("pure-auto-codeql-worker")
    database_url = os.environ["DATABASE_URL"]
    redis_url = os.environ["REDIS_URL"]
    engine, sessions = create_engine_and_sessions(database_url)
    redis = Redis.from_url(redis_url)
    repository = SqlRunRepository(sessions)
    broker = RedisStreamBroker(redis)
    if args.healthcheck:
        healthy = (
            await repository.ping()
            and await repository.schema_ready()
            and bool(await redis.ping())
            and await broker.has_live_workers()
        )
        await redis.aclose()
        await engine.dispose()
        if not healthy:
            raise SystemExit(1)
        return
    worker = AnalysisWorker(
        repository,
        broker,
        worker_id=args.worker_id,
    )
    try:
        await worker.run_forever()
    finally:
        await redis.aclose()
        await engine.dispose()


def main() -> None:
    asyncio.run(_main())


if __name__ == "__main__":
    main()
