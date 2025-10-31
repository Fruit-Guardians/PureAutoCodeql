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


class PythonPathAnalysisAgent:
    """分析Python项目结构以定位和总结Sink点。"""

    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "src"):
        self.analyzer = analyzer
        self.source_root = source_root

    def find_python_files(self, directory: Path) -> List[str]:
        """返回位于source_root下的Python文件的相对路径。"""
        results = []
        if directory.exists():
            for path in directory.rglob("*.py"):
                try:
                    rel_path = os.path.relpath(path, start=os.getcwd())
                    results.append(rel_path)
                except Exception:
                    results.append(str(path))
        return sorted(results)

    def build_prompt(self, cve_analysis: str, py_paths: List[str], diff_path: str = "") -> str:
        """构建详细的提示词，指导LLM定位Sink点。"""
        paths_listing = "\n".join(py_paths)
        return f"""你是一名资深的 CodeQL 安全研究员与 Python 代码审计专家，专注识别可能的 Sink 函数及其调用路径。
任务目标：基于提供的 CVE 分析、Python 文件路径和可选的 diff 文件，定位可能的漏洞接收点（Sink），并输出结构化的审计报告。
输入信息：
1. CVE 分析结果：
{cve_analysis}

2. Python 文件路径列表：
{paths_listing}

3. Diff 文件路径（可选）：
{diff_path}

4. 工作目录：`{Path.cwd()}`（所有路径均相对于该目录）

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

行动指令（严格按照顺序执行）：
1. 识别潜在 Sink 函数类别，如：
   - 命令/代码执行（os.system、subprocess.run/call/Popen、eval、exec）
   - 反序列化（pickle.load、yaml.load、marshal.load、json.loads 自定义对象钩子）
   - 文件写入/删除（open(..., 'w'/'a')、write、os.remove、shutil.rmtree）
   - 数据库/SQL 执行（cursor.execute/raw SQL、SQLAlchemy text/execute）
   - 反射/导入（importlib.import_module、__import__）
   - 路径/文件操作（open/Path 操作中使用用户控制输入）
2. 结合路径列表与代码上下文，定位候选 Sink 的定义或调用位置。
3. 构建可能的调用路径（从入口到 Sink），并说明每一步的依据与数据/控制流。

输出格式（严格遵守）：

````markdown
### Sink 定位报告：[在此填写 CVE 编号]

#### 1. 漏洞类型与风险概述
- 描述：例如命令执行、任意文件写入、RCE、SQL 注入、反序列化执行等

#### 2. Sink 位置清单
- 文件路径：`[精确文件路径]`
- 函数/方法：`[涉及 Sink 的函数或方法]`
- 相关敏感 API：`[os.system/subprocess/eval/exec/pickle/yaml/sql 等]`
- 行号：`[关键调用发生的行号]`
- 触发条件（若已知）：`[输入来源或前置条件]`

#### 3. 代码片段（必要时）
```python
# 片段，避免长注释；必要时使用 "# SINK:" 标注关键点
def vulnerable():
    # SINK: 在此标注关键调用点
    pass
```

#### 4. 初步数据流说明
- 一句话串联可能的来源到 Sink 的路径，例如：用户输入 -> 解析 -> 未验证 -> eval/exec/subprocess.run

#### 5. 备注
- 未覆盖范围、可能的误报/漏报原因
````

规则：
- 禁止虚构不存在的文件或行号；优先依据实际源码与 diff
- 若无法确定 Sink，请说明原因（代码缺失、上下文不足、API 不匹配等）
- 尽量给出可复现和定位的证据（文件、函数名、行号、关键 API）
- 输出必须为可读的 Markdown 文本
"""

    async def analyze_python_paths(self, cve_analysis: str, diff_path: str = "") -> "AgentResult":
        """执行Python项目的Sink路径分析。"""
        try:
            directory = Path(self.source_root)
            py_paths = self.find_python_files(directory)

            if not py_paths:
                from dataclasses import dataclass

                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(
                    content="No Python files found in the specified directory.",
                    success=True
                )

            prompt = self.build_prompt(cve_analysis, py_paths, diff_path)
            return await self.analyzer.run_agent(prompt)

        except Exception as exc:
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(exc))
