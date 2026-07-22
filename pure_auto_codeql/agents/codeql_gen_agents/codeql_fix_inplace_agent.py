from pathlib import Path
from typing import TYPE_CHECKING, Optional

from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.services.llm_service import AgentResult

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


class CodeQLFixInplaceAgent(BasePromptAgent):
    """Fix CodeQL errors by modifying existing query files in-place using MCP tools.

    This agent receives error analysis suggestions and uses MCP filesystem tools
    to apply the fixes directly to the .ql file.
    """

    default_agent_name = "CodeQL In-Place Fix Agent"
    default_agent_type = "codeql_inplace_fix"

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        prompt_file: Optional[Path] = None
    ):
        super().__init__(analyzer, prompt_file or (prompts_dir() / "codeql_fix_inplace.md"))

    def build_prompt(
        self,
        ql_file_path: str,
        curr_ql_content: str,
        prev_fix_suggestions: str,
        prev_original_ql: Optional[str] = None,
    ) -> str:
        """Build the final prompt for in-place fixing."""
        template = self._load_prompt()
        values = {
            "QL_FILE_PATH": ql_file_path or "",
            "CURR_QL_CONTENT": curr_ql_content or "",
            "PREV_FIX_SUGGESTIONS": prev_fix_suggestions or "",
            "PREV_ORIGINAL_QL": prev_original_ql or "",
        }
        return self._fill_placeholders(template, values)

    async def fix(
        self,
        ql_file_path: str,
        curr_ql_content: str,
        prev_fix_suggestions: str,
        prev_original_ql: Optional[str] = None,
        round_index: int = 1,
        show_thinking: bool = False,
        event_callback=None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Apply in-place fixes to the .ql file based on error analysis suggestions."""
        try:
            await self._emit_event(
                event_callback, "agent_start",
                f"开始CodeQL原地修复（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"round_index": round_index, "ql_file_path": ql_file_path},
            )

            prompt = self.build_prompt(
                ql_file_path=ql_file_path,
                curr_ql_content=curr_ql_content,
                prev_fix_suggestions=prev_fix_suggestions,
                prev_original_ql=prev_original_ql,
            )

            result = await self.analyzer.run_agent(
                prompt,
                show_thinking=show_thinking,
                event_callback=event_callback
            )

            await self._emit_event(
                event_callback, "agent_complete",
                f"CodeQL原地修复完成（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"success": result.success, "round_index": round_index},
            )

            return result

        except Exception as e:
            await self._emit_event(
                event_callback, "error",
                f"CodeQL原地修复失败: {str(e)}",
                agent_name=agent_name, agent_type=agent_type,
                data={"error": str(e)},
            )
            return AgentResult(content="", success=False, error=str(e))
