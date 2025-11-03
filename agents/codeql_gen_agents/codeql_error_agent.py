from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

if TYPE_CHECKING:
    from dataclasses import dataclass

    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    class MultiAgentAnalyzer:
        pass


class CodeQLErrorAgent:
    """Diagnose and propose fixes for CodeQL compilation/runtime errors using a dynamic prompt.

    This agent reads the prompt from prompts/codeql_erroranalyze.md and injects
    iterative placeholders (error logs, current/previous QL) to produce actionable fixes.
    """

    def __init__(self, analyzer: "MultiAgentAnalyzer", prompt_file: Optional[Path] = None):
        self.analyzer = analyzer
        # Default prompt path: prompts/codeql_erroranalyze.md
        self.prompt_file = prompt_file or (Path(__file__).resolve().parent.parent.parent / "prompts" / "codeql_erroranalyze.md")

    def _load_prompt(self) -> str:
        """Load prompt template content from markdown file."""
        try:
            return self.prompt_file.read_text(encoding="utf-8")
        except Exception as e:
            return f"Error loading prompt file: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        """Replace [[KEY]] placeholders in the template with provided values."""
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, (v or ""))
        return result

    def build_prompt(
        self,
        error_log: str,
        curr_ql_content: str,
        round_index: int = 1,
        prev_original_ql: Optional[str] = None,
    ) -> str:
        """Build the final prompt including error logs and QL contents."""
        template = self._load_prompt()
        values = {
            "ROUND_INDEX": str(round_index or 1),
            "ERROR_LOG": error_log or "",
            "CURR_QL_CONTENT": curr_ql_content or "",
            "PREV_ORIGINAL_QL": prev_original_ql or "",
        }
        return self._fill_placeholders(template, values)

    async def analyze(
        self,
        error_log: str,
        curr_ql_content: str,
        round_index: int = 1,
        prev_original_ql: Optional[str] = None,
    ) -> "AgentResult":
        """Run error analysis with current and previous context to produce fix suggestions."""
        try:
            prompt = self.build_prompt(
                error_log=error_log,
                curr_ql_content=curr_ql_content,
                round_index=round_index,
                prev_original_ql=prev_original_ql,
            )
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))