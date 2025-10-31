import os
import json
import logging
import re
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

from tools.codeql_compose import CodeQLComposeTool

logger = logging.getLogger(__name__)


class CSourceAnalysisAgent:
    """使用CodeQL工具识别C/C++代码库中的潜在Source点。"""

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        source_root: str = "src",
        database_path: str = "db-cpp"
    ):
        self.analyzer = analyzer
        self.source_root = source_root
        self.database_path = database_path
        self.codeql_composer = CodeQLComposeTool(
            analyzer=analyzer,
            database_path=database_path,
            language="cpp",
        )

    def find_c_files(self, directory: Path) -> List[str]:
        """收集给定目录下的C/C++源文件。"""
        patterns = ("*.c", "*.cc", "*.cpp", "*.cxx", "*.h", "*.hh", "*.hpp", "*.hxx")
        collected = set()
        if directory.exists():
            for pattern in patterns:
                for path in directory.rglob(pattern):
                    try:
                        rel_path = os.path.relpath(path, start=os.getcwd())
                        collected.add(rel_path)
                    except Exception:
                        collected.add(str(path))
        return sorted(collected)

    def build_prompt(self, cve_analysis: str, c_paths: List[str]) -> str:
        current_dir = os.getcwd()
        c_paths_str = "\n".join(c_paths)
        return f"""你是一名资深的 CodeQL 安全研究员与 C/C++ 代码审计专家，专注识别可能的 Source 候选函数。
任务目标：基于提供的 CVE 信息与 C/C++ 文件路径，使用工具进行分析，仅产出“可能存在 Source 点的函数列表”。

输入信息：

1. CVE 分析结果：
{cve_analysis}

2. 相关 C/C++ 文件路径：
{c_paths_str}

3. 工作目录：`{current_dir}`（所有路径均相对于该目录）。

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

行动指令（严格按照顺序执行）：
1. 理解不可信输入来源类型：网络/套接字、文件读取、管道/IPC、环境变量、标准输入、反序列化/二进制解析等。
2. 结合路径列表，聚焦可能接收用户控制数据的函数或方法（例如 recv/read/SSL_read、fgets/fread/scanf、getenv、mmap、自定义解析器等）。
3. 给出每个候选的理由与置信度（high/medium/low）。

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "candidates": [
    {{
      "file_path": "相对路径（如 src/.../x.c）",
      "function_name": "函数/方法名",
      "signature": "函数签名（含参数与类型）",
      "start_line": 0,
      "end_line": 0,
      "reason": "为何此处可能是 Source（用户控制输入、解析入口等）",
      "confidence": "high|medium|low"
    }}
  ]
}}

规则：
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必要时可以使用 server-filesystem 读取文件内容进行补充验证**。
- 请确保输出为合法可解析的 JSON。
- 结果应与源码实际位置一致。
"""

    def build_prompt_with_codeql_results(
        self,
        cve_analysis: str,
        c_paths: List[str],
        codeql_query: str,
        query_results: str
    ) -> str:
        current_dir = os.getcwd()
        c_paths_str = "\n".join(c_paths)
        return f"""你是一名资深的 CodeQL 安全研究员与 C/C++ 代码审计专家，专注识别可能的 Source 候选函数。

任务目标：基于提供的 CVE 信息、C/C++ 文件路径和 CodeQL 查询结果，仅产出“可能存在 Source 点的函数列表”。

输入信息：

1. CVE 分析结果：
{cve_analysis}

2. 相关 C/C++ 文件路径：
{c_paths_str}

3. 工作目录：`{current_dir}`（所有路径均相对于该目录）。

4. 生成的 CodeQL 查询：
{codeql_query}

5. CodeQL 查询执行结果：
{query_results}

可用工具：
- server-filesystem：读取文件内容
- sequential-thinking：多步骤推理

行动指令：
1. **优先分析 CodeQL 查询执行结果**，识别其中提到的潜在 Source 点。
2. 结合 CVE 分析结果，理解可能的输入来源与触发条件。
3. 基于 CodeQL 结果与源码分析，给出候选函数。关注如下模式并给出理由与置信度（high/medium/low）：
   - 网络/套接字输入（recv/read/accept/SSL_read 等）
   - 文件系统/管道读取（fgets/fread/getline/read/mmap 等）
   - 环境变量读取（getenv/secure_getenv 等）
   - 不安全输入工具（scanf/gets/自定义封装等）
   - 反序列化/二进制解析入口

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "candidates": [
    {{
      "file_path": "相对路径（如 src/.../x.c）",
      "function_name": "函数/方法名",
      "signature": "函数签名（含参数与类型）",
      "start_line": 0,
      "end_line": 0,
      "reason": "为何此处可能是 Source（依据 CodeQL 结果与源码位置）",
      "confidence": "high|medium|low"
    }}
  ]
}}

规则：
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必须优先依据 CodeQL 查询结果进行分析**。
- 必要时可以使用 server-filesystem 读取文件内容进行补充验证。
- 请确保输出为合法可解析的 JSON，并与查询结果一致。
"""

    async def generate_source_codeql_query(self, cve_analysis: str):
        """生成针对C/C++源码发现的CodeQL查询，返回(ql, exec_output)。"""
        try:
            requirement = f"""
            Based on the CVE analysis: {cve_analysis}

            Generate a CodeQL query to locate potential C/C++ source points that can receive untrusted input.
            Focus on:
            - Network input APIs (recv, read, SSL_read, accept, CGI request handlers)
            - File and pipe reads (fgets, fread, getline, read, mmap)
            - Environment variables and configuration readers (getenv, secure_getenv)
            - Unsafe input utilities (scanf, gets, custom wrappers)
            - Deserialization or binary parsing entry points

            The query should target user-controlled data entry and output relevant function locations.
            """
            compose_output = await self.codeql_composer._arun(requirement)
            ql_code = None
            block = re.search(r"```ql\s*\n(.*?)\n```", compose_output, re.DOTALL)
            if block:
                ql_code = block.group(1).strip()
            if not ql_code:
                tag = re.search(r"<codeql>(.*?)</codeql>", compose_output, re.DOTALL)
                if tag:
                    ql_code = tag.group(1).strip()
            if not ql_code:
                return "Error: Could not extract CodeQL code from compose result", compose_output
            return ql_code, compose_output
        except Exception as exc:
            logger.error("Failed to generate C/C++ source CodeQL query: %s", exc)
            return f"Error generating CodeQL query: {str(exc)}", ""

    async def execute_source_codeql_query(self, query_content: str, database_path: str = None) -> str:
        """对配置的C/C++数据库执行生成的CodeQL查询（已由Compose执行，保留兼容）。"""
        try:
            return "Execution handled by CodeQLComposeTool during generation."
        except Exception as exc:
            logger.error("Failed to execute C/C++ CodeQL query: %s", exc)
            return f"Error executing CodeQL query: {str(exc)}"

    async def analyze_c_sources(self, cve_analysis: str) -> "AgentResult":
        """Orchestrate C/C++ source analysis using CodeQL generation and execution."""
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

                return AgentResult(content=json.dumps({"candidates": []}), success=True)

            codeql_query, compose_exec_output = await self.generate_source_codeql_query(cve_analysis)
            if isinstance(codeql_query, str) and codeql_query.startswith("Error"):
                from dataclasses import dataclass

                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(
                    content=json.dumps({"candidates": []}),
                    success=False,
                    error=codeql_query
                )

            query_results = compose_exec_output

            prompt = self.build_prompt_with_codeql_results(
                cve_analysis,
                c_paths,
                codeql_query,
                query_results
            )
            return await self.analyzer.run_agent(prompt)
        except Exception as exc:
            logger.exception("Unexpected error in C/C++ source analysis")
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(
                content="",
                success=False,
                error=f"Unexpected error in C/C++ source analysis: {str(exc)}"
            )
