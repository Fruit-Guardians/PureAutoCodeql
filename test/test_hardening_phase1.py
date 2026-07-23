import asyncio
import json
from pathlib import Path

import pytest

from pure_auto_codeql.analysis_models import AnalysisOutcome, StepResult, StepStatus
from pure_auto_codeql.analysis_schemas import (
    VerificationStatus,
    normalize_source_candidate,
)
from pure_auto_codeql.api.models import TaskStatus
from pure_auto_codeql.api.task_manager import TaskManager
from pure_auto_codeql.core.context import AnalysisConfig, AnalysisContext
from pure_auto_codeql.core.pipeline import AnalysisPipeline
from pure_auto_codeql.services.artifacts import (
    ArtifactRegistry,
    LocalArtifactStore,
    S3ArtifactStore,
)
from pure_auto_codeql.services.codeql_composition import (
    BudgetedAnalyzer,
    CodeQLErrorCategory,
    CompositionController,
    CompositionState,
    RetryBudget,
    RetryBudgetExceeded,
)


def test_analysis_config_rejects_invalid_step_dependencies():
    with pytest.raises(ValueError, match="source analysis requires sink analysis"):
        AnalysisConfig(enable_sink_analysis=False).validate()

    with pytest.raises(ValueError, match="path analysis requires source analysis"):
        AnalysisConfig(
            enable_source_analysis=False,
            enable_path_analysis=True,
        ).validate()

    with pytest.raises(ValueError, match="path selection requires CodeQL generation"):
        AnalysisConfig(
            enable_codeql_generation=False,
            enable_path_selection=True,
        ).validate()


@pytest.mark.asyncio
async def test_disabled_steps_are_recorded_as_skipped():
    config = AnalysisConfig(
        enable_cve_analysis=False,
        enable_sink_analysis=False,
        enable_source_analysis=False,
        enable_path_analysis=False,
        enable_codeql_generation=False,
        enable_path_selection=False,
        keep_output_dirs=0,
    )
    pipeline = AnalysisPipeline.create_default_pipeline(config)
    calls = []

    async def fake_consolidation(context, result, effective_config):
        calls.append((context, result, effective_config))

    pipeline._consolidate_output_files = fake_consolidation
    context = AnalysisContext(
        case_id="SKIPPED-CASE",
        case_paths=None,
        cve_assets=None,
        language="python",
    )

    result = await pipeline.execute(context, config)

    assert calls
    assert result.success is True
    assert result.outcome == AnalysisOutcome.COMPLETED_NO_FINDINGS
    assert set(result.step_results) == {
        "cve_analysis",
        "sink_analysis",
        "source_analysis",
        "path_analysis",
        "codeql_generation",
    }
    assert all(item.status == StepStatus.SKIPPED for item in result.step_results.values())


def test_step_result_legacy_constructor_and_structured_error():
    result = StepResult(content="", success=False, error="syntax failed")

    assert result.success is False
    assert result.status == StepStatus.FAILED
    assert result.error == "syntax failed"
    assert result.to_dict()["error"]["code"] == "step_failed"


@pytest.mark.asyncio
async def test_task_deadline_sets_timed_out_status(monkeypatch):
    manager = TaskManager()

    async def slow_analysis(task_id, case_id, config):
        del task_id, case_id, config
        await asyncio.sleep(10)

    monkeypatch.setattr(manager, "_run_analysis", slow_analysis)
    run_id = manager.create_task("TIMEOUT", AnalysisConfig(task_timeout=1))

    assert await manager.start_task(run_id)
    await manager._running_tasks[run_id]

    task = manager.get_task_status(run_id)
    assert task is not None
    assert task.status == TaskStatus.TIMED_OUT
    assert task.completed_at is not None
    assert manager._task_events[run_id][-1]["data"]["timed_out"] is True


def test_manifest_written_by_pipeline_integration(tmp_path):
    class SuccessfulStep:
        name = "cve_analysis"

        async def execute(self, context):
            del context
            return StepResult(content="ok", success=True)

    pipeline = AnalysisPipeline([SuccessfulStep()])
    context = AnalysisContext(
        case_id="MANIFEST-CASE",
        case_paths=None,
        cve_assets=None,
        language="java",
    )
    config = AnalysisConfig(
        output_base_dir=str(tmp_path),
        keep_output_dirs=0,
        enable_path_selection=False,
    )

    result = asyncio.run(pipeline.execute(context, config))

    manifest_path = Path(result.output_directory) / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert manifest["case_id"] == "MANIFEST-CASE"
    assert manifest["effective_config"]["api_key"] is None
    assert manifest["steps"]["cve_analysis"]["status"] == "succeeded"
    assert any(item["path"] == "summary.md" for item in manifest["artifacts"])


def test_composition_controller_deduplicates_queries_and_classifies_errors():
    controller = CompositionController(RetryBudget())

    assert controller.register_query("from Foo select Foo") is True
    assert controller.register_query("from Foo select Foo") is False
    assert controller.duplicate_queries == 1
    assert (
        controller.classify_error('{"format":"lsp_diagnostics"}')
        == CodeQLErrorCategory.SYNTAX
    )
    assert controller.classify_error("database is invalid") == CodeQLErrorCategory.DATABASE
    assert controller.classify_error("No results.") == CodeQLErrorCategory.EMPTY_RESULT
    assert controller.classify_error("tool timed out") == CodeQLErrorCategory.TOOL_TIMEOUT
    assert (
        controller.classify_error("resource exhausted")
        == CodeQLErrorCategory.RESOURCE_EXHAUSTED
    )
    assert controller.classify_error("query crashed") == CodeQLErrorCategory.EXECUTION
    assert controller.classify_error("") == CodeQLErrorCategory.UNKNOWN
    controller.transition(CompositionState.VALIDATE, round=1)
    summary = controller.summary()
    assert summary["state"] == "validate"
    assert summary["transitions"][-1]["metadata"] == {"round": 1}


def test_composition_budget_validates_and_times_out():
    with pytest.raises(ValueError):
        RetryBudget(generation_attempts=0).validate()
    controller = CompositionController(RetryBudget(max_elapsed_seconds=1))
    controller.started_at -= 2
    with pytest.raises(RetryBudgetExceeded, match="seconds"):
        controller.check_time()


@pytest.mark.asyncio
async def test_budgeted_analyzer_enforces_global_llm_call_limit():
    class FakeAnalyzer:
        async def run_agent(self, prompt, *args, **kwargs):
            del args, kwargs
            return prompt

    controller = CompositionController(RetryBudget(max_total_llm_calls=1))
    analyzer = BudgetedAnalyzer(FakeAnalyzer(), controller)

    assert await analyzer.run_agent("first") == "first"
    with pytest.raises(RetryBudgetExceeded):
        await analyzer.run_agent("second")

    controller.mark_failed("budget")
    assert controller.state == CompositionState.FAILED


def test_source_evidence_requires_real_source_location(tmp_path):
    source = tmp_path / "app.py"
    source.write_text("def entry(value):\n    return value\n", encoding="utf-8")

    verified = normalize_source_candidate(
        {
            "file_path": "app.py",
            "line_number": 1,
            "function_name": "entry",
            "reason": "request data enters here",
            "confidence": "high",
        },
        tmp_path,
    )
    missing_evidence = normalize_source_candidate(
        {
            "file_path": "app.py",
            "line_number": 1,
            "function_name": "entry",
            "confidence": "high",
        },
        tmp_path,
    )

    assert verified.verification_status is VerificationStatus.VERIFIED
    assert missing_evidence.verification_status is VerificationStatus.UNVERIFIED


def test_artifact_registry_hashes_and_store_rejects_escape(tmp_path):
    run_dir = tmp_path / "runs" / "run-1"
    run_dir.mkdir(parents=True)
    summary = run_dir / "summary.md"
    summary.write_text("result", encoding="utf-8")

    artifact = ArtifactRegistry(run_dir).register(summary)
    assert artifact.sha256
    assert artifact.size == 6
    assert artifact.path == "summary.md"

    store = LocalArtifactStore(tmp_path / "store")
    with pytest.raises(ValueError):
        store.put_file("run-1", "../escape.txt", summary)

    stored = store.put_file("run-1", "reports/summary.md", summary)
    with store.open(
        type(stored)(
            name=stored.name,
            path=f"run-1/{stored.path}",
            media_type=stored.media_type,
            sha256=stored.sha256,
            size=stored.size,
        )
    ) as handler:
        assert handler.read() == b"result"

    class FakeS3:
        uploads = []

        def upload_file(self, filename, bucket, key):
            self.uploads.append((filename, bucket, key))

    s3 = S3ArtifactStore(FakeS3(), "bucket", "prefix")
    uploaded = s3.put_file("run-1", "summary.md", summary)
    assert uploaded.path == "s3://bucket/prefix/run-1/summary.md"
    with pytest.raises(ValueError):
        s3.put_file("run-1", "../escape", summary)
    with pytest.raises(NotImplementedError):
        s3.open(uploaded)
