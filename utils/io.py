import json
from pathlib import Path
from typing import Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    from utils.intel import IntelBundle


def read_json_text(path: Path) -> str:
    """读取JSON文件，验证其语法，并返回原始文本。"""
    if not path.exists():
        raise FileNotFoundError(f"Missing file: {path}")
    text = path.read_text(encoding="utf-8")
    try:
        json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Invalid JSON: {e}")
    return text


def write_analysis_output(
    cve_result: "AgentResult",
    sink_result: "AgentResult",
    source_result: "AgentResult",
    output_path: Path = Path("output.md"),
    *,
    intel_bundle: Optional["IntelBundle"] = None,
) -> None:
    """将合并的Source和Sink分析结果写入Markdown文件。"""
    try:
        sections = [
            "# Multi-Agent Analysis Output\n",
        ]
        if intel_bundle:
            sections.extend(
                [
                    "\n## 情报采集\n",
                    intel_bundle.prompt_block(),
                ]
            )
        sections.extend(
            [
                "\n## CVE Analysis\n",
                (cve_result.content if cve_result and cve_result.success else f"(failed) {cve_result.error if cve_result else 'no result'}"),
                "\n\n## Java Sink Analysis\n",
                (sink_result.content if sink_result and sink_result.success else f"(failed) {sink_result.error if sink_result else 'no result'}"),
                "\n\n## Java Source Analysis\n",
                (source_result.content if source_result and source_result.success else f"(failed) {source_result.error if source_result else 'no result'}"),
                "\n"
            ]
        )
        output = "".join(sections)
        output_path.write_text(output, encoding="utf-8")
        print(f"Output written to {output_path}")
    except Exception as e:
        print(f"Failed to write analysis output: {e}")
