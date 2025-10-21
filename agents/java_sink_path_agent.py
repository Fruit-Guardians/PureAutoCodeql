from pathlib import Path
from typing import List, TYPE_CHECKING

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


class JavaPathAnalysisAgent:
    """Agent for analyzing Java file paths in decompiled source code."""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "h5-vsan-service.jar_Decompiler.com"):
        self.analyzer = analyzer
        self.source_root = source_root
    
    def build_prompt(self, cve_analysis: str, java_paths: List[str], diff_path: str = "") -> str:
        """Build prompt for Java path analysis agent."""
        # Placeholder prompt - can be customized based on requirements
        return (
            "你是一个Java源代码分析助手。"
            "请分析以下信息：\n\n"
            f"CVE分析结果：\n{cve_analysis}\n\n"
            f"Java文件路径：\n" + "\n".join(java_paths) + "\n\n"
            f"差异路径：{diff_path}\n\n"
            f"你可以调用的工具：server-filesystem和sequential-thinking进行文件读取和长逻辑思考，工作目录在{Path.cwd()}\n\n"
            "请根据以上信息读取目标java文件，并告诉我目标java文件在该漏洞下的利用点SINK在哪里，你可以直接调用工具无需过问，你可以直接确定要继续"
        )
    
    def find_java_files(self, directory: Path) -> List[str]:
        """Find all Java files in the specified directory."""
        java_files = []
        if directory.exists():
            for java_file in directory.rglob("*.java"):
                canonical_path = find_path_from_java_file(str(java_file), self.source_root)
                if canonical_path:
                    java_files.append(canonical_path)
        return java_files
    
    async def analyze_java_paths(self, cve_analysis: str, diff_path: str = "") -> "AgentResult":
        """Analyze Java file paths and provide comprehensive analysis."""
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
            
            prompt = self.build_prompt(cve_analysis, java_paths, diff_path)
            return await self.analyzer.run_agent(prompt)
        
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))