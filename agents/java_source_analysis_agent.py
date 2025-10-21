from pathlib import Path
from typing import List, TYPE_CHECKING
import os

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None
    
    class MultiAgentAnalyzer:
        pass

from utils.java import find_path_from_java_file


class JavaSourceAnalysisAgent:
    """Agent for analyzing Java source files to identify Source points."""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "h5-vsan-service.jar_Decompiler.com"):
        self.analyzer = analyzer
        self.source_root = source_root
    
    def build_prompt(self, cve_analysis: str, java_paths: List[str]) -> str:
        """Build prompt for Java source analysis agent (Source detection)."""
        current_dir = os.getcwd()
        return (
            "你是一个Java源代码分析助手。"
            "请分析以下信息：\n\n"
            f"CVE分析结果：\n{cve_analysis}\n\n"
            f"Java文件路径：\n" + "\n".join(java_paths) + "\n\n"
            f"你可以调用的工具：server-filesystem和sequential-thinking进行文件读取和长逻辑思考，工作目录在{current_dir}\n\n"
            "请根据以上信息读取目标java文件，并识别可能的SOURCE（数据来源点），例如用户输入、外部请求参数、配置读取、环境变量等。"
            "输出应清晰标注来源点的位置（文件路径、类名、方法名、行号），并简要说明其作为数据来源的理由。"
        )
    
    def find_java_files(self, directory: Path) -> List[str]:
        """Find all Java files in the specified directory (same as Sink agent)."""
        java_files = []
        if directory.exists():
            for java_file in directory.rglob("*.java"):
                canonical_path = find_path_from_java_file(str(java_file), self.source_root)
                if canonical_path:
                    java_files.append(canonical_path)
        return java_files
    
    async def analyze_java_sources(self, cve_analysis: str) -> "AgentResult":
        """Analyze Java sources and identify possible Source points."""
        try:
            directory = Path(self.source_root)
            java_paths = self.find_java_files(directory)
            
            if not java_paths:
                from dataclasses import dataclass
                
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None
                
                return AgentResult(
                    content="No Java files found in the specified directory.",
                    success=True
                )
            
            prompt = self.build_prompt(cve_analysis, java_paths)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))