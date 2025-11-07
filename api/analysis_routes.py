from typing import Optional
from fastapi import APIRouter, HTTPException, Query, BackgroundTasks
from fastapi.responses import JSONResponse

from api.models import (
    AnalysisRequest,
    AnalysisTaskInfo,
    AnalysisResult,
    TaskListResponse,
    TaskStatus,
    ErrorResponse
)
from api.task_manager import get_task_manager
from utils.case import resolve_case


router = APIRouter(prefix="/api/analysis", tags=["analysis"])

@router.post("/start", response_model=AnalysisTaskInfo, status_code=202)
async def start_analysis(
    request: AnalysisRequest,
    background_tasks: BackgroundTasks
) -> AnalysisTaskInfo:
    
    try:
        case_paths = resolve_case(request.case_id)
        if not case_paths.root.exists():
            raise HTTPException(
                status_code=404,
                detail=f"项目 '{request.case_id}' 不存在"
            )
    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"无效的项目ID: {str(e)}"
        )
    
    task_manager = get_task_manager()
    task_id = task_manager.create_task(request.case_id)
    
    config = {
        'language': request.language,
        'requirement': request.requirement,
        'max_rounds': request.max_rounds,
        'enable_cve_analysis': request.enable_cve_analysis,
        'enable_sink_analysis': request.enable_sink_analysis,
        'show_thinking': False,
        'refresh_intel': False
    }
    
    background_tasks.add_task(task_manager.start_task, task_id, config)
    
    task_info = task_manager.get_task_status(task_id)
    if not task_info:
        raise HTTPException(
            status_code=500,
            detail="任务创建失败"
        )
    
    return task_info


@router.get("/{task_id}/status", response_model=AnalysisTaskInfo)
async def get_task_status(task_id: str) -> AnalysisTaskInfo:
    task_manager = get_task_manager()
    task_info = task_manager.get_task_status(task_id)
    
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )
    
    return task_info


@router.get("/{task_id}/result", response_model=AnalysisResult)
async def get_task_result(task_id: str) -> AnalysisResult:
    task_manager = get_task_manager()
    
    task_info = task_manager.get_task_status(task_id)
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )
    
    if task_info.status == TaskStatus.PENDING:
        raise HTTPException(
            status_code=409,
            detail="任务尚未开始执行"
        )
    
    if task_info.status == TaskStatus.RUNNING:
        raise HTTPException(
            status_code=409,
            detail="任务正在执行中，请稍后查询"
        )
    
    if task_info.status == TaskStatus.CANCELLED:
        raise HTTPException(
            status_code=410,
            detail="任务已被取消"
        )
    
    if task_info.status == TaskStatus.FAILED:
        raise HTTPException(
            status_code=500,
            detail=f"任务执行失败: {task_info.error}"
        )
    
    result = task_manager.get_task_result(task_id)
    if not result:
        raise HTTPException(
            status_code=404,
            detail="任务结果不存在"
        )
    
    return result


@router.delete("/{task_id}", status_code=200)
async def cancel_task(task_id: str) -> dict:
    task_manager = get_task_manager()
    
    task_info = task_manager.get_task_status(task_id)
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )
    
    if task_info.status not in [TaskStatus.PENDING, TaskStatus.RUNNING]:
        raise HTTPException(
            status_code=400,
            detail=f"无法取消状态为 '{task_info.status.value}' 的任务"
        )
    
    success = await task_manager.cancel_task(task_id)
    if not success:
        raise HTTPException(
            status_code=500,
            detail="任务取消失败"
        )
    
    return {
        "message": "任务已成功取消",
        "task_id": task_id
    }


@router.get("/tasks", response_model=TaskListResponse)
async def list_tasks(
    status: Optional[TaskStatus] = Query(None, description="按状态过滤"),
    page: int = Query(1, ge=1, description="页码"),
    page_size: int = Query(20, ge=1, le=100, description="每页大小")
) -> TaskListResponse:
    task_manager = get_task_manager()
    
    offset = (page - 1) * page_size
    
    tasks, total = task_manager.list_tasks(
        status_filter=status,
        limit=page_size,
        offset=offset
    )
    
    return TaskListResponse(
        tasks=tasks,
        total=total,
        page=page,
        page_size=page_size
    )


@router.post("/cleanup", status_code=200)
async def cleanup_old_tasks(
    max_age_hours: int = Query(24, ge=1, le=168, description="最大保留时间（小时）")
) -> dict:
    task_manager = get_task_manager()
    task_manager.cleanup_old_tasks(max_age_hours)
    
    return {
        "message": f"已清理超过 {max_age_hours} 小时的旧任务"
    }
