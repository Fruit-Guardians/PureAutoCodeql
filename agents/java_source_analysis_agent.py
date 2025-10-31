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


class JavaSourceAnalysisAgent:
    """用于分析Java源代码以识别潜在source点的Agent。"""

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        source_root: str = "h5-vsan-service.jar_Decompiler.com",
    ):
        self.analyzer = analyzer
        self.source_root = source_root
    
    def build_prompt(self, sink_analysis: str, source_path: Path) -> str:
        """构建Java源码分析Agent的提示词。"""
        return (
        f"""你是一名顶级的CodeQL安全研究员和Java代码审计专家。

你的核心任务是通过分析sink点的漏洞信息，分析出可能的source点

**输入信息:**

1. **sink点分析结果**:
   ```
   {sink_analysis}
   ```
   * **作用**: 这将帮你直接找到sink点，你需要反推来找到具体的source点位置。

2. **项目源代码路径**:
   ```
   {source_path}
   ```
   * **作用**: 你可以调用工具去读取java文件来辅助分析。

3. **工作目录**: 你的工作目录在 `{Path.cwd()}`。所有文件路径都是基于此目录的相对路径。

**可用工具:**

* `server-filesystem`: 用于读取文件内容，使用此工具时需特别注意，要尽量少的读取文件。
**输出格式 (必须严格遵守，不能有任何额外的注释或解释和多的标题):**

````markdown
### Source点分析报告：[此处填写CVE编号]
#### 1. Source点定位
* **文件路径**: `[定位到的具体文件路径]`
* **类名**: `[包含Source点的类名]`
* **方法名**: `[包含Source点的方法名]`

#### 2. Source代码片段
```java
```
````
**执行规则:**

* 你可以直接调用工具，无需事先征求同意。
* 整个过程必须保持自主性，直接按步骤执行并输出最终报告。
* 如果分析后无法明确找到Source点，请在报告的“分析与理由”部分清楚地说明，并解释可能的原因（例如，漏洞逻辑复杂，关键代码不在提供的文件范围内等）。
"""
    )
    
    
    async def analyze_java_sources(self, sink_analysis: str) -> "AgentResult":
        """分析Java源码并识别可能的Source点。"""
        try:
            directory = Path(self.source_root)
            
            prompt = self.build_prompt(sink_analysis, directory)
            return await self.analyzer.run_agent(prompt)
        
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))
