"""分析流水线模块

提供分析步骤的定义和流水线执行功能。
"""

import logging
import time
import json
import shutil
from datetime import datetime
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, List, Optional

from core.context import AnalysisContext, AnalysisConfig, AnalysisResult
from services.llm_service import AgentResult, MultiAgentAnalyzer

# 配置日志
logger = logging.getLogger(__name__)

# 导入现有的Agent和工具
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.unified_sink_path_agent import UnifiedSinkPathAgent
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from tools.codeql_compose import CodeQLComposeTool


class AnalysisStep(ABC):
    """分析步骤抽象基类。"""

    def __init__(self, name: str, agent_name: str = None):
        self.name = name
        self.agent_name = agent_name or name

    @abstractmethod
    async def execute(self, context: AnalysisContext) -> Any:
        """执行分析步骤。"""
        pass


class CVEAnalysisStep(AnalysisStep):
    """CVE分析步骤。"""

    def __init__(self):
        super().__init__("cve_analysis", agent_name="CVE Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行CVE分析。"""
        # 从配置中获取提供商信息
        provider = getattr(context, '_config', None) and context._config.llm_provider
        if provider:
            from config import get_llm_config_by_provider, LLMRole
            llm_config = get_llm_config_by_provider(provider, LLMRole.CHAT)
            analyzer = MultiAgentAnalyzer(llm_config)
        else:
            analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        cve_agent = CVEAnalysisAgent(analyzer)
        intel_prompt = context.intel_bundle.prompt_block() if context.intel_bundle else None

        print("=== CVE Analysis ===")
        result = await cve_agent.analyze_cve(
            Path(context.cve_assets.json_path),
            intel_prompt=intel_prompt,
            show_thinking=context.show_thinking,
            event_callback=context.event_callback,
            agent_name=self.agent_name,
            agent_type=self.name
        )

        if not result.success:
            print(f"CVE analysis failed: {result.error}")
        elif not context.show_thinking:
            # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
            print(result.content)

        return result


class SinkAnalysisStep(AnalysisStep):
    """Sink路径分析步骤。"""

    def __init__(self):
        super().__init__("sink_analysis", agent_name="Sink Path Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Sink路径分析。"""
        # 从配置中获取提供商信息
        provider = getattr(context, '_config', None) and context._config.llm_provider
        if provider:
            from config import get_llm_config_by_provider, LLMRole
            llm_config = get_llm_config_by_provider(provider, LLMRole.CHAT)
            analyzer = MultiAgentAnalyzer(llm_config)
        else:
            analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        sink_agent = UnifiedSinkPathAgent(analyzer, context.case_paths.source_code)

        print(f"=== {context.language.title()} Sink Path Analysis ===")
        result = await sink_agent.analyze_paths(
            context.language,
            context.get_result("cve_analysis").content if context.has_result("cve_analysis") else "",
            str(context.cve_assets.diff_path) if context.cve_assets.diff_path else "",
            show_thinking=context.show_thinking,
            event_callback=context.event_callback,
            agent_name=self.agent_name,
            agent_type=self.name
        )

        if not result.success:
            print(f"{context.language.title()} sink analysis failed: {result.error}")
        elif not context.show_thinking:
            # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
            print(result.content)

        return result


class SourceAnalysisStep(AnalysisStep):
    """Source分析步骤。"""

    def __init__(self):
        super().__init__("source_analysis", agent_name="Source Analysis Agent")

    async def execute(self, context: AnalysisContext) -> Any:
        """执行Source分析。"""
        # 从配置中获取提供商信息
        provider = getattr(context, '_config', None) and context._config.llm_provider
        if provider:
            from config import get_llm_config_by_provider, LLMRole
            llm_config = get_llm_config_by_provider(provider, LLMRole.CHAT)
            analyzer = MultiAgentAnalyzer(llm_config)
        else:
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
            show_thinking=context.show_thinking,
            event_callback=context.event_callback,
            agent_name=self.agent_name,
            agent_type=self.name
        )

        if not result.success:
            print(f"{context.language.title()} source analysis failed: {result.error}")
        elif not context.show_thinking:
            # 只在未开启思考过程时打印结果（开启时已在流式输出中显示）
            print(result.content)

        return result


class CodeQLGenerationStep(AnalysisStep):
    """CodeQL查询生成步骤。"""

    def __init__(self):
        super().__init__("codeql_generation", agent_name="CodeQL Generation Agent")

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
        # 从配置中获取提供商信息
        provider = getattr(context, '_config', None) and context._config.llm_provider
        if provider:
            from config import get_llm_config_by_provider, LLMRole
            think_config = get_llm_config_by_provider(provider, LLMRole.THINK)
            codeql_analyzer = MultiAgentAnalyzer(think_config)
        else:
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
        compose_output = await codeql_tool._arun(
            codeql_requirement, 
            show_thinking=context.show_thinking, 
            event_callback=context.event_callback,
            agent_name=self.agent_name,
            agent_type=self.name
        )
        print(compose_output)

        # Normalize output into AgentResult for downstream consumers
        if isinstance(compose_output, AgentResult):
            return compose_output

        if isinstance(compose_output, str):
            normalized = compose_output.strip().lower()
            if normalized.startswith("error") or normalized.startswith("failed"):
                return AgentResult(content="", success=False, error=compose_output)

            return AgentResult(content=compose_output, success=True)

        # Fallback: treat unexpected payload as failure with repr for debugging
        return AgentResult(
            content="",
            success=False,
            error=f"Unexpected CodeQL generation output type: {type(compose_output).__name__}"
        )


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

    async def execute(self, context: AnalysisContext, config: Optional[AnalysisConfig] = None) -> AnalysisResult:
        """执行分析流水线。"""
        start_time = time.time()
        result = AnalysisResult(
            case_id=context.case_id,
            language=context.language
        )
        config = config or AnalysisConfig()
        
        # 将配置存储到上下文中，供步骤使用
        context._config = config

        try:
            for step in self.steps:
                logger.info(f"开始执行步骤: {step.name}")
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
                    logger.error(f"步骤 {step.name} 执行失败: {step_result.error}")
                    break

        except Exception as e:
            result.success = False
            result.error_message = str(e)
            logger.exception(f"分析流水线执行异常: {e}")

        finally:
            result.execution_time = time.time() - start_time
            
            # 整合输出文件到统一文件夹
            await self._consolidate_output_files(context, result, config)

        return result

    async def _consolidate_output_files(
        self, 
        context: AnalysisContext, 
        result: AnalysisResult,
        config: AnalysisConfig
    ) -> None:
        """整合所有输出文件到统一的文件夹结构。"""
        try:
            from utils.io import write_analysis_output
            
            # 确定输出路径
            if config.output_file:
                # 用户指定了输出文件，直接写入该文件
                output_path = Path(config.output_file)
                output_path.parent.mkdir(parents=True, exist_ok=True)
                output_dir = output_path.parent
                
                logger.info(f"写入输出文件: {output_path}")
                write_analysis_output(
                    result.cve_result,
                    result.sink_result,
                    result.source_result,
                    output_path=output_path,
                    codeql_result=result.codeql_result,
                    codeql_execution_result=result.codeql_execution_result,
                    language=result.language,
                    intel_bundle=context.intel_bundle,
                    encoding=config.output_encoding,
                )
                result.output_directory = str(output_dir)
                logger.info(f"✅ 分析结果已保存到: {output_path}")
            else:
                # 使用时间戳目录
                output_base = Path(config.output_base_dir)
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                output_dir = output_base / f'analysis_output_{timestamp}'
                output_dir.mkdir(parents=True, exist_ok=True)
                
                logger.info(f"创建输出目录: {output_dir}")
                
                # 生成output.md文件
                output_path = output_dir / 'output.md'
                write_analysis_output(
                    result.cve_result,
                    result.sink_result,
                    result.source_result,
                    output_path=output_path,
                    codeql_result=result.codeql_result,
                    codeql_execution_result=result.codeql_execution_result,
                    language=result.language,
                    intel_bundle=context.intel_bundle,
                    encoding=config.output_encoding,
                )
                
                # 处理SARIF文件
                await self._process_sarif_files(output_base, output_dir, config)
                
                # 清理旧输出目录
                if config.keep_output_dirs > 0:
                    self._cleanup_old_output_dirs(output_base, config.keep_output_dirs)
                
                # 更新结果中的输出目录信息
                result.output_directory = str(output_dir)
                logger.info(f"✅ 分析结果已整合到文件夹: {output_dir}")
                
        except PermissionError as e:
            error_msg = f"文件权限错误，无法写入输出文件: {e}"
            logger.error(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"
        except OSError as e:
            error_msg = f"文件系统错误，无法写入输出文件: {e}"
            logger.error(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"
        except Exception as e:
            error_msg = f"输出文件整合失败: {e}"
            logger.exception(error_msg)
            result.error_message = (result.error_message or "") + f"\n{error_msg}"
    
    async def _process_sarif_files(
        self, 
        output_base: Path, 
        output_dir: Path, 
        config: AnalysisConfig
    ) -> None:
        """处理SARIF文件：复制、转换、删除。"""
        try:
            sarif_files = list(output_base.glob('result_*.sarif')) if output_base.exists() else []
            if not sarif_files:
                logger.debug("未找到SARIF文件")
                return
            
            latest_sarif = max(sarif_files, key=lambda x: x.stat().st_mtime)
            sarif_filename = latest_sarif.name
            target_sarif = output_dir / sarif_filename
            
            # 先复制文件
            shutil.copy2(latest_sarif, target_sarif)
            logger.info(f"已复制SARIF文件: {sarif_filename}")
            
            # 转换SARIF为JSON（在复制成功后）
            try:
                from utils.sarif_utils import sarif_to_all_paths
                
                with open(target_sarif, 'r', encoding='utf-8') as f:
                    sarif_data = json.load(f)
                
                json_data = sarif_to_all_paths(sarif_data)
                json_filename = latest_sarif.stem + '.json'
                json_path = output_dir / json_filename
                
                with open(json_path, 'w', encoding=config.output_encoding) as f:
                    json.dump(json_data, f, ensure_ascii=False, indent=2)
                
                # 验证JSON文件写入成功
                if json_path.exists() and json_path.stat().st_size > 0:
                    logger.info(f"✅ SARIF文件已转换为JSON: {json_filename}")
                else:
                    logger.warning(f"⚠️ JSON文件写入可能失败: {json_filename}")
                    
            except Exception as e:
                logger.warning(f"SARIF转JSON失败: {e}，但SARIF文件已保存")
            
            # 所有处理完成后再删除原文件
            try:
                latest_sarif.unlink()
                logger.debug(f"已删除原始SARIF文件: {latest_sarif}")
            except Exception as e:
                logger.warning(f"删除原始SARIF文件失败: {e}，文件已保留")
                
        except Exception as e:
            logger.exception(f"处理SARIF文件时出错: {e}")
    
    def _cleanup_old_output_dirs(self, output_base: Path, keep_count: int) -> None:
        """清理旧的输出目录，只保留最近的N个。"""
        try:
            if not output_base.exists():
                return
            
            # 查找所有输出目录
            output_dirs = [
                d for d in output_base.iterdir()
                if d.is_dir() and d.name.startswith('analysis_output_')
            ]
            
            if len(output_dirs) <= keep_count:
                return
            
            # 按修改时间排序，删除最旧的
            sorted_dirs = sorted(output_dirs, key=lambda x: x.stat().st_mtime, reverse=True)
            dirs_to_remove = sorted_dirs[keep_count:]
            
            for old_dir in dirs_to_remove:
                try:
                    shutil.rmtree(old_dir)
                    logger.info(f"已清理旧输出目录: {old_dir.name}")
                except Exception as e:
                    logger.warning(f"清理目录失败 {old_dir.name}: {e}")
                    
        except Exception as e:
            logger.warning(f"清理旧输出目录时出错: {e}")