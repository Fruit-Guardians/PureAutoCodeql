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


class CodeQLGenAgent:
    """Generate CodeQL queries using a dynamic prompt template with iterative context.

    This agent reads the prompt from agents/prompts/codeql_generate.md and injects
    iterative placeholders before sending it to the shared MultiAgentAnalyzer.
    """

    def __init__(self, analyzer: "MultiAgentAnalyzer", prompt_file: Optional[Path] = None):
        self.analyzer = analyzer
        # Default prompt path: agents/prompts/codeql_generate.md
        self.prompt_file = prompt_file or (Path(__file__).resolve().parent.parent / "prompts" / "codeql_generate.md")

    def _load_prompt(self) -> str:
        """Load prompt template content from markdown file."""
        try:
            return self.prompt_file.read_text(encoding="utf-8")
        except Exception as e:
            return f"Error loading prompt file: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        """Replace [[KEY]] placeholders in the template with provided values.

        This avoids str.format brace collisions with JSON/Markdown by using [[...]] markers.
        """
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, (v or ""))
        return result

    def build_prompt(
        self,
        language: str,
        requirement: str,
        round_index: int = 1,
        prev_original_ql: Optional[str] = None,
        prev_fix_suggestions: Optional[str] = None,
    ) -> str:
        """Build the final prompt by injecting iterative context placeholders."""
        template = self._load_prompt()
        values = {
            "ROUND_INDEX": str(round_index or 1),
            "LANGUAGE": language or "java",
            "REQUIREMENT": requirement or "",
            "PREV_ORIGINAL_QL": prev_original_ql or "",
            "PREV_FIX_SUGGESTIONS": prev_fix_suggestions or "",
        }
        return self._fill_placeholders(template, values)

    async def generate(
        self,
        language: str,
        requirement: str,
        round_index: int = 1,
        prev_original_ql: Optional[str] = None,
        prev_fix_suggestions: Optional[str] = None,
    ) -> "AgentResult":
        """Generate CodeQL query content using MultiAgentAnalyzer with prompt context."""
        try:
            prompt = self.build_prompt(
                language=language,
                requirement=requirement,
                round_index=round_index,
                prev_original_ql=prev_original_ql,
                prev_fix_suggestions=prev_fix_suggestions,
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