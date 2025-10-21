from pathlib import Path
from typing import TYPE_CHECKING

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
    
    def build_prompt(self, json_text: str) -> str:
        """Build a prompt to convert the provided CVE JSON into focused Markdown only."""
        return (
            "You are a security analysis assistant. Convert the following CVE JSON into a clear, concise Markdown report.\n"
            "Only output the final Markdown without any extra commentary or styling beyond Markdown itself.漏洞的利用类型的CWE编号同时带上具体含义\n\n"
            "最终的输出形式必须是Markdown格式，不要包含任何额外的注释或样式，不包含漏洞修复相关的内容，只包含以下内容:\n"
            "- 利用类型\n"
            "- 漏洞点\n"
            "- 利用条件\n"
            "JSON:\n" + json_text
        )
    
    async def analyze_cve(self, json_path: Path) -> "AgentResult":
        """Analyze CVE JSON file and return markdown report."""
        try:
            json_text = read_json_text(json_path)
            prompt = self.build_prompt(json_text)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))