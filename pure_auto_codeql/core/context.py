"""分析上下文模块

提供分析过程中的上下文管理和结果构建。
"""

from dataclasses import dataclass, field
from typing import Any, Dict, Optional

from pure_auto_codeql.analysis_models import (
    AnalysisOutcome,
    Artifact,
    ErrorDetail,
    StepResult,
    StepStatus,
)
from pure_auto_codeql.services.path_selection import PathSelectionResult
from pure_auto_codeql.utils.case import CasePaths, CveAssets
from pure_auto_codeql.utils.intel import IntelBundle


@dataclass
class AnalysisContext:
    """分析上下文，包含分析过程中的所有数据。"""

    # 基础信息
    case_id: str
    case_paths: CasePaths
    cve_assets: CveAssets
    language: str

    # 情报数据
    intel_bundle: Optional[IntelBundle] = None

    # 分析结果存储
    _results: Dict[str, Any] = field(default_factory=dict)

    # 额外数据存储（用于存储验证查询等临时数据）
    data: Dict[str, Any] = field(default_factory=dict)

    # 配置选项
    show_thinking: bool = False

    # Phase 3: 事件回调
    event_callback: Optional[Any] = None

    def add_result(self, step_name: str, result: Any) -> None:
        """添加分析步骤的结果。"""
        self._results[step_name] = result

    def get_result(self, step_name: str) -> Any:
        """获取指定步骤的结果。"""
        return self._results.get(step_name)

    def has_result(self, step_name: str) -> bool:
        """检查是否有指定步骤的结果。"""
        return step_name in self._results


@dataclass
class AnalysisResult:
    """分析结果，包含所有分析步骤的输出。"""

    case_id: str
    language: str

    # 各个分析步骤的结果
    cve_result: Optional[StepResult] = None
    sink_result: Optional[StepResult] = None
    source_result: Optional[StepResult] = None
    path_analysis_result: Optional[StepResult] = None
    codeql_result: Optional[StepResult] = None
    codeql_execution_result: Optional[Any] = None
    path_selection_result: Optional[PathSelectionResult] = None

    # 执行信息
    success: bool = True
    error_message: Optional[str] = None
    error_detail: Optional[ErrorDetail] = None
    outcome: AnalysisOutcome = AnalysisOutcome.RUNNING
    step_results: Dict[str, StepResult] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)
    artifacts: list[Artifact] = field(default_factory=list)
    execution_time: Optional[float] = None

    # 输出目录信息
    output_directory: Optional[str] = None

    def is_complete(self) -> bool:
        """检查分析是否完整完成。"""
        return self.outcome in {
            AnalysisOutcome.COMPLETED_WITH_FINDINGS,
            AnalysisOutcome.COMPLETED_NO_FINDINGS,
        }

    def finalize_outcome(self) -> AnalysisOutcome:
        statuses = [step.status for step in self.step_results.values()]
        if any(status == StepStatus.TIMED_OUT for status in statuses):
            self.outcome = AnalysisOutcome.TIMED_OUT
        elif any(status == StepStatus.CANCELLED for status in statuses):
            self.outcome = AnalysisOutcome.CANCELLED
        elif not self.success or any(status == StepStatus.FAILED for status in statuses):
            self.outcome = (
                AnalysisOutcome.PARTIAL
                if any(status == StepStatus.SUCCEEDED for status in statuses)
                else AnalysisOutcome.FAILED
            )
        elif not statuses:
            self.outcome = AnalysisOutcome.PARTIAL
        else:
            selected = getattr(self.path_selection_result, "selected_paths", None)
            findings_count = 0
            if isinstance(self.codeql_execution_result, dict):
                findings_count = int(
                    self.codeql_execution_result.get("findings_count")
                    or self.codeql_execution_result.get("paths_count")
                    or 0
                )
            self.outcome = (
                AnalysisOutcome.COMPLETED_WITH_FINDINGS
                if selected or findings_count > 0
                else AnalysisOutcome.COMPLETED_NO_FINDINGS
            )
        return self.outcome

    def get_summary(self) -> str:
        """获取分析结果摘要。"""
        status = "✅ 成功" if self.success and self.is_complete() else "❌ 失败/不完整"
        execution_time_str = f"{self.execution_time:.2f}秒" if self.execution_time else "未记录"
        summary = f"""
案例分析摘要:
- 案例ID: {self.case_id}
- 编程语言: {self.language}
- 分析状态: {status}
- 执行时间: {execution_time_str}
"""
        if self.error_message:
            summary += f"- 错误信息: {self.error_message}\n"
        if self.output_directory:
            summary += f"- 输出目录: {self.output_directory}\n"

        return summary


@dataclass
class AnalysisConfig:
    """分析配置"""

    # LLM配置
    llm_config: Optional[Any] = None
    language: Optional[str] = None
    llm_provider: Optional[str] = None  # 模型提供商名称（deepseek/siliconflow/zhipu），如果指定则覆盖环境变量
    think_model: Optional[str] = None  # 推理模型名称，如果指定则覆盖默认模型
    chat_model: Optional[str] = None  # 对话模型名称，如果指定则覆盖默认模型
    api_key: Optional[str] = None  # API Key，如果指定则覆盖环境变量
    base_url: Optional[str] = None  # Base URL，如果指定则覆盖环境变量

    # LSP配置
    lsp_enabled: bool = True
    lsp_pack_root: Optional[str] = None

    # 输出配置
    output_file: Optional[str] = None  # 如果指定，写入该文件；否则使用时间戳目录
    output_base_dir: str = "output"  # 输出基础目录
    output_encoding: str = "utf-8"  # 输出文件编码
    keep_output_dirs: int = 10  # 保留的输出目录数量（0表示不清理）

    # 执行配置
    show_thinking: bool = False
    refresh_intel: bool = False
    requirement: Optional[str] = None
    max_codeql_rounds: int = 5
    max_total_llm_calls: int = 20
    task_timeout: int = 3600

    # 流水线步骤开关
    enable_cve_analysis: bool = True
    enable_sink_analysis: bool = True
    enable_source_analysis: bool = True
    enable_path_analysis: bool = True
    enable_codeql_generation: bool = True
    enable_path_selection: bool = True

    # 路径配置
    json_path: Optional[str] = None
    diff_path: Optional[str] = None
    source_root: Optional[str] = None
    db_path: Optional[str] = None

    # Phase 3: 事件回调
    event_callback: Optional[Any] = None

    # 实验性功能
    enable_error_tidy: bool = False
    enable_source_sink_fallback: bool = False
    fallback_empty_retry_max: int = 5
    enable_breakpoint_recovery: bool = False
    enable_template_refinement: bool = False

    # Sink/Source 验证配置
    enable_sink_source_verification: bool = False
    verification_retry_max: int = 3
    verification_timeout: int = 30

    def validate(self) -> None:
        if self.max_codeql_rounds < 1:
            raise ValueError("max_codeql_rounds must be at least 1")
        if self.max_total_llm_calls < 1:
            raise ValueError("max_total_llm_calls must be at least 1")
        if self.task_timeout < 1:
            raise ValueError("task_timeout must be at least 1 second")
        if self.enable_source_analysis and not self.enable_sink_analysis:
            raise ValueError("source analysis requires sink analysis")
        if self.enable_path_analysis and not self.enable_source_analysis:
            raise ValueError("path analysis requires source analysis")
        if self.enable_path_selection and not self.enable_codeql_generation:
            raise ValueError("path selection requires CodeQL generation")
