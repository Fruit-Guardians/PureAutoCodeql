import os
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


class CPathAnalysisAgent:
    """分析C/C++源文件以总结潜在的Sink点。"""

    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "src"):
        self.analyzer = analyzer
        self.source_root = source_root

    def find_c_files(self, directory: Path) -> List[str]:
        """返回C/C++文件的相对路径用于下游分析。"""
        patterns = ("*.c", "*.cc", "*.cpp", "*.cxx", "*.h", "*.hh", "*.hpp", "*.hxx")
        results = set()
        if directory.exists():
            for pattern in patterns:
                for path in directory.rglob(pattern):
                    try:
                        rel_path = os.path.relpath(path, start=os.getcwd())
                        results.add(rel_path)
                    except Exception:
                        results.add(str(path))
        return sorted(results)

    def build_prompt(self, cve_analysis: str, c_paths: List[str], diff_path: str = "") -> str:
        """构建用于定位Sink点的分析提示词。"""
        paths_listing = "\n".join(c_paths)
        return f"""你是一名资深的 CodeQL 安全研究员与 C/C++ 漏洞审计专家。

任务目标：基于提供的 CVE 分析、C/C++ 文件路径和可选的 diff 文件，定位可能的漏洞接收点（Sink），并输出结构化的审计报告。

输入信息：

1. CVE 分析结果：
{cve_analysis}

2. C/C++ 文件路径列表：
{paths_listing}

3. Diff 文件路径：
{diff_path} 注意路径是相对于工作目录的，不要错误读取不存在的文件

4. 工作目录：`{Path.cwd()}`（所有路径均相对于该目录）

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

输出格式（严格遵守）：

````markdown
### Sink 定位报告：[在此填写 CVE 编号]

#### 1. 漏洞类型与风险概述
- 描述：例如命令执行、任意文件写入、SQL 注入、反序列化执行、格式化字符串等

#### 2. Sink 位置清单
- 文件路径：`[精确文件路径]`
- 函数/方法：`[涉及 Sink 的函数或方法]`
- 相关敏感 API：`[system/exec/write/strcpy/sql/模板等]`
- 行号：`[关键调用发生的行号]`
- 触发条件（若已知）：`[输入来源或前置条件]`

#### 3. 代码片段（必要时）
```c
// 片段，避免长注释；必要时使用 "// SINK:" 标注关键点
void vulnerable() {{
    // SINK: 在此标注关键调用点
}}
```

#### 4. 初步数据流说明
- 一句话串联可能的来源到 Sink 的路径，例如：用户输入 -> 解析 -> 未验证 -> system/exec

#### 5. 备注
- 未覆盖范围、可能的误报/漏报原因
````

规则：
- 禁止虚构不存在的文件或行号；优先依据实际源码与 diff
- 若无法确定 Sink，请说明原因（代码缺失、上下文不足、API 不匹配等）
- 尽量给出可复现和定位的证据（文件、函数名、行号、关键 API）
- 输出必须为可读的 Markdown 文本
"""

    async def analyze_c_paths(self, cve_analysis: str, diff_path: str = "") -> "AgentResult":
        """运行C/C++代码的Sink路径分析工作流。"""
        try:
            directory = Path(self.source_root)
            c_paths = self.find_c_files(directory)

            if not c_paths:
                from dataclasses import dataclass

                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(
                    content="No C/C++ files found in the specified directory.",
                    success=True
                )

            prompt = self.build_prompt(cve_analysis, c_paths, diff_path)
            return await self.analyzer.run_agent(prompt)

        except Exception as exc:
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(exc))
