import os
import json
import logging
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

from tools.codeql_generator_tool import CodeQLGeneratorTool
from tools.codeql_runner_tool import CodeQLRunnerTool

logger = logging.getLogger(__name__)


class PythonSourceAnalysisAgent:
    """Analyze Python sources to identify possible Source points using CodeQL tools."""

    def __init__(self, analyzer: "MultiAgentAnalyzer", source_root: str = "src", database_path: str = "db-python"):
        self.analyzer = analyzer
        self.source_root = source_root
        self.database_path = database_path
        self.codeql_generator = CodeQLGeneratorTool(analyzer=analyzer)
        self.codeql_runner = CodeQLRunnerTool()

    def find_python_files(self, directory: Path) -> List[str]:
        """Find all Python files in the specified directory and return relative paths."""
        py_files = []
        if directory.exists():
            for py_file in directory.rglob("*.py"):
                try:
                    rel_path = os.path.relpath(py_file, start=os.getcwd())
                    py_files.append(rel_path)
                except Exception:
                    py_files.append(str(py_file))
        return py_files

    def build_prompt(self, cve_analysis: str, py_paths: List[str]) -> str:
        current_dir = os.getcwd()
        py_paths_str = "\n".join(py_paths)
        return f"""你是一名顶级的CodeQL安全研究员和Python代码审计专家，专注于识别可能的Source候选函数。

任务目标：基于提供的CVE信息和Python文件路径，自主使用CodeQL工具进行分析，仅产出"可能存在Source点的函数列表"。

输入信息：

1. CVE分析结果：
{cve_analysis}

2. 相关Python文件路径：
{py_paths_str}

3. 工作目录：`{current_dir}`（所有路径均为相对该目录）。

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

行动指令（严格按照顺序执行）：
1. 理解CVE涉及的不可信输入来源类型（HTTP参数、头、Cookie、反序列化、文件/路径、环境变量、网络IO、数据库结果、表达式/模板等）。
2. 基于CodeQL查询结果识别候选函数，关注如下模式并给出理由与置信度（high/medium/low）：
   - Web框架请求参数取值（Flask/Django/FastAPI：request.args/request.form/Query/Body/Path 等）
   - 反序列化入口（pickle、yaml、json、xml 解析）
   - 文件系统/路径构造（open、os.path、pathlib 等）
   - 环境变量读取（os.environ、dotenv 等）
   - 网络/套接字/消息队列输入（socket、requests 响应等）
   - 模板/表达式执行（jinja2、eval/exec 等）

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "candidates": [
    {{
      "file_path": "相对路径（如 app/.../x.py）",
      "class_name": "类名（若为顶层函数则为空字符串）",
      "method_name": "函数名",
      "signature": "函数签名（含参数）",
      "start_line": 0,
      "end_line": 0,
      "reason": "为什么此函数可能是Source（关键API/取参点/框架绑定等）",
      "confidence": "high|medium|low"
    }}
  ]
}}

规则：
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必须优先基于CodeQL查询结果进行分析**，这是分析的核心依据
- 必要时可以使用server-filesystem读取文件内容进行补充验证
- 请确保输出为合法可解析的 JSON
- 确保分析结果与CodeQL查询结果的一致性
"""

    async def generate_source_codeql_query(self, cve_analysis: str) -> str:
        """Generate CodeQL query for source analysis (Python)."""
        try:
            requirement = f"""
            Based on the CVE analysis: {cve_analysis}

            Generate a CodeQL query to find potential source points in Python code that could receive untrusted input.
            Focus on:
            - Web framework request parameters and bodies (Flask/Django/FastAPI)
            - File system operations
            - Environment variables
            - Network input
            - Deserialization entry points (pickle/yaml/json/xml)
            - Template/expression evaluation (jinja2/eval/exec)

            The query should identify functions or methods that could be entry points for untrusted data.
            """
            result = await self.codeql_generator._arun(requirement, language="python")
            return result
        except Exception as e:
            return f"Error generating CodeQL query: {str(e)}"

    async def execute_source_codeql_query(self, query_content: str, database_path: str = None) -> str:
        """Execute CodeQL query for Python source analysis."""
        try:
            db_path = database_path or self.database_path
            result = await self.codeql_runner._arun(query_content, db_path, language="python")
            return result
        except Exception as e:
            return f"Error executing CodeQL query: {str(e)}"

    async def analyze_python_sources(self, cve_analysis: str) -> "AgentResult":
        """Analyze Python sources and identify possible Source points using CodeQL tools."""
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
                return AgentResult(content=json.dumps({"candidates": []}), success=True)

            codeql_query = await self.generate_source_codeql_query(cve_analysis)
            if codeql_query.startswith("Error"):
                from dataclasses import dataclass
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None
                return AgentResult(content=json.dumps({"candidates": []}), success=False, error=codeql_query)

            query_results = await self.execute_source_codeql_query(codeql_query)
            if query_results.startswith("Error"):
                from dataclasses import dataclass
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None
                return AgentResult(content=json.dumps({"candidates": []}), success=False, error=query_results)

            prompt = self.build_prompt_with_codeql_results(cve_analysis, py_paths, codeql_query, query_results)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            return AgentResult(content="", success=False, error=f"Unexpected error in Python source analysis: {str(e)}")

    def build_prompt_with_codeql_results(self, cve_analysis: str, py_paths: List[str], codeql_query: str, query_results: str) -> str:
        current_dir = os.getcwd()
        py_paths_str = "\n".join(py_paths)
        return f"""你是一名顶级的CodeQL安全研究员和Python代码审计专家，专注于识别可能的Source候选函数。

任务目标：基于提供的CVE信息、Python文件路径和CodeQL查询结果，仅产出"可能存在Source点的函数列表"。

输入信息：

1. CVE分析结果：
{cve_analysis}

2. 相关Python文件路径：
{py_paths_str}

3. 工作目录：`{current_dir}`（所有路径均为相对该目录）。

4. 生成的CodeQL查询：
{codeql_query}

5. CodeQL查询执行结果：
{query_results}

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

行动指令：
1. **重点分析CodeQL查询结果**，识别其中提到的潜在Source点
2. 结合CVE分析结果，理解不可信输入来源类型
3. 基于CodeQL结果和源码分析，识别候选函数
4. 关注如下模式并给出理由与置信度（high/medium/low）：
   - Web框架请求参数取值（Flask/Django/FastAPI：request.args/request.form/Query/Body/Path 等）
   - 反序列化入口（pickle、yaml、json、xml 解析）
   - 文件系统/路径构造（open、os.path、pathlib 等）
   - 环境变量读取（os.environ、dotenv 等）
   - 网络/套接字/消息队列输入
   - 模板/表达式执行（jinja2、eval/exec 等）

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "candidates": [
    {{
      "file_path": "相对路径（如 app/.../x.py）",
      "class_name": "类名（若为顶层函数则为空字符串）",
      "method_name": "函数名",
      "signature": "函数签名（含参数）",
      "start_line": 0,
      "end_line": 0,
      "reason": "为什么此函数可能是Source（关键API/取参点/框架绑定等）",
      "confidence": "high|medium|low"
    }}
  ]
}}

规则：
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必须优先基于CodeQL查询结果进行分析**，这是分析的核心依据
- 必要时可以使用server-filesystem读取文件内容进行补充验证
- 请确保输出为合法可解析的 JSON
- 确保分析结果与CodeQL查询结果的一致性
"""