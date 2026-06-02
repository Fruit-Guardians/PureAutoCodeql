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
    """Analyze and diagnose CodeQL compilation/runtime errors.
    
    This agent analyzes errors and provides structured fix suggestions,
    but does NOT generate or modify code directly.
    """

    def __init__(
        self, 
        analyzer: "MultiAgentAnalyzer", 
        prompt_file: Optional[Path] = None
    ):
        self.analyzer = analyzer
        # Always use error analysis prompt
        self.prompt_file = prompt_file or (
            Path(__file__).resolve().parent.parent.parent / "prompts" / "codeql_erroranalyze.md"
        )

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
        """Build the final prompt including error logs and QL contents.
        
        Args:
            error_log: Error log from CodeQL execution
            curr_ql_content: Current query content
            round_index: Current iteration number
            prev_original_ql: Previous query content (optional)
        """
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
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Run error analysis with current and previous context to produce fix suggestions.
        
        Args:
            error_log: Error log from CodeQL execution
            curr_ql_content: Current query content
            round_index: Current iteration number
            prev_original_ql: Previous query content (optional)
            show_thinking: Whether to show thinking process
            event_callback: Callback for events
            agent_name: Name of the agent
            agent_type: Type of the agent
        """
        try:
            _agent_name = agent_name or "CodeQL Error Analysis Agent"
            _agent_type = agent_type or "codeql_error_analysis"
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始CodeQL错误分析（第{round_index}轮）",
                    "data": {"round_index": round_index}
                })
            
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
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL错误分析完成（第{round_index}轮）",
                    "data": {"success": result.success, "round_index": round_index}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL Error Analysis Agent"
                _agent_type = agent_type or "codeql_error_analysis"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL错误分析失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))