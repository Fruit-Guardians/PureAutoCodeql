"""Authoritative run-state repositories."""

from __future__ import annotations

import asyncio
from copy import deepcopy
from datetime import UTC, datetime, timedelta
from typing import Any, Protocol

from sqlalchemy import select, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import async_sessionmaker

from .db import (
    ArtifactRecord,
    CancellationRequestRecord,
    EventCheckpointRecord,
    RunRecord,
    StepAttemptRecord,
)
from .events import RunEvent

TERMINAL_STATUSES = {
    "completed_with_findings",
    "completed_no_findings",
    "partial",
    "failed",
    "cancelled",
    "timed_out",
}


class RunRepository(Protocol):
    async def ping(self) -> bool: ...
    async def schema_ready(self) -> bool: ...
    async def create_run(self, run_id: str, case_id: str, config: dict[str, Any]) -> dict[str, Any]: ...
    async def get_run(self, run_id: str) -> dict[str, Any] | None: ...
    async def list_runs(self, status: str | None, limit: int, offset: int) -> tuple[list[dict[str, Any]], int]: ...
    async def claim_run(self, run_id: str, worker_id: str, lease_seconds: int) -> bool: ...
    async def heartbeat(self, run_id: str, worker_id: str, lease_seconds: int) -> bool: ...
    async def finish_run(
        self,
        run_id: str,
        status: str,
        *,
        result: dict[str, Any] | None = None,
        error: dict[str, Any] | None = None,
    ) -> bool: ...
    async def request_cancellation(self, run_id: str, requested_by: str | None = None) -> bool: ...
    async def cancellation_requested(self, run_id: str) -> bool: ...
    async def save_event_checkpoint(self, event: RunEvent, *, terminal: bool) -> None: ...
    async def get_terminal_event(self, run_id: str) -> RunEvent | None: ...
    async def add_step_attempt(
        self,
        run_id: str,
        step: str,
        attempt: int,
        status: str,
        diagnostics: dict[str, Any] | None = None,
    ) -> None: ...
    async def add_artifacts(self, run_id: str, artifacts: list[dict[str, Any]]) -> None: ...


class InMemoryRunRepository:
    """Deterministic repository used by tests and explicit local-only mode."""

    def __init__(self):
        self.runs: dict[str, dict[str, Any]] = {}
        self.cancellations: set[str] = set()
        self.events: dict[str, dict[str, Any]] = {}
        self._lock = asyncio.Lock()

    async def ping(self) -> bool:
        return True

    async def schema_ready(self) -> bool:
        return True

    async def create_run(self, run_id: str, case_id: str, config: dict[str, Any]) -> dict[str, Any]:
        async with self._lock:
            if run_id in self.runs:
                return deepcopy(self.runs[run_id])
            now = datetime.now(UTC)
            self.runs[run_id] = {
                "id": run_id,
                "case_id": case_id,
                "status": "queued",
                "config": deepcopy(config),
                "attempt_count": 0,
                "created_at": now,
                "updated_at": now,
            }
            return deepcopy(self.runs[run_id])

    async def get_run(self, run_id: str) -> dict[str, Any] | None:
        value = self.runs.get(run_id)
        return deepcopy(value) if value else None

    async def list_runs(self, status: str | None, limit: int, offset: int) -> tuple[list[dict[str, Any]], int]:
        values = list(self.runs.values())
        if status:
            values = [run for run in values if run["status"] == status]
        values.sort(key=lambda run: run["created_at"], reverse=True)
        return deepcopy(values[offset : offset + limit]), len(values)

    async def claim_run(self, run_id: str, worker_id: str, lease_seconds: int) -> bool:
        async with self._lock:
            run = self.runs.get(run_id)
            now = datetime.now(UTC)
            if not run or run["status"] in TERMINAL_STATUSES:
                return False
            lease = run.get("lease_expires_at")
            if run.get("lease_owner") not in {None, worker_id} and lease and lease > now:
                return False
            run.update(
                status="running",
                lease_owner=worker_id,
                lease_expires_at=now + timedelta(seconds=lease_seconds),
                heartbeat_at=now,
                started_at=run.get("started_at") or now,
                attempt_count=run["attempt_count"] + 1,
                updated_at=now,
            )
            return True

    async def heartbeat(self, run_id: str, worker_id: str, lease_seconds: int) -> bool:
        async with self._lock:
            run = self.runs.get(run_id)
            if not run or run.get("lease_owner") != worker_id or run["status"] != "running":
                return False
            now = datetime.now(UTC)
            run["heartbeat_at"] = now
            run["lease_expires_at"] = now + timedelta(seconds=lease_seconds)
            return True

    async def finish_run(
        self,
        run_id: str,
        status: str,
        *,
        result: dict[str, Any] | None = None,
        error: dict[str, Any] | None = None,
    ) -> bool:
        if status not in TERMINAL_STATUSES:
            raise ValueError("finish_run requires a terminal status")
        async with self._lock:
            run = self.runs.get(run_id)
            if not run:
                return False
            if run["status"] in TERMINAL_STATUSES:
                return run["status"] == status
            now = datetime.now(UTC)
            run.update(
                status=status,
                outcome=status,
                result=deepcopy(result),
                error=deepcopy(error),
                completed_at=now,
                updated_at=now,
                lease_owner=None,
                lease_expires_at=None,
            )
            return True

    async def request_cancellation(self, run_id: str, requested_by: str | None = None) -> bool:
        del requested_by
        async with self._lock:
            run = self.runs.get(run_id)
            if not run or run["status"] in TERMINAL_STATUSES:
                return False
            self.cancellations.add(run_id)
            return True

    async def cancellation_requested(self, run_id: str) -> bool:
        return run_id in self.cancellations

    async def save_event_checkpoint(self, event: RunEvent, *, terminal: bool) -> None:
        if terminal:
            self.events[event.run_id] = event.model_dump(mode="json")

    async def get_terminal_event(self, run_id: str) -> RunEvent | None:
        payload = self.events.get(run_id)
        return RunEvent.model_validate(payload) if payload else None

    async def add_step_attempt(
        self,
        run_id: str,
        step: str,
        attempt: int,
        status: str,
        diagnostics: dict[str, Any] | None = None,
    ) -> None:
        run = self.runs.get(run_id)
        if run is not None:
            run.setdefault("step_attempts", []).append(
                {
                    "step": step,
                    "attempt": attempt,
                    "status": status,
                    "diagnostics": deepcopy(diagnostics),
                }
            )

    async def add_artifacts(self, run_id: str, artifacts: list[dict[str, Any]]) -> None:
        run = self.runs.get(run_id)
        if run is not None:
            run["artifact_records"] = deepcopy(artifacts)


class SqlRunRepository:
    def __init__(self, sessions: async_sessionmaker):
        self.sessions = sessions

    async def ping(self) -> bool:
        try:
            async with self.sessions() as session:
                return (await session.execute(text("SELECT 1"))).scalar_one() == 1
        except SQLAlchemyError:
            return False

    async def schema_ready(self) -> bool:
        try:
            async with self.sessions() as session:
                version = (
                    await session.execute(
                        text("SELECT version_num FROM alembic_version LIMIT 1")
                    )
                ).scalar_one_or_none()
                return version == "0001_durable_runs"
        except SQLAlchemyError:
            return False

    @staticmethod
    def _run_dict(record: RunRecord) -> dict[str, Any]:
        return {
            column.name: getattr(record, column.name)
            for column in RunRecord.__table__.columns
        }

    async def create_run(self, run_id: str, case_id: str, config: dict[str, Any]) -> dict[str, Any]:
        async with self.sessions.begin() as session:
            existing = await session.get(RunRecord, run_id)
            if existing:
                return self._run_dict(existing)
            record = RunRecord(id=run_id, case_id=case_id, status="queued", config=config)
            session.add(record)
            await session.flush()
            return self._run_dict(record)

    async def get_run(self, run_id: str) -> dict[str, Any] | None:
        async with self.sessions() as session:
            record = await session.get(RunRecord, run_id)
            return self._run_dict(record) if record else None

    async def list_runs(self, status: str | None, limit: int, offset: int) -> tuple[list[dict[str, Any]], int]:
        async with self.sessions() as session:
            statement = select(RunRecord)
            if status:
                statement = statement.where(RunRecord.status == status)
            records = (
                await session.execute(
                    statement.order_by(RunRecord.created_at.desc()).offset(offset).limit(limit)
                )
            ).scalars().all()
            count_statement = select(RunRecord)
            if status:
                count_statement = count_statement.where(RunRecord.status == status)
            all_records = (await session.execute(count_statement)).scalars().all()
            return [self._run_dict(record) for record in records], len(all_records)

    async def claim_run(self, run_id: str, worker_id: str, lease_seconds: int) -> bool:
        async with self.sessions.begin() as session:
            statement = select(RunRecord).where(RunRecord.id == run_id).with_for_update(skip_locked=True)
            record = (await session.execute(statement)).scalar_one_or_none()
            now = datetime.now(UTC)
            if not record or record.status in TERMINAL_STATUSES:
                return False
            if record.lease_owner not in {None, worker_id} and record.lease_expires_at and record.lease_expires_at > now:
                return False
            record.status = "running"
            record.lease_owner = worker_id
            record.lease_expires_at = now + timedelta(seconds=lease_seconds)
            record.heartbeat_at = now
            record.started_at = record.started_at or now
            record.attempt_count += 1
            return True

    async def heartbeat(self, run_id: str, worker_id: str, lease_seconds: int) -> bool:
        async with self.sessions.begin() as session:
            record = await session.get(RunRecord, run_id, with_for_update=True)
            if not record or record.lease_owner != worker_id or record.status != "running":
                return False
            now = datetime.now(UTC)
            record.heartbeat_at = now
            record.lease_expires_at = now + timedelta(seconds=lease_seconds)
            return True

    async def finish_run(
        self,
        run_id: str,
        status: str,
        *,
        result: dict[str, Any] | None = None,
        error: dict[str, Any] | None = None,
    ) -> bool:
        if status not in TERMINAL_STATUSES:
            raise ValueError("finish_run requires a terminal status")
        async with self.sessions.begin() as session:
            record = await session.get(RunRecord, run_id, with_for_update=True)
            if not record:
                return False
            if record.status in TERMINAL_STATUSES:
                return record.status == status
            record.status = status
            record.outcome = status
            record.result = result
            record.error = error
            record.completed_at = datetime.now(UTC)
            record.lease_owner = None
            record.lease_expires_at = None
            return True

    async def request_cancellation(self, run_id: str, requested_by: str | None = None) -> bool:
        async with self.sessions.begin() as session:
            record = await session.get(RunRecord, run_id)
            if not record or record.status in TERMINAL_STATUSES:
                return False
            existing = await session.get(CancellationRequestRecord, run_id)
            if not existing:
                session.add(CancellationRequestRecord(run_id=run_id, requested_by=requested_by))
            return True

    async def cancellation_requested(self, run_id: str) -> bool:
        async with self.sessions() as session:
            return await session.get(CancellationRequestRecord, run_id) is not None

    async def save_event_checkpoint(self, event: RunEvent, *, terminal: bool) -> None:
        if not terminal:
            return
        async with self.sessions.begin() as session:
            record = await session.get(EventCheckpointRecord, event.run_id)
            payload = event.model_dump(mode="json")
            if record:
                record.event_id = event.event_id or record.event_id
                record.terminal = True
                record.event = payload
            else:
                session.add(
                    EventCheckpointRecord(
                        run_id=event.run_id,
                        event_id=event.event_id or "0-0",
                        terminal=True,
                        event=payload,
                    )
                )

    async def get_terminal_event(self, run_id: str) -> RunEvent | None:
        async with self.sessions() as session:
            record = await session.get(EventCheckpointRecord, run_id)
            if not record or not record.terminal:
                return None
            return RunEvent.model_validate(record.event)

    async def add_step_attempt(
        self,
        run_id: str,
        step: str,
        attempt: int,
        status: str,
        diagnostics: dict[str, Any] | None = None,
    ) -> None:
        async with self.sessions.begin() as session:
            existing = (
                await session.execute(
                    select(StepAttemptRecord).where(
                        StepAttemptRecord.run_id == run_id,
                        StepAttemptRecord.step == step,
                        StepAttemptRecord.attempt == attempt,
                    )
                )
            ).scalar_one_or_none()
            if existing:
                return
            session.add(
                StepAttemptRecord(
                    run_id=run_id,
                    step=step,
                    attempt=attempt,
                    status=status,
                    diagnostics=diagnostics,
                )
            )

    async def add_artifacts(self, run_id: str, artifacts: list[dict[str, Any]]) -> None:
        async with self.sessions.begin() as session:
            for artifact in artifacts:
                existing = (
                    await session.execute(
                        select(ArtifactRecord).where(
                            ArtifactRecord.run_id == run_id,
                            ArtifactRecord.sha256 == artifact["sha256"],
                        )
                    )
                ).scalar_one_or_none()
                if existing:
                    continue
                session.add(
                    ArtifactRecord(
                        run_id=run_id,
                        name=artifact["name"],
                        location=artifact["path"],
                        media_type=artifact["media_type"],
                        sha256=artifact["sha256"],
                        size=artifact["size"],
                    )
                )
