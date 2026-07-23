import asyncio
import zipfile
from datetime import UTC, datetime
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from pure_auto_codeql.analysis_models import (
    AnalysisOutcome,
    Artifact,
    StepResult,
    StepStatus,
)
from pure_auto_codeql.api.durable_task_manager import DurableTaskManager
from pure_auto_codeql.api.models import AnalysisTaskInfo, TaskStatus
from pure_auto_codeql.api.security import SlidingWindowRateLimiter, TokenVerifier
from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.platform.db import Base, create_engine_and_sessions
from pure_auto_codeql.platform.events import RunEvent
from pure_auto_codeql.platform.queue import MemoryStreamBroker
from pure_auto_codeql.platform.repository import (
    InMemoryRunRepository,
    SqlRunRepository,
)
from pure_auto_codeql.utils.project_importer.filesystem import ImportLimits, _safe_extract_zip
from pure_auto_codeql.worker import AnalysisWorker


@pytest.mark.asyncio
async def test_memory_broker_replays_events_after_event_id():
    broker = MemoryStreamBroker()
    first = await broker.publish_event(
        RunEvent(run_id="run-1", type="progress", message="one")
    )
    second = await broker.publish_event(
        RunEvent(run_id="run-1", type="completed", message="two")
    )

    stream = broker.events("run-1", first.event_id)
    replayed = await anext(stream)
    await stream.aclose()

    assert replayed.event_id == second.event_id
    assert replayed.message == "two"


@pytest.mark.asyncio
async def test_sse_terminal_event_survives_redis_history_loss():
    repository = InMemoryRunRepository()
    broker = MemoryStreamBroker()
    manager = DurableTaskManager(repository, broker)
    await repository.create_run("run-terminal", "CASE", {})
    terminal = RunEvent(
        event_id="42-0",
        run_id="run-terminal",
        type="completed",
        message="durable terminal",
    )
    await repository.save_event_checkpoint(terminal, terminal=True)

    stream = manager.events("run-terminal", "10-0")
    replayed = await anext(stream)
    with pytest.raises(StopAsyncIteration):
        await anext(stream)

    assert replayed.event_id == "42-0"
    assert replayed.message == "durable terminal"


@pytest.mark.asyncio
async def test_worker_delivery_is_idempotent(monkeypatch):
    repository = InMemoryRunRepository()
    broker = MemoryStreamBroker()
    config = AnalysisConfig(
        enable_cve_analysis=False,
        enable_sink_analysis=False,
        enable_source_analysis=False,
        enable_path_analysis=False,
        enable_codeql_generation=False,
        enable_path_selection=False,
    )
    await repository.create_run("run-1", "CASE-1", {**config.__dict__, "event_callback": None})
    await broker.enqueue("run-1", {})
    message = (await broker.consume("worker-1"))[0]

    calls = 0

    class FakeOrchestrator:
        def __init__(self, effective_config):
            self.config = effective_config

        async def analyze_case(self, case_id, language=None):
            nonlocal calls
            calls += 1
            assert case_id == "CASE-1"
            return SimpleNamespace(
                outcome=AnalysisOutcome.COMPLETED_NO_FINDINGS,
                output_directory="/tmp/run-1",
                step_results={
                    "codeql_generation": StepResult(
                        status=StepStatus.SUCCEEDED,
                        metrics={"attempts": 1},
                    )
                },
                artifacts=[
                    Artifact(
                        name="query.ql",
                        path="/tmp/run-1/query.ql",
                        media_type="text/plain",
                        sha256="a" * 64,
                        size=12,
                    )
                ],
            )

    monkeypatch.setattr("pure_auto_codeql.worker.AnalysisOrchestrator", FakeOrchestrator)
    worker = AnalysisWorker(repository, broker, worker_id="worker-1")
    await worker.process(message)
    await worker.process(message)

    run = await repository.get_run("run-1")
    assert run["status"] == "completed_no_findings"
    assert run["attempt_count"] == 1
    assert run["step_attempts"][0]["step"] == "codeql_generation"
    assert run["artifact_records"][0]["sha256"] == "a" * 64
    assert calls == 1
    assert not broker.pending


@pytest.mark.asyncio
async def test_worker_observes_durable_cancellation(monkeypatch):
    repository = InMemoryRunRepository()
    broker = MemoryStreamBroker()
    config = AnalysisConfig(task_timeout=30)
    await repository.create_run("run-cancel", "CASE-1", {**config.__dict__, "event_callback": None})
    await broker.enqueue("run-cancel", {})
    message = (await broker.consume("worker-1"))[0]
    started = asyncio.Event()

    class SlowOrchestrator:
        def __init__(self, effective_config):
            del effective_config

        async def analyze_case(self, case_id, language=None):
            del case_id, language
            started.set()
            await asyncio.sleep(30)

    monkeypatch.setattr("pure_auto_codeql.worker.AnalysisOrchestrator", SlowOrchestrator)
    worker = AnalysisWorker(repository, broker, worker_id="worker-1")
    processing = asyncio.create_task(worker.process(message))
    await started.wait()
    await repository.request_cancellation("run-cancel")
    await processing

    run = await repository.get_run("run-cancel")
    assert run["status"] == "cancelled"
    assert not broker.pending


@pytest.mark.asyncio
async def test_worker_preserves_message_owned_by_live_lease():
    repository = InMemoryRunRepository()
    broker = MemoryStreamBroker()
    await repository.create_run("run-live", "CASE-1", {})
    await broker.enqueue("run-live", {})
    message = (await broker.consume("worker-1"))[0]
    assert await repository.claim_run("run-live", "worker-1", 60)

    worker = AnalysisWorker(repository, broker, worker_id="worker-2")
    await worker.process(message)

    assert message.message_id in broker.pending


def test_token_hash_rotation_and_rate_limit():
    import hashlib

    digest = hashlib.sha256(b"next-token").hexdigest()
    verifier = TokenVerifier(f"old-token,sha256:{digest}")
    assert verifier.verify("Bearer old-token")
    assert verifier.verify("Bearer next-token")
    assert not verifier.verify("Bearer wrong")

    limiter = SlidingWindowRateLimiter(2)
    assert limiter.allow("client")
    assert limiter.allow("client")
    assert not limiter.allow("client")


def test_zip_import_limits_reject_high_compression_ratio(tmp_path):
    archive = tmp_path / "bomb.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr("payload.txt", b"A" * 100_000)

    with pytest.raises(ValueError, match="compression ratio"):
        _safe_extract_zip(
            archive,
            tmp_path / "out",
            ImportLimits(max_compression_ratio=2.0),
        )


@pytest.mark.asyncio
async def test_memory_repository_lifecycle_and_lease_rules():
    repository = InMemoryRunRepository()
    created = await repository.create_run("run-1", "CASE", {"language": "python"})
    duplicate = await repository.create_run("run-1", "OTHER", {})
    assert created == duplicate
    assert await repository.ping()
    assert await repository.schema_ready()
    assert await repository.claim_run("missing", "worker-a", 30) is False
    assert await repository.claim_run("run-1", "worker-a", 30)
    assert await repository.claim_run("run-1", "worker-b", 30) is False
    assert await repository.heartbeat("run-1", "worker-b", 30) is False
    assert await repository.heartbeat("run-1", "worker-a", 30)
    assert await repository.request_cancellation("run-1", "tester")
    assert await repository.cancellation_requested("run-1")
    assert await repository.finish_run(
        "run-1",
        "completed_no_findings",
        result={"findings": 0},
    )
    assert await repository.finish_run("run-1", "completed_no_findings")
    assert not await repository.request_cancellation("run-1")
    assert not await repository.claim_run("run-1", "worker-a", 30)
    with pytest.raises(ValueError):
        await repository.finish_run("run-1", "running")
    runs, total = await repository.list_runs("completed_no_findings", 10, 0)
    assert total == 1
    assert runs[0]["result"] == {"findings": 0}
    terminal = RunEvent(
        event_id="9-0",
        run_id="run-1",
        type="completed",
        message="done",
    )
    await repository.save_event_checkpoint(terminal, terminal=False)
    await repository.save_event_checkpoint(terminal, terminal=True)
    assert repository.events["run-1"]["event_id"] == "9-0"
    assert (await repository.get_terminal_event("run-1")).event_id == "9-0"


@pytest.mark.asyncio
async def test_sql_repository_lifecycle(tmp_path):
    engine, sessions = create_engine_and_sessions(f"sqlite+aiosqlite:///{tmp_path / 'runs.db'}")
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    repository = SqlRunRepository(sessions)
    try:
        assert await repository.ping()
        assert not await repository.schema_ready()
        async with engine.begin() as connection:
            await connection.execute(
                text("CREATE TABLE alembic_version (version_num VARCHAR(32) PRIMARY KEY)")
            )
            await connection.execute(
                text("INSERT INTO alembic_version (version_num) VALUES ('0001_durable_runs')")
            )
        assert await repository.schema_ready()
        await repository.create_run("run-1", "CASE", {"language": "java"})
        duplicate = await repository.create_run("run-1", "OTHER", {})
        assert duplicate["case_id"] == "CASE"
        assert await repository.claim_run("run-1", "worker-a", 30)
        assert await repository.heartbeat("run-1", "worker-a", 30)
        assert await repository.request_cancellation("run-1", "tester")
        assert await repository.request_cancellation("run-1", "tester")
        assert await repository.cancellation_requested("run-1")
        await repository.add_step_attempt(
            "run-1",
            "source_analysis",
            1,
            "succeeded",
            {"confidence": 0.9},
        )
        artifact_records = [
            {
                "name": "manifest.json",
                "path": "/tmp/manifest.json",
                "media_type": "application/json",
                "sha256": "b" * 64,
                "size": 42,
            }
        ]
        await repository.add_step_attempt(
            "run-1",
            "source_analysis",
            1,
            "succeeded",
            {"confidence": 0.9},
        )
        await repository.add_artifacts(
            "run-1",
            artifact_records,
        )
        await repository.add_artifacts("run-1", artifact_records)
        await repository.save_event_checkpoint(
            RunEvent(event_id="1-0", run_id="run-1", type="completed", message="done"),
            terminal=False,
        )
        assert await repository.get_terminal_event("run-1") is None
        await repository.save_event_checkpoint(
            RunEvent(event_id="2-0", run_id="run-1", type="completed", message="done"),
            terminal=True,
        )
        await repository.save_event_checkpoint(
            RunEvent(event_id="3-0", run_id="run-1", type="completed", message="done again"),
            terminal=True,
        )
        assert (await repository.get_terminal_event("run-1")).event_id == "3-0"
        assert await repository.finish_run("run-1", "completed_no_findings", result={"ok": True})
        assert await repository.finish_run("run-1", "completed_no_findings")
        assert not await repository.request_cancellation("run-1")
        runs, total = await repository.list_runs(None, 10, 0)
        assert total == 1
        assert runs[0]["result"] == {"ok": True}
        with pytest.raises(ValueError):
            await repository.finish_run("run-1", "running")
    finally:
        await engine.dispose()


def test_api_parameters_reach_effective_analysis_config(monkeypatch):
    from pure_auto_codeql.api import analysis_routes, server

    captured = {}

    class FakeDurableManager:
        async def create_task(self, case_id, config):
            captured.update(config.__dict__)
            return AnalysisTaskInfo(
                task_id="run-1",
                run_id="run-1",
                case_id=case_id,
                status=TaskStatus.QUEUED,
                created_at=datetime.now(UTC),
                effective_config={"language": config.language},
                event_url="/api/v1/analysis/run-1/stream",
            )

    monkeypatch.setattr(analysis_routes, "get_durable_task_manager", lambda: FakeDurableManager())
    monkeypatch.setattr(analysis_routes, "validate_analysis_case", lambda *args, **kwargs: None)
    response = TestClient(server.app).post(
        "/api/v1/analysis/start",
        json={
            "case_id": "CASE",
            "language": "python",
            "requirement": "find command injection",
            "max_rounds": 7,
            "enable_cve_analysis": False,
            "enable_sink_analysis": False,
            "enable_source_analysis": False,
            "enable_path_analysis": False,
            "enable_codeql_generation": True,
            "enable_path_selection": False,
            "enable_breakpoint_recovery": True,
            "enable_source_sink_fallback": True,
            "timeout_seconds": 123,
        },
    )
    assert response.status_code == 202
    expected = {
        "language": "python",
        "requirement": "find command injection",
        "max_codeql_rounds": 7,
        "enable_cve_analysis": False,
        "enable_sink_analysis": False,
        "enable_source_analysis": False,
        "enable_path_analysis": False,
        "enable_codeql_generation": True,
        "enable_path_selection": False,
        "enable_breakpoint_recovery": True,
        "enable_source_sink_fallback": True,
        "task_timeout": 123,
    }
    assert {key: captured[key] for key in expected} == expected
