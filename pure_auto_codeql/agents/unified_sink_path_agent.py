import logging
from pathlib import Path
from typing import TYPE_CHECKING, Optional

from pure_auto_codeql.services.llm_service import AgentResult

logger = logging.getLogger(__name__)

if TYPE_CHECKING:
    class MultiAgentAnalyzer:
        pass

from pure_auto_codeql.prompts import build_sink_prompt


class UnifiedSinkPathAgent:
    """统一分析Java/Python/C/C++ Sink点的agent。"""

    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = ""):
        self.analyzer = analyzer
        self.source_root = source_root

    def build_prompt(self, language: str, cve_analysis: str, source_path: str, diff_path: Optional[Path] = None) -> str:
        """构建针对不同语言的提示词。"""
        return build_sink_prompt(language, cve_analysis, source_path, diff_path)

    async def analyze_paths(self, language: str, cve_analysis: str, diff_path: str = "", show_thinking: bool = True, event_callback=None, agent_name: str = None, agent_type: str = None) -> "AgentResult":
        """统一的分析方法，根据语言类型执行相应的分析。"""
        try:
            _agent_name = agent_name or "Sink Path Analysis Agent"
            _agent_type = agent_type or "sink_analysis"

            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始{language}语言Sink路径分析",
                    "data": {"language": language, "diff_path": diff_path}
                })

            directory = Path(self.source_root).resolve()

            def format_for_prompt(path: Optional[Path]) -> str:
                return str(path.resolve()) if path is not None else ""

            def extract_diff_relative_paths(diff_file: Optional[Path]) -> list:
                paths = []
                if not diff_file or not diff_file.is_file():
                    return paths
                try:
                    with diff_file.open("r", encoding="utf-8", errors="ignore") as fh:
                        for line in fh:
                            if line.startswith("diff --git "):
                                parts = line.strip().split()
                                if len(parts) >= 4:
                                    a_path = parts[2]
                                    b_path = parts[3]
                                    if a_path.startswith("a/"):
                                        paths.append(Path(a_path[2:]))
                                    if b_path.startswith("b/"):
                                        paths.append(Path(b_path[2:]))
                            if len(paths) >= 10:
                                break
                except Exception:
                    logger.debug("解析 diff 文件路径失败", exc_info=True)
                return paths

            def infer_source_root(base_dir: Path, relative_paths: list) -> Path:
                for rel_path in relative_paths:
                    rel_parts = rel_path.parts
                    try:
                        for candidate in base_dir.rglob(rel_path.name):
                            if not candidate.exists():
                                continue
                            try:
                                candidate_rel = candidate.relative_to(base_dir)
                            except ValueError:
                                continue
                            cand_parts = candidate_rel.parts
                            if len(cand_parts) >= len(rel_parts) and cand_parts[-len(rel_parts):] == rel_parts:
                                prefix_parts = cand_parts[:-len(rel_parts)]
                                if prefix_parts:
                                    return base_dir.joinpath(*prefix_parts)
                                return base_dir
                    except Exception:
                        continue
                return base_dir

            abs_diff_path = Path(diff_path).resolve() if diff_path else None
            diff_relative_paths = extract_diff_relative_paths(abs_diff_path)

            source_root_for_prompt = infer_source_root(directory, diff_relative_paths)

            source_prompt_path = format_for_prompt(source_root_for_prompt) or str(source_root_for_prompt)

            diff_prompt_path = format_for_prompt(abs_diff_path)

            if not diff_prompt_path:
                diff_prompt_path = "注意，diff文件并未给出，请根据CVE分析结果综合判断sink的类型"

            logger.debug("diff_path_for_prompt: %s", diff_prompt_path)

            prompt = self.build_prompt(language, cve_analysis, source_prompt_path, diff_prompt_path)
            result = await self.analyzer.run_agent(prompt, show_thinking=show_thinking, event_callback=event_callback)

            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"{language}语言Sink路径分析完成",
                    "data": {"success": result.success}
                })

            return result

        except Exception as exc:
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"Sink路径分析失败: {str(exc)}",
                    "data": {"error": str(exc)}
                })

            return AgentResult(content="", success=False, error=str(exc))
