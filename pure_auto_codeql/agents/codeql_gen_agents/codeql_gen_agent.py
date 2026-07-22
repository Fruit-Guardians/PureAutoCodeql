from pathlib import Path
from typing import TYPE_CHECKING, Optional

from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.services.llm_service import AgentResult

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


class CodeQLGenAgent(BasePromptAgent):
    """Generate CodeQL queries using a dynamic prompt template with iterative context.

    This agent reads the prompt from prompts/codeql_generate.md and injects
    iterative placeholders before sending it to the shared MultiAgentAnalyzer.
    """

    default_agent_name = "CodeQL Generation Agent"
    default_agent_type = "codeql_generation"

    def __init__(self, analyzer: "MultiAgentAnalyzer", prompt_file: Optional[Path] = None):
        super().__init__(analyzer, prompt_file or (prompts_dir() / "codeql_generate.md"))

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
        event_callback=None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Generate CodeQL query content using MultiAgentAnalyzer with prompt context."""
        try:
            await self._emit_event(
                event_callback, "agent_start",
                f"开始CodeQL查询生成（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"language": language, "round_index": round_index},
            )

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

            await self._emit_event(
                event_callback, "agent_complete",
                f"CodeQL查询生成完成（第{round_index}轮）",
                agent_name=agent_name, agent_type=agent_type,
                data={"success": result.success, "round_index": round_index},
            )

            return result

        except Exception as e:
            await self._emit_event(
                event_callback, "error",
                f"CodeQL查询生成失败: {str(e)}",
                agent_name=agent_name, agent_type=agent_type,
                data={"error": str(e)},
            )
            return AgentResult(content="", success=False, error=str(e))
