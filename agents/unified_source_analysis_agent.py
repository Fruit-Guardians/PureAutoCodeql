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
from prompts.source_prompts import (
    build_source_analysis_with_sink_prompt,
    build_source_analysis_with_codeql_prompt
)

logger = logging.getLogger(__name__)


class UnifiedSourceAnalysisAgent:
    """统一的多语言Source分析器，支持Java、Python、C/C++代码分析。"""

    def __init__(
        self,
        analyzer: "MultiAgentAnalyzer",
        source_root: str = "src",
        database_path: str = None
    ):
        self.analyzer = analyzer
        self.source_root = source_root
        self.database_path = database_path
        self.codeql_composer = None

    def _get_language_config(self, language: str):
        """获取特定语言的配置信息。"""
        configs = {
            "java": {
                "file_patterns": ["*.java"],
                "database_path": "db-java",
                "language": "java",
                "source_keywords": ["getParameter", "getInputStream", "read", "parse", "deserialize"],
                "network_apis": ["HttpServletRequest", "Socket", "URLConnection"],
                "file_apis": ["FileInputStream", "BufferedReader", "Scanner"]
            },
            "python": {
                "file_patterns": ["*.py"],
                "database_path": "db-python",
                "language": "python",
                "source_keywords": ["input", "get", "post", "request", "read", "parse"],
                "network_apis": ["requests", "urllib", "socket", "flask", "django"],
                "file_apis": ["open", "read", "readlines", "with open"]
            },
            "cpp": {
                "file_patterns": ["*.c", "*.cc", "*.cpp", "*.cxx", "*.h", "*.hh", "*.hpp", "*.hxx"],
                "database_path": "db-cpp",
                "language": "cpp",
                "source_keywords": ["recv", "read", "scanf", "getenv", "fgets"],
                "network_apis": ["recv", "read", "accept", "SSL_read"],
                "file_apis": ["fread", "fgets", "getline", "read", "mmap"]
            }
        }
        
        if language not in configs:
            raise ValueError(f"不支持的语言: {language}")
        
        return configs[language]

    def find_source_files(self, language: str, directory: Path) -> List[str]:
        """返回源码目录的绝对路径，不收集具体文件。"""
        if not directory.exists():
            return []
        
        # 如果传入的是文件，返回其父目录
        if directory.is_file():
            logger.warning(
                f"source_root 指向文件而非目录: {directory}，将使用父目录: {directory.parent}"
            )
            return [str(directory.parent.resolve())]
        
        # 如果是目录，返回目录本身
        if directory.is_dir():
            return [str(directory.resolve())]
        
        # 其他情况（如符号链接等），尝试解析
        try:
            resolved = directory.resolve()
            if resolved.is_dir():
                return [str(resolved)]
            elif resolved.is_file():
                logger.warning(
                    f"source_root 解析后指向文件: {resolved}，将使用父目录: {resolved.parent}"
                )
                return [str(resolved.parent.resolve())]
        except Exception as e:
            logger.error(f"无法解析 source_root 路径 {directory}: {e}")
        
        return []

    def build_prompt(self, language: str, cve_analysis: str, sink_analysis: str, source_paths: List[str]) -> str:
        """构建基于sink分析结果的source分析提示词。"""
        config = self._get_language_config(language)
        current_dir = os.getcwd()
        
        # 获取文件扩展名
        file_extension = config['file_patterns'][0][1:] if config['file_patterns'] else 'ext'
        
        return build_source_analysis_with_sink_prompt(
            language=language,
            cve_analysis=cve_analysis,
            sink_analysis=sink_analysis,
            source_paths=source_paths,
            current_dir=current_dir,
            file_extension=file_extension
        )

    def build_prompt_with_codeql_results(
        self,
        language: str,
        cve_analysis: str,
        source_paths: List[str],
        codeql_query: str,
        query_results: str
    ) -> str:
        """构建包含CodeQL查询结果的提示词。"""
        config = self._get_language_config(language)
        current_dir = os.getcwd()
        
        # 获取文件扩展名
        file_extension = config['file_patterns'][0][1:] if config['file_patterns'] else 'ext'
        
        return build_source_analysis_with_codeql_prompt(
            language=language,
            cve_analysis=cve_analysis,
            source_paths=source_paths,
            current_dir=current_dir,
            codeql_query=codeql_query,
            query_results=query_results,
            file_extension=file_extension
        )

    async def generate_source_codeql_query(self, language: str, cve_analysis: str, show_thinking: bool = True):
        """生成针对特定语言源码发现的CodeQL查询，返回(ql, exec_output)。"""
        try:
            config = self._get_language_config(language)
            
            # 初始化CodeQL composer
            if not self.codeql_composer:
                self.codeql_composer = CodeQLComposeTool(
                    analyzer=self.analyzer,
                    database_path=self.database_path or config["database_path"],
                    language=config["language"],
                )
            
            requirement = f"""
            Based on the CVE analysis: {cve_analysis}

            Generate a CodeQL query to locate potential {language.upper()} source points that can receive untrusted input.
            Focus on:
            - Network input APIs ({', '.join(config['network_apis'])})
            - File and pipe reads ({', '.join(config['file_apis'])})
            - Environment variables and configuration readers
            - Unsafe input utilities
            - Deserialization or binary parsing entry points

            The query should target user-controlled data entry and output relevant function locations.
            """
            
            compose_output = await self.codeql_composer._arun(requirement, show_thinking=show_thinking)
            ql_code = None
            # 优先匹配标准格式：```ql
            block = re.search(r"```ql\s*\n(.*?)\n```", compose_output, re.DOTALL)
            if block:
                ql_code = block.group(1).strip()
            
            # 匹配带空格的格式：``` ql
            if not ql_code:
                block = re.search(r"```\s+ql\s*\n(.*?)\n```", compose_output, re.DOTALL)
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
            logger.error("Failed to generate %s source CodeQL query: %s", language, exc)
            return f"Error generating CodeQL query: {str(exc)}", ""

    async def analyze_sources(self, language: str, sink_analysis: str, show_thinking: bool = True, event_callback=None) -> "AgentResult":
        """统一的多语言source分析方法，基于sink分析结果查找source点。"""
        try:
            directory = Path(self.source_root)
            
            # 如果 source_root 是文件，使用其父目录
            if directory.exists() and directory.is_file():
                logger.warning(
                    f"source_root 是文件而非目录: {directory}，将使用父目录: {directory.parent}"
                )
                directory = directory.parent
            
            source_paths = self.find_source_files(language, directory)
            
            if not source_paths:
                from dataclasses import dataclass

                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(content=json.dumps({"candidates": []}), success=True)

            # 构建基于sink分析的提示词
            # 从sink_analysis中提取CVE信息（如果包含的话）
            cve_analysis = ""
            if "CVE" in sink_analysis:
                # 尝试从sink分析结果中提取CVE相关信息
                cve_analysis = sink_analysis
            
            prompt = self.build_prompt(
                language=language,
                cve_analysis=cve_analysis,
                sink_analysis=sink_analysis,
                source_paths=source_paths
            )
            
            return await self.analyzer.run_agent(prompt, show_thinking=show_thinking, event_callback=event_callback)
            
        except Exception as exc:
            logger.exception("Unexpected error in %s source analysis", language)
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(
                content="",
                success=False,
                error=f"Unexpected error in {language} source analysis: {str(exc)}"
            )