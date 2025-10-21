import json
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None


def read_json_text(path: Path) -> str:
    """Read a JSON file, validate its syntax, and return the original text."""
    if not path.exists():
        raise FileNotFoundError(f"Missing file: {path}")
    text = path.read_text(encoding="utf-8")
    try:
        json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Invalid JSON: {e}")
    return text


def write_analysis_output(cve_result: "AgentResult", sink_result: "AgentResult", source_result: "AgentResult", output_path: Path = Path("output.md")) -> None:
    """Write combined Source and Sink analysis results to a Markdown file."""
    try:
        sections = [
            "# Multi-Agent Analysis Output\n",
            "\n## CVE Analysis\n",
            (cve_result.content if cve_result and cve_result.success else f"(failed) {cve_result.error if cve_result else 'no result'}"),
            "\n\n## Java Sink Analysis\n",
            (sink_result.content if sink_result and sink_result.success else f"(failed) {sink_result.error if sink_result else 'no result'}"),
            "\n\n## Java Source Analysis\n",
            (source_result.content if source_result and source_result.success else f"(failed) {source_result.error if source_result else 'no result'}"),
            "\n"
        ]
        output = "".join(sections)
        output_path.write_text(output, encoding="utf-8")
        print(f"Output written to {output_path}")
    except Exception as e:
        print(f"Failed to write analysis output: {e}")