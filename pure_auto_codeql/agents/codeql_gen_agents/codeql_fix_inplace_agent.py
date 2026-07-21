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


class CodeQLFixInplaceAgent:
    """Fix CodeQL errors by modifying existing query files in-place using MCP tools.

    This agent receives error analysis suggestions and uses MCP filesystem tools
    to apply the fixes directly to the .ql file.
    """

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        prompt_file: Optional[Path] = None
    ):
        self.analyzer = analyzer
        # Always use in-place fix prompt
        self.prompt_file = prompt_file or (prompts_dir() / "codeql_fix_inplace.md")

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
        ql_file_path: str,
        curr_ql_content: str,
        prev_fix_suggestions: str,
        prev_original_ql: Optional[str] = None,
    ) -> str:
        """Build the final prompt for in-place fixing.
        
        Args:
            ql_file_path: Absolute path to the .ql file to be modified
            curr_ql_content: Current query content
            prev_fix_suggestions: Error analysis suggestions from ErrorAgent
            prev_original_ql: Previous query content (optional)
        """
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
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """Apply in-place fixes to the .ql file based on error analysis suggestions.
        
        Args:
            ql_file_path: Absolute path to the .ql file to be modified
            curr_ql_content: Current query content
            prev_fix_suggestions: Error analysis suggestions from ErrorAgent
            prev_original_ql: Previous query content (optional)
            round_index: Current iteration number
            show_thinking: Whether to show thinking process
            event_callback: Callback for events
            agent_name: Name of the agent
            agent_type: Type of the agent
        """
        try:
            _agent_name = agent_name or "CodeQL In-Place Fix Agent"
            _agent_type = agent_type or "codeql_inplace_fix"
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始CodeQL原地修复（第{round_index}轮）",
                    "data": {"round_index": round_index, "ql_file_path": ql_file_path}
                })
            
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
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL原地修复完成（第{round_index}轮）",
                    "data": {"success": result.success, "round_index": round_index}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL In-Place Fix Agent"
                _agent_type = agent_type or "codeql_inplace_fix"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL原地修复失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))

