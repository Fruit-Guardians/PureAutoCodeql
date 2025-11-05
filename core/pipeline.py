"""分析流水线模块

提供分析步骤的定义和流水线执行功能。
"""

import time
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, List, Optional

from core.context import AnalysisContext, AnalysisResult
from services.llm_service import MultiAgentAnalyzer

# 导入现有的Agent和工具
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.unified_sink_path_agent import UnifiedSinkPathAgent
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from tools.codeql_compose import CodeQLComposeTool


class AnalysisStep(ABC):
    """分析步骤抽象基类。"""

    def __init__(self, name: str):
        self.name = name

    @abstractmethod
    async def execute(self, context: AnalysisContext) -> Any:
        """执行分析步骤。"""
        pass


class CVEAnalysisStep(AnalysisStep):
    """CVE分析步骤。"""

    def __init__(self):
        super().__init__("cve_analysis")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CVE分析。"""
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        cve_agent = CVEAnalysisAgent(analyzer)
        intel_prompt = context.intel_bundle.prompt_block() if context.intel_bundle else None

        print("=== CVE Analysis ===")
        result = await cve_agent.analyze_cve(
            Path(context.cve_assets.json_path),
            intel_prompt=intel_prompt,
            show_thinking=context.show_thinking
        )

        if not result.success:
            print(f"CVE analysis failed: {result.error}")
        else:
            print(result.content)

        return result


class SinkAnalysisStep(AnalysisStep):
    """Sink路径分析步骤。"""

    def __init__(self):
        super().__init__("sink_analysis")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Sink路径分析。"""
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        sink_agent = UnifiedSinkPathAgent(analyzer, context.case_paths.source_code)

        print(f"=== {context.language.title()} Sink Path Analysis ===")
        result = await sink_agent.analyze_paths(
            context.language,
            context.get_result("cve_analysis").content if context.has_result("cve_analysis") else "",
            str(context.cve_assets.diff_path) if context.cve_assets.diff_path else "",
            show_thinking=context.show_thinking
        )

        if not result.success:
            print(f"{context.language.title()} sink analysis failed: {result.error}")
        else:
            print(result.content)

        return result


class SourceAnalysisStep(AnalysisStep):
    """Source分析步骤。"""

    def __init__(self):
        super().__init__("source_analysis")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Source分析。"""
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        source_agent = UnifiedSourceAnalysisAgent(
            analyzer,
            context.case_paths.source_code,
            str(context.case_paths.db)
        )

        print(f"=== {context.language.title()} Source Analysis ===")
        result = await source_agent.analyze_sources(
            context.language,
            context.get_result("sink_analysis").content if context.has_result("sink_analysis") else "",
            show_thinking=context.show_thinking
        )

        if not result.success:
            print(f"{context.language.title()} source analysis failed: {result.error}")
        else:
            print(result.content)

        return result


class CodeQLGenerationStep(AnalysisStep):
    """CodeQL查询生成步骤。"""

    def __init__(self):
        super().__init__("codeql_generation")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CodeQL查询生成。"""
        # 构建CodeQL生成需求
        codeql_requirement = f"""
        基于以下分析结果生成CodeQL查询：
        CVE路径分析结果：
        {context.get_result("cve_analysis").content if context.has_result("cve_analysis") else "CVE分析失败"}

        Sink路径分析结果：
        {context.get_result("sink_analysis").content if context.has_result("sink_analysis") else "Sink分析失败"}

        Source分析结果：
        {context.get_result("source_analysis").content if context.has_result("source_analysis") else "Source分析失败"}

        请基于上述分析生成一个完整的CodeQL查询，用于检测相关的安全漏洞。
        """

        # 使用推理模型
        from config import get_think_config
        codeql_analyzer = MultiAgentAnalyzer(get_think_config())
        await codeql_analyzer.initialize()

        codeql_tool = CodeQLComposeTool(
            analyzer=codeql_analyzer,
            database_path=str(context.case_paths.db),
            language=context.language,
        )

        print("=== CodeQL Query Generation ===")
        print("🔍 调用CodeQLComposeTool进行查询生成和语法检查...")
        compose_output = await codeql_tool._arun(codeql_requirement, show_thinking=context.show_thinking)
        print(compose_output)

        return compose_output


class AnalysisPipeline:
    """分析流水线，管理分析步骤的执行。"""

    def __init__(self, steps: List[AnalysisStep]):
        self.steps = steps

    @classmethod
    def create_default_pipeline(cls) -> "AnalysisPipeline":
        """创建默认的分析流水线。"""
        steps = [
            CVEAnalysisStep(),
            SinkAnalysisStep(),
            SourceAnalysisStep(),
            CodeQLGenerationStep(),
        ]
        return cls(steps)

    async def execute(self, context: AnalysisContext) -> AnalysisResult:
        """执行分析流水线。"""
        start_time = time.time()
        result = AnalysisResult(
            case_id=context.case_id,
            language=context.language
        )

        try:
            for step in self.steps:
                print(f"\n开始执行步骤: {step.name}")
                step_result = await step.execute(context)
                context.add_result(step.name, step_result)

                # 将结果映射到AnalysisResult
                if step.name == "cve_analysis":
                    result.cve_result = step_result
                elif step.name == "sink_analysis":
                    result.sink_result = step_result
                elif step.name == "source_analysis":
                    result.source_result = step_result
                elif step.name == "codeql_generation":
                    result.codeql_result = step_result

                # 检查步骤是否成功
                if hasattr(step_result, 'success') and not step_result.success:
                    result.success = False
                    result.error_message = f"步骤 {step.name} 失败: {step_result.error}"
                    break

        except Exception as e:
            result.success = False
            result.error_message = str(e)

        finally:
            result.execution_time = time.time() - start_time

        return result