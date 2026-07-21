from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional

from pure_auto_codeql.paths import get_repo_root, prompts_dir

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


class TemplateRefinementAgent:
    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        language: Optional[str] = None,
        prompt_file: Optional[Path] = None,
    ) -> None:
        self.analyzer = analyzer
        self.language = language or "java"

        self._project_root = get_repo_root()
        self.prompt_file = prompt_file or (prompts_dir() / "template_refinement.md")

    def _load_prompt(self) -> str:
        try:
            return self.prompt_file.read_text(encoding="utf-8")
        except Exception as e:  # pragma: no cover - 防御性分支
            return f"Error loading prompt file: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, v or "")
        return result

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
        prompts_dir = self._project_root / "prompts"
        return (prompts_dir / filename).resolve()

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
            _agent_name = agent_name or "Template Refinement Agent"
            _agent_type = agent_type or "template_refinement"

            if event_callback:
                from datetime import datetime

                await event_callback(
                    {
                        "type": "agent_start",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": "开始模板优化（Template Refinement）",
                        "data": {"language": language or self.language},
                    }
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

            if event_callback:
                from datetime import datetime

                await event_callback(
                    {
                        "type": "agent_complete",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": "模板优化完成（Template Refinement 完成）",
                        "data": {"success": getattr(result, "success", False)},
                    }
                )

            return result

        except Exception as e:  # pragma: no cover - 防御性分支
            if event_callback:
                from datetime import datetime

                _agent_name = agent_name or "Template Refinement Agent"
                _agent_type = agent_type or "template_refinement"
                await event_callback(
                    {
                        "type": "error",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": f"模板优化失败: {str(e)}",
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
