from pathlib import Path
from typing import List, Optional, TYPE_CHECKING
import os
import json
import logging

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
from tools.codeql_generator_tool import CodeQLGeneratorTool
from tools.codeql_runner_tool import CodeQLRunnerTool

# Configure logging for CodeQL tool integration
logger = logging.getLogger(__name__)


class JavaSourceAnalysisAgent:

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        source_root: str = "h5-vsan-service.jar_Decompiler.com",
        database_path: Optional[str] = None,
    ):
        self.analyzer = analyzer
        self.source_root = source_root
        # Initialize CodeQL tools
        self.codeql_generator = CodeQLGeneratorTool(analyzer=analyzer)
        self.codeql_runner = CodeQLRunnerTool()
        self.database_path = database_path
    
    def build_prompt(self, cve_analysis: str, java_paths: List[str]) -> str:
        """Build a simplified prompt for CodeQL-based analysis."""
        current_dir = os.getcwd()
        java_paths_str = "\n".join(java_paths)

        return (
            f"""你是一名顶级的CodeQL安全研究员和Java代码审计专家，专注于识别可能的Source候选函数。

    任务目标：基于提供的CVE信息和Java文件路径，自主使用CodeQL工具进行分析，仅产出"可能存在Source点的函数列表"。

    输入信息：

    1. CVE分析结果：
    {cve_analysis}

    2. 相关Java文件路径：
    {java_paths}

    3. 工作目录：`{current_dir}`（所有路径均为相对该目录）。

    可用工具：
    - codeql_generator：生成CodeQL查询（必须使用）
    - codeql_runner：执行CodeQL查询（必须使用）
    - server-filesystem：读取文件内容
    - sequential-thinking：多步骤推理

    行动指令（严格按照顺序执行）：
    1. 理解CVE涉及的不可信输入来源类型（HTTP参数、头、Cookie、反序列化、文件/路径、环境变量、网络IO、数据库结果、表达式/模板等）。
    2. **必须使用codeql_generator工具**生成针对Source点识别的CodeQL查询
    3. **必须使用codeql_runner工具**执行查询并获取结果
    4. 基于CodeQL查询结果识别候选函数，关注如下模式并给出理由与置信度（high/medium/low）：
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
    - **必须优先使用CodeQL工具进行分析**，这是分析的核心步骤
    - 必要时可以使用server-filesystem读取文件内容进行补充分析
    - 请确保输出为合法可解析的 JSON
    - 确保CodeQL工具调用的完整性和准确性
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
    
    async def generate_source_codeql_query(self, cve_analysis: str) -> str:
        """Generate CodeQL query for source analysis based on CVE information."""
        logger.info("Starting CodeQL query generation for source analysis")
        try:
            # Create a requirement description for source analysis
            requirement = f"""
            Based on the CVE analysis: {cve_analysis}
            
            Generate a CodeQL query to find potential source points in Java code that could receive untrusted input.
            Focus on:
            - HTTP request parameters and headers
            - File system operations
            - Environment variables
            - Network input
            - Deserialization entry points
            - Database query results
            - Template/expression evaluation
            
            The query should identify methods that could be entry points for untrusted data.
            """
            
            logger.debug(f"CodeQL generation requirement: {requirement[:200]}...")
            result = await self.codeql_generator._arun(requirement)
            logger.info("CodeQL query generation completed successfully")
            return result
        except Exception as e:
            error_msg = f"Error generating CodeQL query: {str(e)}"
            logger.error(error_msg)
            return error_msg
    
    async def execute_source_codeql_query(
        self,
        query_content: str,
        database_path: Optional[str] = None,
    ) -> str:
        """Execute CodeQL query for source analysis."""
        resolved_db = database_path or self.database_path or "h5-vsan/db-java"
        logger.info(f"Starting CodeQL query execution on database: {resolved_db}")
        try:
            logger.debug(f"CodeQL query content: {query_content[:200]}...")
            result = await self.codeql_runner._arun(query_content, resolved_db)
            logger.info("CodeQL query execution completed successfully")
            return result
        except Exception as e:
            error_msg = f"Error executing CodeQL query: {str(e)}"
            logger.error(error_msg)
            return error_msg
    
    async def analyze_java_sources(self, cve_analysis: str) -> "AgentResult":
        """Analyze Java sources and identify possible Source points using CodeQL tools."""
        logger.info("Starting Java source analysis with CodeQL tools")
        try:
            directory = Path(self.source_root)
            java_paths = self.find_java_files(directory)
            logger.info(f"Found {len(java_paths)} Java files for analysis")
            
            if not java_paths:
                logger.warning("No Java files found for analysis")
                from dataclasses import dataclass
                
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None
                
                return AgentResult(
                    content=json.dumps({"candidates": []}),
                    success=True
                )
            
            # Generate CodeQL query for source analysis
            logger.info("Generating CodeQL query for source analysis")
            codeql_query = await self.generate_source_codeql_query(cve_analysis)
            
            # Check if query generation was successful
            if codeql_query.startswith("Error"):
                logger.error(f"CodeQL query generation failed: {codeql_query}")
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
            
            # Execute the CodeQL query
            logger.info("Executing CodeQL query")
            query_results = await self.execute_source_codeql_query(codeql_query)
            
            # Check if query execution was successful
            if query_results.startswith("Error"):
                logger.error(f"CodeQL query execution failed: {query_results}")
                from dataclasses import dataclass
                
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None
                
                return AgentResult(
                    content=json.dumps({"candidates": []}),
                    success=False,
                    error=query_results
                )
            
            # Build prompt with CodeQL results
            logger.info("Building analysis prompt with CodeQL results")
            prompt = self.build_prompt_with_codeql_results(cve_analysis, java_paths, codeql_query, query_results)
            
            logger.info("Running agent analysis with CodeQL results")
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            error_msg = f"Unexpected error in source analysis: {str(e)}"
            logger.error(error_msg)
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=error_msg)
    
    def build_prompt_with_codeql_results(self, cve_analysis: str, java_paths: List[str], codeql_query: str, query_results: str) -> str:
        """Build prompt that includes CodeQL query and results."""
        current_dir = os.getcwd()
        java_paths_str = "\n".join(java_paths)

        return (
            f"""你是一名顶级的CodeQL安全研究员和Java代码审计专家，专注于识别可能的Source候选函数。

    任务目标：基于提供的CVE信息、Java文件路径和CodeQL查询结果，仅产出"可能存在Source点的函数列表"。

    输入信息：

    1. CVE分析结果：
    {cve_analysis}

    2. 相关Java文件路径：
    {java_paths}

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
    - **必须优先基于CodeQL查询结果进行分析**，这是分析的核心依据
    - 必要时可以使用server-filesystem读取文件内容进行补充验证
    - 请确保输出为合法可解析的 JSON
    - 确保分析结果与CodeQL查询结果的一致性
    """
        )
