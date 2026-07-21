from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

from pure_auto_codeql.paths import prompts_dir

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

    This agent reads the prompt from prompts/codeql_generate.md and injects
    iterative placeholders before sending it to the shared MultiAgentAnalyzer.
    """

    def __init__(self, analyzer: "MultiAgentAnalyzer", prompt_file: Optional[Path] = None):
        self.analyzer = analyzer
        # Default prompt path: prompts/codeql_generate.md
        self.prompt_file = prompt_file or (prompts_dir() / "codeql_generate.md")

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
        show_thinking: bool = False,
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Generate CodeQL query content using MultiAgentAnalyzer with prompt context."""
        try:
            _agent_name = agent_name or "CodeQL Generation Agent"
            _agent_type = agent_type or "codeql_generation"
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始CodeQL查询生成（第{round_index}轮）",
                    "data": {"language": language, "round_index": round_index}
                })
            
            prompt = self.build_prompt(
                language=language,
                requirement=requirement,
                round_index=round_index,
                prev_original_ql=prev_original_ql,
                prev_fix_suggestions=prev_fix_suggestions,
            )
            
            result = await self.analyzer.run_agent(
                prompt, 
                show_thinking=show_thinking, 
                event_callback=event_callback
            )
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL查询生成完成（第{round_index}轮）",
                    "data": {"success": result.success, "round_index": round_index}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL Generation Agent"
                _agent_type = agent_type or "codeql_generation"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL查询生成失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))