import logging
from types import SimpleNamespace

from pure_auto_codeql.analysis_models import AnalysisOutcome, StepResult
from pure_auto_codeql.utils.logger import (
    ConsoleFormatter,
    clean_terminal_message,
    print_user_success,
)
from pure_auto_codeql.utils.terminal_ui import (
    StreamBlock,
    print_result_card,
    print_stage_end,
    print_stage_start,
    summarize_tool_input,
)


def test_clean_terminal_message_removes_legacy_and_nerd_font_prefixes():
    assert clean_terminal_message("✅ 󰄬 分析完成") == "分析完成"
    assert clean_terminal_message("  ⚠️  查询为空") == "查询为空"


def test_console_formatter_is_compact_and_has_no_module_or_timestamp():
    record = logging.LogRecord(
        name="pure_auto_codeql.example",
        level=logging.INFO,
        pathname=__file__,
        lineno=1,
        msg="✅ 分析完成",
        args=(),
        exc_info=None,
    )

    rendered = ConsoleFormatter().format(record)

    assert rendered == "󰋼  分析完成"
    assert "pure_auto_codeql.example" not in rendered


def test_user_output_has_one_status_icon(capsys):
    print_user_success("✅ 分析完成")

    assert capsys.readouterr().out == "󰄬  分析完成\n"


def test_stage_output_is_numbered_and_reports_duration(capsys):
    print_stage_start(2, 5, "sink_analysis")
    print_stage_end("sink_analysis", StepResult().status, 1.25)

    output = capsys.readouterr().out
    assert "02/05 · Sink Analysis" in output
    assert "succeeded" in output
    assert "1.2s" in output


def test_stream_block_prefixes_each_reasoning_line(capsys):
    block = StreamBlock()
    block.start("Sink Agent")
    block.write("first line\nsecond")
    block.write(" line\n")
    block.finish()

    output = capsys.readouterr().out
    assert "  │ first line\n" in output
    assert "  │ second line\n" in output
    assert "reasoning complete" in output


def test_tool_input_summary_prefers_useful_fields():
    summary = summarize_tool_input(
        {"query": "AsyncClient.get", "path": "src/serde.py", "unused": True}
    )

    assert summary == "query=AsyncClient.get · path=src/serde.py"


def test_result_card_contains_outcome_and_relative_artifacts(capsys, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    result = SimpleNamespace(
        outcome=AnalysisOutcome.COMPLETED_WITH_FINDINGS,
        step_results={"sink_analysis": StepResult()},
        execution_time=2.4,
        output_directory=str(tmp_path / "output" / "run"),
        case_id="CVE-2025-0001",
        language="python",
    )

    print_result_card(result)

    output = capsys.readouterr().out
    assert "completed_with_findings" in output
    assert "1/1 succeeded" in output
    assert "output/run" in output
