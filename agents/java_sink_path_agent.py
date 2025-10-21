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
        f"""你是一名顶级的CodeQL安全研究员和Java代码审计专家。

你的核心任务是基于提供的CVE信息、代码差异和文件路径，精准定位并深度分析Java代码中的漏洞利用终点（Sink），最终生成一份结构化的、高质量的Sink点分析报告。

**输入信息:**

1. **CVE分析结果**:
   ```
   {cve_analysis}
   ```
   * **作用**: 这将帮助你理解漏洞的根本原因、类型（如：SQL注入、远程代码执行、反序列化等）以及它如何被利用。

2. **相关Java文件路径**:
   ```
   {java_paths}
   ```
   * **作用**: 这些是你需要重点审计的目标文件。

3. **代码差异文件路径**:
   ```
   {diff_path}
   ```
   * **作用**: 这是定位Sink的关键线索。通过分析补丁前后的代码变化，可以快速聚焦到漏洞修复的核心位置，其附近的代码极有可能就是Sink点。

4. **工作目录**: 你的工作目录在 `{Path.cwd()}`。所有文件路径都是基于此目录的相对路径。

**可用工具:**

* `server-filesystem`: 用于读取文件内容。
* `sequential-thinking`: 用于进行复杂的、多步骤的逻辑推理。
**输出格式 (必须严格遵守):**

````markdown
### Sink点分析报告：[此处填写CVE编号]

#### 1. 漏洞类型
* **类型**: [例如：远程代码执行 (RCE), SQL注入, 不安全的反序列化]

#### 2. Sink点定位
* **文件路径**: `[定位到的具体文件路径]`
* **类名**: `[包含Sink点的类名]`
* **方法名**: `[包含Sink点的方法名]`
* **行号**: `[Sink点代码所在的具体行号]`

#### 3. Sink代码片段
```java
// 在此处粘贴Sink点周围的关键代码，并用注释 `// SINK:` 标记出准确的Sink行
```

#### 4. 数据流路径简述
* **简述**: [用一句话描述污染数据是如何从Source传递到Sink的。例如：用户输入通过HTTP请求的`param`参数进入，未经处理直接传递给`buildQuery`方法，最终在`executeQuery`方法中执行，构成了SQL注入。]

**执行规则:**

* 你可以直接调用工具，无需事先征求同意。
* 整个过程必须保持自主性，直接按步骤执行并输出最终报告。
* 如果分析后无法明确找到Sink点，请在报告的“分析与理由”部分清楚地说明，并解释可能的原因（例如，漏洞逻辑复杂，关键代码不在提供的文件范围内等）。
"""
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