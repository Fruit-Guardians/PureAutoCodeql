from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

from pure_auto_codeql.paths import get_repo_root

if TYPE_CHECKING:
    from dataclasses import dataclass

    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    class MultiAgentAnalyzer:
        async def run_agent(self, prompt: str, show_thinking: bool = False, event_callback=None) -> "AgentResult":  # type: ignore[override]
            ...


class SourceSinkFallbackAgent:
    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        language: Optional[str] = None,
        prompt_file: Optional[Path] = None,
    ) -> None:
        self.analyzer = analyzer
        self.language = language or "java"

        self._project_root = get_repo_root()
        self.prompt_file = prompt_file

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
        prompts_dir = self._project_root / "prompts"
        return (prompts_dir / filename).resolve()

    def _load_prompt(self, language: Optional[str] = None) -> str:
        try:
            prompt_path = self.prompt_file or self._resolve_prompt_path(language)
            return prompt_path.read_text(encoding="utf-8")
        except Exception as e:  # pragma: no cover - 防御性分支
            return f"Error loading prompt file: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, v or "")
        return result

    def build_prompt(
        self,
        language: str,
        cve_analysis_report: str,
        source_analysis_report: str,
        sink_analysis_report: str,
        previous_attempts_context: Optional[str] = None,
    ) -> str:
        template = self._load_prompt(language=language)
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
            _agent_name = agent_name or "Source-Sink Fallback Agent"
            _agent_type = agent_type or "source_sink_fallback"

            if event_callback:
                from datetime import datetime

                await event_callback(
                    {
                        "type": "agent_start",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": f"开始 Source-Sink 回退查询生成（{language}）",
                        "data": {
                            "language": language,
                        },
                    }
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

            if event_callback:
                from datetime import datetime

                await event_callback(
                    {
                        "type": "agent_end",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": f"Source-Sink 回退查询生成完成（{language}）",
                        "data": {"success": getattr(result, "success", False)},
                    }
                )

            return result

        except Exception as e:  # pragma: no cover - 防御性分支
            if event_callback:
                from datetime import datetime

                _agent_name = agent_name or "Source-Sink Fallback Agent"
                _agent_type = agent_type or "source_sink_fallback"
                await event_callback(
                    {
                        "type": "agent_error",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": f"Source-Sink 回退查询生成失败: {str(e)}",
                        "data": {"error": str(e)},
                    }
                )

            from dataclasses import dataclass

            @dataclass
            class AgentResult:  # type: ignore[redefinition]
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))
