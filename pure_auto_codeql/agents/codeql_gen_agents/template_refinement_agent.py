from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.services.llm_service import AgentResult

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


class TemplateRefinementAgent(BasePromptAgent):
    default_agent_name = "Template Refinement Agent"
    default_agent_type = "template_refinement"

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        language: Optional[str] = None,
        prompt_file: Optional[Path] = None,
    ) -> None:
        super().__init__(analyzer, prompt_file or (prompts_dir() / "template_refinement.md"))
        self.language = language or "java"

    def _resolve_template_path(self, language: Optional[str] = None) -> Path:
        lang = (language or self.language or "java").lower()
        if lang == "c++":
            lang = "cpp"

        mapping: Dict[str, str] = {
            "java": "java_temple_ql.md",
            "python": "python_template_ql.md",
            "c": "c_template_ql.md",
            "cpp": "c_template_ql.md",
        }

        filename = mapping.get(lang) or mapping["java"]
        return (prompts_dir() / filename).resolve()

    def build_prompt(
        self,
        error_tidy_doc: str,
        language: Optional[str] = None,
    ) -> str:
        template = self._load_prompt()
        lang = language or self.language or "java"
        template_path = self._resolve_template_path(lang)

        values = {
            "LANGUAGE": lang,
            "TEMPLATE_FILE_PATH": str(template_path),
            "TEMPLATE_CONTENT": template_path.read_text(encoding="utf-8"),
            "ERROR_TIDY_DOC": error_tidy_doc or "",
        }
        return self._fill_placeholders(template, values)

    async def refine_template(
        self,
        error_tidy_doc: str,
        language: Optional[str] = None,
        show_thinking: bool = False,
        event_callback=None,
        agent_name: Optional[str] = None,
        agent_type: Optional[str] = None,
    ) -> "AgentResult":
        try:
            await self._emit_event(
                event_callback, "agent_start",
                "开始模板优化（Template Refinement）",
                agent_name=agent_name, agent_type=agent_type,
                data={"language": language or self.language},
            )

            prompt = self.build_prompt(
                error_tidy_doc=error_tidy_doc,
                language=language,
            )

            result = await self.analyzer.run_agent(
                prompt,
                show_thinking=show_thinking,
                event_callback=event_callback,
            )

            await self._emit_event(
                event_callback, "agent_complete",
                "模板优化完成（Template Refinement 完成）",
                agent_name=agent_name, agent_type=agent_type,
                data={"success": getattr(result, "success", False)},
            )

            return result

        except Exception as e:  # pragma: no cover - 防御性分支
            await self._emit_event(
                event_callback, "error",
                f"模板优化失败: {str(e)}",
                agent_name=agent_name, agent_type=agent_type,
                data={"error": str(e)},
            )
            return AgentResult(content="", success=False, error=str(e))
