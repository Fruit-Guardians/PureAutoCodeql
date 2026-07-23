"""API数据模型定义"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ProjectInfo(BaseModel):
    """项目基本信息"""

    case_id: str = Field(..., description="项目案例ID")
    path: str = Field(..., description="项目路径")
    description: Optional[str] = Field(None, description="项目描述")
    exists: bool = Field(..., description="项目是否存在")
    has_database: bool = Field(False, description="是否包含CodeQL数据库")
    has_source: bool = Field(False, description="是否包含源代码")
    languages: List[str] = Field(default_factory=list, description="检测到的编程语言")


class ProjectDetail(ProjectInfo):
    """项目详细信息"""

    cve_id: Optional[str] = Field(None, description="关联的CVE ID")
    cve_description: Optional[str] = Field(None, description="CVE描述")
    file_count: int = Field(0, description="文件总数")
    directory_structure: Optional[Dict[str, Any]] = Field(None, description="目录结构")


class FileInfo(BaseModel):
    """文件信息"""

    path: str = Field(..., description="文件相对路径")
    name: str = Field(..., description="文件名")
    size: int = Field(..., description="文件大小(字节)")
    is_directory: bool = Field(..., description="是否为目录")
    extension: Optional[str] = Field(None, description="文件扩展名")


class ProjectFilesResponse(BaseModel):
    """项目文件列表响应"""

    case_id: str = Field(..., description="项目案例ID")
    files: List[FileInfo] = Field(..., description="文件列表")
    total_count: int = Field(..., description="文件总数")


class ProjectImportRequest(BaseModel):
    """项目导入请求"""

    source_path: str = Field(..., description="待导入目录的绝对路径")
    case_id: Optional[str] = Field(None, description="覆盖默认的CVE ID")
    overwrite: bool = Field(False, description="若项目已存在是否覆盖")
    language: Optional[str] = Field(None, description="指定CodeQL数据库语言：python/java/cpp（默认自动检测）")
    skip_codeql: bool = Field(False, description="是否跳过CodeQL数据库创建")
    build_command: Optional[str] = Field(None, description="C/C++ 构建命令（Java/Python项目不需要），等价于 codeql --command")
    build_script: Optional[str] = Field(None, description="C/C++ 构建脚本路径（Java/Python项目不需要）")
    build_workdir: Optional[str] = Field(None, description="C/C++ 构建命令的工作目录（Java/Python项目不需要）")


class ProjectImportResponse(BaseModel):
    """项目导入响应"""

    case_id: str = Field(..., description="导入后的项目ID")
    target_path: str = Field(..., description="项目在工作区的路径")
    language: Optional[str] = Field(None, description="检测或指定的语言")
    metadata_files: List[str] = Field(default_factory=list, description="已复制的CVE元数据文件")
    codeql_created: bool = Field(False, description="CodeQL数据库是否创建成功")
    codeql_error: Optional[str] = Field(None, description="CodeQL数据库创建错误（如有）")
    project: ProjectDetail = Field(..., description="导入后项目的详细信息")
    build_command_used: Optional[str] = Field(None, description="实际使用的构建命令（C/C++）")
    build_workdir: Optional[str] = Field(None, description="构建命令的工作目录")


class TaskStatus(str, Enum):
    """任务状态枚举"""

    QUEUED = "queued"
    PENDING = "queued"  # compatibility alias
    RUNNING = "running"
    COMPLETED_WITH_FINDINGS = "completed_with_findings"
    COMPLETED_NO_FINDINGS = "completed_no_findings"
    PARTIAL = "partial"
    COMPLETED = "completed_no_findings"  # compatibility alias
    FAILED = "failed"
    CANCELLED = "cancelled"
    TIMED_OUT = "timed_out"


class AnalysisRequest(BaseModel):
    """分析任务请求"""

    case_id: str = Field(..., description="项目案例ID")
    language: Optional[str] = Field(None, description="目标语言")
    requirement: Optional[str] = Field(None, description="CodeQL查询需求描述")
    max_rounds: int = Field(5, ge=1, le=10, description="最大迭代轮数")
    enable_cve_analysis: bool = Field(True, description="是否启用CVE分析")
    enable_sink_analysis: bool = Field(True, description="是否启用Sink分析")
    enable_source_analysis: bool = Field(True, description="是否启用Source分析")
    enable_path_analysis: bool = Field(True, description="是否启用路径流转分析")
    enable_codeql_generation: bool = Field(True, description="是否启用CodeQL生成")
    enable_path_selection: bool = Field(True, description="是否启用路径筛选")
    enable_breakpoint_recovery: bool = Field(False, description="空结果时是否启用断流恢复")
    enable_source_sink_fallback: bool = Field(False, description="是否启用Source/Sink回退查询")
    timeout_seconds: Optional[int] = Field(None, ge=1, le=86400, description="任务超时秒数")


class AnalysisTaskInfo(BaseModel):
    """分析任务信息"""

    task_id: str = Field(..., description="任务ID")
    run_id: Optional[str] = Field(None, description="运行ID；当前与task_id一致")
    case_id: str = Field(..., description="项目案例ID")
    status: TaskStatus = Field(..., description="任务状态")
    created_at: datetime = Field(..., description="创建时间")
    started_at: Optional[datetime] = Field(None, description="开始时间")
    completed_at: Optional[datetime] = Field(None, description="完成时间")
    progress: Optional[str] = Field(None, description="进度信息")
    error: Optional[str] = Field(None, description="错误信息")
    effective_config: Optional[Dict[str, Any]] = Field(None, description="去敏后的有效配置")
    event_url: Optional[str] = Field(None, description="SSE事件地址")


class AnalysisResult(BaseModel):
    """分析结果"""

    task_id: str = Field(..., description="任务ID")
    case_id: str = Field(..., description="项目案例ID")
    status: TaskStatus = Field(..., description="任务状态")
    cve_analysis: Optional[Dict[str, Any]] = Field(None, description="CVE分析结果")
    sink_analysis: Optional[Dict[str, Any]] = Field(None, description="Sink/Source分析结果")
    source_analysis: Optional[Dict[str, Any]] = Field(None, description="Source分析结果")
    codeql_query: Optional[str] = Field(None, description="生成的CodeQL查询")
    query_results: Optional[Dict[str, Any]] = Field(None, description="查询执行结果")
    output_dir: Optional[str] = Field(None, description="输出目录路径")
    outcome: Optional[str] = Field(None, description="结构化运行结局")
    steps: Dict[str, Any] = Field(default_factory=dict, description="步骤结果")
    manifest: Optional[Dict[str, Any]] = Field(None, description="运行清单")
    artifacts: List[Dict[str, Any]] = Field(default_factory=list, description="产物列表")


class TaskListResponse(BaseModel):
    """任务列表响应"""

    tasks: List[AnalysisTaskInfo] = Field(..., description="任务列表")
    total: int = Field(..., description="任务总数")
    page: int = Field(1, description="当前页码")
    page_size: int = Field(20, description="每页大小")


class HealthResponse(BaseModel):
    """健康检查响应"""

    status: str = Field(..., description="服务状态")
    version: str = Field(..., description="API版本")
    timestamp: datetime = Field(..., description="当前时间")
    dependencies: Dict[str, bool] = Field(default_factory=dict, description="依赖健康状态")


class VersionResponse(BaseModel):
    """版本信息响应"""

    api_version: str = Field(..., description="API版本")
    build_time: Optional[str] = Field(None, description="构建时间")
    commit_hash: Optional[str] = Field(None, description="Git提交哈希")


class ErrorResponse(BaseModel):
    """错误响应"""

    error: str = Field(..., description="错误类型")
    message: str = Field(..., description="错误消息")
    detail: Optional[Any] = Field(None, description="详细信息")
    timestamp: datetime = Field(default_factory=datetime.now, description="错误时间")


class StreamEventType(str, Enum):
    """流式事件类型枚举"""

    STEP_START = "step_start"
    STEP_PROGRESS = "step_progress"
    AGENT_ACTION = "agent_action"
    TOOL_START = "tool_start"
    TOOL_OUTPUT = "tool_output"
    AGENT_OUTPUT = "agent_output"
    STEP_COMPLETE = "step_complete"
    ERROR = "error"
    COMPLETED = "completed"
    # Agent级别事件类型
    AGENT_START = "agent_start"
    AGENT_THINKING = "agent_thinking"
    AGENT_TOOL_CALL = "agent_tool_call"
    AGENT_COMPLETE = "agent_complete"


class StreamEvent(BaseModel):
    """流式事件模型"""

    type: StreamEventType = Field(..., description="事件类型")
    timestamp: datetime = Field(default_factory=datetime.now, description="事件时间戳")
    step_name: Optional[str] = Field(None, description="当前步骤名称")
    message: Optional[str] = Field(None, description="消息内容")
    data: Optional[Dict[str, Any]] = Field(None, description="附加数据")
    agent_name: Optional[str] = Field(None, description="Agent名称")
    agent_type: Optional[str] = Field(None, description="Agent类型")
