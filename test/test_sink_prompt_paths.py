from pathlib import Path

import pytest

from pure_auto_codeql.agents.unified_sink_path_agent import UnifiedSinkPathAgent
from pure_auto_codeql.analysis_models import AgentResult


class _PromptCapturingAnalyzer:
    def __init__(self) -> None:
        self.prompt = ""

    async def run_agent(self, prompt: str, **_kwargs) -> AgentResult:
        self.prompt = prompt
        return AgentResult(content="ok", success=True)


@pytest.mark.asyncio
async def test_sink_agent_uses_authorized_absolute_paths(tmp_path: Path) -> None:
    case_dir = tmp_path / "projects" / "CVE-2025-54381"
    source_dir = case_dir / "repo"
    source_file = source_dir / "src" / "serde.py"
    source_file.parent.mkdir(parents=True)
    source_file.write_text("async def deserialize(): pass\n", encoding="utf-8")

    diff_file = case_dir / "inputs" / "CVE-2025-54381.diff"
    diff_file.parent.mkdir(parents=True)
    diff_file.write_text(
        "diff --git a/src/serde.py b/src/serde.py\n",
        encoding="utf-8",
    )

    analyzer = _PromptCapturingAnalyzer()
    agent = UnifiedSinkPathAgent(analyzer, str(case_dir))

    result = await agent.analyze_paths(
        "python",
        "SSRF analysis",
        str(diff_file),
        show_thinking=False,
    )

    assert result.success
    assert str(source_dir.resolve()) in analyzer.prompt
    assert str(diff_file.resolve()) in analyzer.prompt
    assert f"{case_dir}/projects/CVE-2025-54381" not in analyzer.prompt
    assert "不要添加 `projects/` 等前缀" in analyzer.prompt
