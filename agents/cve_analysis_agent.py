from pathlib import Path
from typing import Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None
    
    class MultiAgentAnalyzer:
        pass

from utils.io import read_json_text


class CVEAnalysisAgent:
    """Agent for analyzing CVE JSON files."""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer"):
        self.analyzer = analyzer
    
    def build_prompt(self, json_text: str, intel_block: Optional[str] = None) -> str:
        """Build a prompt to convert the provided CVE JSON into focused Markdown only."""
        sections = [
            "You are a security analysis assistant. Convert the following CVE JSON into a clear, concise Markdown report.",
            "Output only Markdown with no additional commentary outside the document.",
            "Required sections:",
            "- Vulnerability Overview",
            "- Impact",
            "- Mitigation or Remediation Guidance",
        ]
        if intel_block:
            sections.append(
                "\nAdditional intelligence (GHSA/NVD fetch results). Integrate the insights, cite differences, "
                "and avoid repeating identical text verbatim. Highlight missing sources if any:\n"
            )
            sections.append(intel_block)
        sections.append("\nJSON:\n")
        sections.append(json_text)
        return "\n".join(sections)
    
    async def analyze_cve(
        self,
        json_path: Path,
        *,
        intel_prompt: Optional[str] = None,
    ) -> "AgentResult":
        """Analyze CVE JSON file and return markdown report."""
        try:
            json_text = read_json_text(json_path)
            prompt = self.build_prompt(json_text, intel_prompt)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))
