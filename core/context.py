"""分析上下文模块

提供分析过程中的上下文管理和结果构建。
"""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Optional

from services.llm_service import AgentResult
from utils.case import CasePaths, CveAssets
from utils.intel import IntelBundle


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

    # 配置选项
    show_thinking: bool = False

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
    cve_result: Optional[AgentResult] = None
    sink_result: Optional[AgentResult] = None
    source_result: Optional[AgentResult] = None
    codeql_result: Optional[AgentResult] = None
    codeql_execution_result: Optional[Any] = None

    # 执行信息
    success: bool = True
    error_message: Optional[str] = None
    execution_time: Optional[float] = None
    
    # 输出目录信息
    output_directory: Optional[str] = None

    def is_complete(self) -> bool:
        """检查分析是否完整完成。"""
        return all([
            self.cve_result and self.cve_result.success,
            self.sink_result and self.sink_result.success,
            self.source_result and self.source_result.success,
            self.codeql_result and self.codeql_result.success
        ])

    def get_summary(self) -> str:
        """获取分析结果摘要。"""
        status = "✅ 成功" if self.success and self.is_complete() else "❌ 失败/不完整"
        summary = f"""
案例分析摘要:
- 案例ID: {self.case_id}
- 编程语言: {self.language}
- 分析状态: {status}
- 执行时间: {self.execution_time:.2f}秒 (如果有)
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

    # LSP配置
    lsp_enabled: bool = True
    lsp_pack_root: Optional[str] = None

    # 输出配置
    output_file: str = "output.md"

    # 执行配置
    show_thinking: bool = False
    refresh_intel: bool = False

    # 路径配置
    json_path: Optional[str] = None
    diff_path: Optional[str] = None
    source_root: Optional[str] = None
    db_path: Optional[str] = None
