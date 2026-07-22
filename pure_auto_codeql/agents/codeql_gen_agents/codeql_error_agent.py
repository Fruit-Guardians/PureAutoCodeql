from pathlib import Path
from typing import TYPE_CHECKING, Optional

from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.services.llm_service import AgentResult

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


class CodeQLErrorAgent(BasePromptAgent):
    """Analyze and diagnose CodeQL compilation/runtime errors.

    This agent analyzes errors and provides structured fix suggestions,
    but does NOT generate or modify code directly.
    """

    default_agent_name = "CodeQL Error Analysis Agent"
    default_agent_type = "codeql_error_analysis"

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        prompt_file: Optional[Path] = None
    ):
        super().__init__(analyzer, prompt_file or (prompts_dir() / "codeql_erroranalyze.md"))

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
        show_thinking: bool = False,
        event_callback=None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Run error analysis with current and previous context to produce fix suggestions."""
        try:
            await self._emit_event(
                event_callback, "agent_start",
                f"开始CodeQL错误分析（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"round_index": round_index},
            )

            prompt = self.build_prompt(
                error_log=error_log,
                curr_ql_content=curr_ql_content,
                round_index=round_index,
                prev_original_ql=prev_original_ql,
            )

            result = await self.analyzer.run_agent(
                prompt,
                show_thinking=show_thinking,
                event_callback=event_callback
            )

            await self._emit_event(
                event_callback, "agent_complete",
                f"CodeQL错误分析完成（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"success": result.success, "round_index": round_index},
            )

            return result

        except Exception as e:
            await self._emit_event(
                event_callback, "error",
                f"CodeQL错误分析失败: {str(e)}",
                agent_name=agent_name, agent_type=agent_type,
                data={"error": str(e)},
            )
            return AgentResult(content="", success=False, error=str(e))
