import os
from pathlib import Path
from typing import TYPE_CHECKING, Optional

# 运行时可用的AgentResult类定义
class AgentResult:
    def __init__(self, content: str, success: bool, error: str = None):
        self.content = content
        self.success = success
        self.error = error

if TYPE_CHECKING:
    from dataclasses import dataclass

    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    class MultiAgentAnalyzer:
        pass

from prompts import build_sink_prompt


class UnifiedSinkPathAgent:
    """统一分析Java/Python/C/C++ Sink点的agent。"""

    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = ""):
        self.analyzer = analyzer
        self.source_root = source_root

    def build_prompt(self, language: str, cve_analysis: str, source_path: str, diff_path: Optional[Path] = None) -> str:
        """构建针对不同语言的提示词。"""
        return build_sink_prompt(language, cve_analysis, source_path, diff_path)

    async def analyze_paths(self, language: str, cve_analysis: str, diff_path: str = "", show_thinking: bool = True) -> "AgentResult":
        """统一的分析方法，根据语言类型执行相应的分析。"""
        try:
            directory = Path(self.source_root).resolve()

            # 将路径规范化为相对于 MCP 文件系统根目录（projects）的相对路径
            server_root = (Path.cwd() / "projects").resolve()
            try:
                source_rel = os.path.relpath(directory, start=server_root)
            except Exception:
                source_rel = str(directory)

            abs_diff_path = Path(diff_path).resolve() if diff_path else None
            if abs_diff_path is not None:
                try:
                    diff_rel = os.path.relpath(abs_diff_path, start=server_root)
                except Exception:
                    diff_rel = str(abs_diff_path)
            else:
                diff_rel = ""

            if diff_rel == "":
                diff_rel = "注意，diff文件并未给出，请根据CVE分析结果综合判断sink的类型"
           
            print("diff_rel:", "/projects/" + diff_rel)

            prompt = self.build_prompt(language, cve_analysis, source_rel, abs_diff_path)
            return await self.analyzer.run_agent(prompt, show_thinking=show_thinking)

        except Exception as exc:
            return AgentResult(content="", success=False, error=str(exc))