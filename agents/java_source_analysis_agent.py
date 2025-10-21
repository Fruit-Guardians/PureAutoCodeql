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
    
    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "h5-vsan-service.jar_Decompiler.com"):
        self.analyzer = analyzer
        self.source_root = source_root
    
    def build_prompt(self, cve_analysis: str, java_paths: List[str]) -> str:
        current_dir = os.getcwd()
        java_paths_str = "\n".join(java_paths)

        #TODO: 这里的prompt之后需要修改为调用codeql生成和解析codeql查询结果
        return (
            f"""你是一名顶级的CodeQL安全研究员和Java代码审计专家，专注于识别可能的Source候选函数。

    任务目标：基于提供的CVE信息和Java文件路径，仅产出“可能存在Source点的函数列表”。

    输入信息：

    1. CVE分析结果：
    {cve_analysis}

    2. 相关Java文件路径：
    {java_paths}

    3. 工作目录：`{current_dir}`（所有路径均为相对该目录）。

    可用工具：
    - server-filesystem：读取文件内容
    - sequential-thinking：多步骤推理

    行动指令：
    1. 理解CVE涉及的不可信输入来源类型（HTTP参数、头、Cookie、反序列化、文件/路径、环境变量、网络IO、数据库结果、表达式/模板等）。
    2. 审计源码：使用 server-filesystem 读取 `{java_paths_str}` 中的文件，定位“可能接收不可信输入”的函数/方法。无需进行完整数据流溯源，仅做候选定位与简要理由。
    3. 识别候选函数：关注如下模式并给出理由与置信度（high/medium/low）：
       - Servlet/Spring MVC 参数绑定与取参（HttpServletRequest、@RequestParam、@PathVariable、@RequestBody 等）
       - 反序列化入口（ObjectInputStream、readObject、Yaml/JSON/XML 解析）
       - 文件系统/路径构造（new File、Paths.get、ServletContext.getRealPath 等）
       - 环境变量/系统属性读取（System.getenv、System.getProperty）
       - 网络/套接字/消息队列输入
       - 任何第三方框架/库的用户输入接收点

    输出要求（必须严格遵守）：
    - 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
    - JSON 结构如下：
    {{
      "cve": "",
      "candidates": [
        {{
          "file_path": "相对路径（如 src/.../X.java）",
          "class_name": "类名",
          "method_name": "方法名",
          "signature": "方法签名（含参数类型）",
          "start_line": 0,
          "end_line": 0,
          "reason": "为什么此函数可能是Source（关键API/取参点/框架绑定等）",
          "confidence": "high|medium|low"
        }}
      ]
    }}

    规则：
    - 若没有发现候选函数，请输出：{{"candidates": []}}
    - 可以使用工具读取 `{java_paths_str}` 中的文件内容。
    - 请确保输出为合法可解析的 JSON。
    """
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