"""Integration tests for AnalysisPipeline.execute orchestration.

These drive the real pipeline with lightweight fake AnalysisStep instances --
no LLM, no CodeQL, no network. They lock the orchestration contract:
ordered execution, result mapping, short-circuit on failure, exception
containment, and the always-run consolidation in the finally block.
"""

import pytest

from pure_auto_codeql.core.context import AnalysisConfig, AnalysisContext
from pure_auto_codeql.core.pipeline import AnalysisPipeline, AnalysisStep
from pure_auto_codeql.services.llm_service import AgentResult

STEP_NAMES = [
    "cve_analysis",
    "sink_analysis",
    "source_analysis",
    "path_analysis",
    "codeql_generation",
]


class RecordingStep(AnalysisStep):
    """Fake step that records execution order and returns a canned result."""

    def __init__(self, name, order, *, success=True, error=None, raises=None,
                 set_exec_result=None):
        super().__init__(name)
        self._order = order
        self._success = success
        self._error = error
        self._raises = raises
        self._set_exec_result = set_exec_result

    async def execute(self, context):
        self._order.append(self.name)
        if self._raises is not None:
            raise self._raises
        if self._set_exec_result is not None:
            context.data["codeql_execution_result"] = self._set_exec_result
        return AgentResult(content=f"{self.name}-content",
                           success=self._success, error=self._error)


def make_context():
    # case_paths / cve_assets are unused by the orchestration path (and are
    # only touched by consolidation, which the orchestration tests stub out).
    return AnalysisContext(
        case_id="TEST-CASE-1",
        case_paths=None,
        cve_assets=None,
        language="java",
    )


def stub_consolidation(pipeline, calls):
    """Replace _consolidate_output_files with an async spy (no IO)."""

    async def _spy(context, result, config):
        calls.append((context, result, config))

    pipeline._consolidate_output_files = _spy


# --------------------------------------------------------------------------- #
# Orchestration (consolidation stubbed out)
# --------------------------------------------------------------------------- #

@pytest.mark.asyncio
async def test_steps_execute_in_order_and_map_to_result():
    order = []
    steps = [
        RecordingStep("cve_analysis", order),
        RecordingStep("sink_analysis", order),
        RecordingStep("source_analysis", order),
        RecordingStep("path_analysis", order),
        RecordingStep("codeql_generation", order,
                      set_exec_result={"sentinel": "exec"}),
    ]
    pipeline = AnalysisPipeline(steps)
    stub_consolidation(pipeline, [])
    context = make_context()

    result = await pipeline.execute(context, AnalysisConfig())

    assert order == STEP_NAMES
    assert result.success is True
    assert result.cve_result.content == "cve_analysis-content"
    assert result.sink_result.content == "sink_analysis-content"
    assert result.source_result.content == "source_analysis-content"
    assert result.path_analysis_result.content == "path_analysis-content"
    assert result.codeql_result.content == "codeql_generation-content"
    assert result.codeql_execution_result == {"sentinel": "exec"}
    assert result.execution_time is not None and result.execution_time >= 0


@pytest.mark.asyncio
async def test_step_failure_short_circuits_remaining_steps():
    order = []
    steps = [
        RecordingStep("cve_analysis", order),
        RecordingStep("sink_analysis", order, success=False, error="boom"),
        RecordingStep("source_analysis", order),
        RecordingStep("path_analysis", order),
        RecordingStep("codeql_generation", order),
    ]
    pipeline = AnalysisPipeline(steps)
    stub_consolidation(pipeline, [])

    result = await pipeline.execute(make_context(), AnalysisConfig())

    # sink_analysis ran and failed; nothing after it executed.
    assert order == ["cve_analysis", "sink_analysis"]
    assert result.success is False
    assert "sink_analysis" in result.error_message
    assert "boom" in result.error_message
    # Result carried the successful steps up to the failure.
    assert result.cve_result.content == "cve_analysis-content"
    assert result.source_result is None


@pytest.mark.asyncio
async def test_step_exception_is_caught_and_not_propagated():
    order = []
    steps = [
        RecordingStep("cve_analysis", order),
        RecordingStep("sink_analysis", order, raises=RuntimeError("kaboom")),
        RecordingStep("source_analysis", order),
    ]
    pipeline = AnalysisPipeline(steps)
    stub_consolidation(pipeline, [])

    result = await pipeline.execute(make_context(), AnalysisConfig())

    assert order == ["cve_analysis", "sink_analysis"]
    assert result.success is False
    assert result.error_message == "kaboom"


@pytest.mark.asyncio
async def test_consolidation_runs_even_when_a_step_raises():
    order = []
    calls = []
    steps = [RecordingStep("cve_analysis", order, raises=ValueError("x"))]
    pipeline = AnalysisPipeline(steps)
    stub_consolidation(pipeline, calls)

    result = await pipeline.execute(make_context(), AnalysisConfig())

    # finally-block consolidation fired exactly once despite the exception.
    assert len(calls) == 1
    assert calls[0][1] is result


@pytest.mark.asyncio
async def test_executed_step_results_recorded_in_context():
    order = []
    steps = [
        RecordingStep("cve_analysis", order),
        RecordingStep("sink_analysis", order),
    ]
    pipeline = AnalysisPipeline(steps)
    stub_consolidation(pipeline, [])
    context = make_context()

    await pipeline.execute(context, AnalysisConfig())

    assert context.has_result("cve_analysis")
    assert context.has_result("sink_analysis")
    assert context.get_result("cve_analysis").content == "cve_analysis-content"


@pytest.mark.asyncio
async def test_default_config_used_when_none_passed():
    order = []
    pipeline = AnalysisPipeline([RecordingStep("cve_analysis", order)])
    calls = []
    stub_consolidation(pipeline, calls)

    await pipeline.execute(make_context(), config=None)

    # A default AnalysisConfig was constructed and threaded into consolidation.
    assert isinstance(calls[0][2], AnalysisConfig)


def test_create_default_pipeline_has_five_named_steps():
    pipeline = AnalysisPipeline.create_default_pipeline()
    assert [s.name for s in pipeline.steps] == STEP_NAMES


# --------------------------------------------------------------------------- #
# Consolidation finally-block (real IO into a tmp dir)
# --------------------------------------------------------------------------- #

@pytest.mark.asyncio
async def test_consolidation_writes_summary_into_output_dir(tmp_path):
    order = []
    steps = [RecordingStep(name, order) for name in STEP_NAMES]
    pipeline = AnalysisPipeline(steps)
    config = AnalysisConfig(output_base_dir=str(tmp_path), keep_output_dirs=0)

    result = await pipeline.execute(make_context(), config)

    assert result.success is True
    assert result.error_message is None
    assert result.output_directory is not None
    run_dir = tmp_path / "TEST-CASE-1"
    assert run_dir.exists()
    summary = next(run_dir.rglob("summary.md"), None)
    assert summary is not None and summary.is_file()
