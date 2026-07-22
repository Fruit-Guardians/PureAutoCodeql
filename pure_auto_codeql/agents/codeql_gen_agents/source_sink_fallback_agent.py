from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
from pure_auto_codeql.paths import prompts_dir
from pure_auto_codeql.services.llm_service import AgentResult

if TYPE_CHECKING:
    from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


class SourceSinkFallbackAgent(BasePromptAgent):
    default_agent_name = "Source-Sink Fallback Agent"
    default_agent_type = "source_sink_fallback"

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        language: Optional[str] = None,
        prompt_file: Optional[Path] = None,
    ) -> None:
        super().__init__(analyzer, prompt_file)
        self.language = language or "java"

    def _resolve_prompt_path(self, language: Optional[str] = None) -> Path:
        lang = (language or self.language or "java").lower()
        if lang == "c++":
            lang = "cpp"

        mapping: Dict[str, str] = {
            "java": "java_source_sink_ql.md",
            "python": "python_source_sink_ql.md",
            "c": "c_source_sink_ql.md",
            "cpp": "c_source_sink_ql.md",
        }

        filename = mapping.get(lang) or mapping["java"]
        return (prompts_dir() / filename).resolve()

    def build_prompt(
        self,
        language: str,
        cve_analysis_report: str,
        source_analysis_report: str,
        sink_analysis_report: str,
        previous_attempts_context: Optional[str] = None,
    ) -> str:
        template = self._load_prompt(self.prompt_file or self._resolve_prompt_path(language))
        lang = language or self.language or "java"

        values = {
            "LANGUAGE": lang,
            "CVE_ANALYSIS_REPORT": cve_analysis_report or "",
            "SOURCE_ANALYSIS_REPORT": source_analysis_report or "",
            "SINK_ANALYSIS_REPORT": sink_analysis_report or "",
            "PREVIOUS_ATTEMPTS_CONTEXT": previous_attempts_context or "",
        }
        return self._fill_placeholders(template, values)

    async def generate(
        self,
        language: str,
        cve_analysis_report: str,
        source_analysis_report: str,
        sink_analysis_report: str,
        previous_attempts_context: Optional[str] = None,
        show_thinking: bool = False,
        event_callback=None,
        agent_name: Optional[str] = None,
        agent_type: Optional[str] = None,
    ) -> "AgentResult":
        try:
            await self._emit_event(
                event_callback, "agent_start",
                f"开始 Source-Sink 回退查询生成（{language}）",
                agent_name=agent_name, agent_type=agent_type,
                data={"language": language},
            )

            prompt = self.build_prompt(
                language=language,
                cve_analysis_report=cve_analysis_report,
                source_analysis_report=source_analysis_report,
                sink_analysis_report=sink_analysis_report,
                previous_attempts_context=previous_attempts_context,
            )

            result = await self.analyzer.run_agent(
                prompt,
                show_thinking=show_thinking,
                event_callback=event_callback,
            )

            await self._emit_event(
                event_callback, "agent_complete",
                f"Source-Sink 回退查询生成完成（{language}）",
                agent_name=agent_name, agent_type=agent_type,
                data={"success": getattr(result, "success", False)},
            )

            return result

        except Exception as e:  # pragma: no cover - 防御性分支
            await self._emit_event(
                event_callback, "error",
                f"Source-Sink 回退查询生成失败: {str(e)}",
                agent_name=agent_name, agent_type=agent_type,
                data={"error": str(e)},
            )
            return AgentResult(content="", success=False, error=str(e))
