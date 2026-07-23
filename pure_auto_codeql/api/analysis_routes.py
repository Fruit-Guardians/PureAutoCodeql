import asyncio
import json
from typing import Optional

from fastapi import APIRouter, BackgroundTasks, HTTPException, Query, Request
from fastapi.responses import StreamingResponse

from pure_auto_codeql.application import (
    AnalysisValidationError,
    validate_analysis_case,
)
from pure_auto_codeql.core.context import AnalysisConfig

from .config import get_config
from .durable_task_manager import get_durable_task_manager
from .models import AnalysisRequest, AnalysisResult, AnalysisTaskInfo, TaskListResponse, TaskStatus
from .task_manager import get_task_manager

router = APIRouter(prefix="/analysis", tags=["analysis"])

# 启动任务
@router.post("/start", response_model=AnalysisTaskInfo, status_code=202)
async def start_analysis(
    request: AnalysisRequest,
    background_tasks: BackgroundTasks
) -> AnalysisTaskInfo:

    try:
        validate_analysis_case(request.case_id, projects_dir=get_config().projects_dir)
    except AnalysisValidationError as e:
        raise HTTPException(
            status_code=e.status_code,
            detail=str(e),
        ) from e

    api_config = get_config()
    config = AnalysisConfig(
        language=request.language,
        requirement=request.requirement,
        max_codeql_rounds=request.max_rounds,
        task_timeout=request.timeout_seconds or api_config.task_timeout,
        enable_cve_analysis=request.enable_cve_analysis,
        enable_sink_analysis=request.enable_sink_analysis,
        enable_source_analysis=request.enable_source_analysis,
        enable_path_analysis=request.enable_path_analysis,
        enable_codeql_generation=request.enable_codeql_generation,
        enable_path_selection=request.enable_path_selection,
        enable_breakpoint_recovery=request.enable_breakpoint_recovery,
        enable_source_sink_fallback=request.enable_source_sink_fallback,
        show_thinking=False,
        refresh_intel=False,
    )
    try:
        config.validate()
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    durable_manager = get_durable_task_manager()
    if durable_manager:
        return await durable_manager.create_task(request.case_id, config)

    task_manager = get_task_manager()
    task_id = task_manager.create_task(request.case_id, config)
    background_tasks.add_task(task_manager.start_task, task_id)

    task_info = task_manager.get_task_status(task_id)
    if not task_info:
        raise HTTPException(
            status_code=500,
            detail="任务创建失败"
        )

    return task_info


@router.get("/{task_id}/status", response_model=AnalysisTaskInfo)
async def get_task_status(task_id: str) -> AnalysisTaskInfo:
    durable_manager = get_durable_task_manager()
    if durable_manager:
        task_info = await durable_manager.get_task_status(task_id)
        if not task_info:
            raise HTTPException(status_code=404, detail=f"任务 '{task_id}' 不存在")
        return task_info

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
    durable_manager = get_durable_task_manager()
    task_manager = None if durable_manager else get_task_manager()
    task_info = (
        await durable_manager.get_task_status(task_id)
        if durable_manager
        else task_manager.get_task_status(task_id)
    )
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )

    if task_info.status == TaskStatus.QUEUED:
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

    if task_info.status == TaskStatus.TIMED_OUT:
        raise HTTPException(
            status_code=504,
            detail=f"任务执行超时: {task_info.error}",
        )

    if task_info.status == TaskStatus.FAILED:
        raise HTTPException(
            status_code=500,
            detail=f"任务执行失败: {task_info.error}"
        )

    result = (
        await durable_manager.get_task_result(task_id)
        if durable_manager
        else task_manager.get_task_result(task_id)
    )
    if not result:
        raise HTTPException(
            status_code=404,
            detail="任务结果不存在"
        )

    return result


@router.delete("/{task_id}", status_code=200)
async def cancel_task(task_id: str) -> dict:
    durable_manager = get_durable_task_manager()
    task_manager = None if durable_manager else get_task_manager()
    task_info = (
        await durable_manager.get_task_status(task_id)
        if durable_manager
        else task_manager.get_task_status(task_id)
    )
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )

    if task_info.status not in [TaskStatus.QUEUED, TaskStatus.RUNNING]:
        raise HTTPException(
            status_code=400,
            detail=f"无法取消状态为 '{task_info.status.value}' 的任务"
        )

    success = await (
        durable_manager.cancel_task(task_id)
        if durable_manager
        else task_manager.cancel_task(task_id)
    )
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
    offset = (page - 1) * page_size
    durable_manager = get_durable_task_manager()
    if durable_manager:
        tasks, total = await durable_manager.list_tasks(status, page_size, offset)
    else:
        tasks, total = get_task_manager().list_tasks(
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
    if get_durable_task_manager():
        return {
            "message": "持久化模式由数据库保留策略管理历史任务",
        }
    task_manager = get_task_manager()
    task_manager.cleanup_old_tasks(max_age_hours)

    return {
        "message": f"已清理超过 {max_age_hours} 小时的旧任务"
    }


@router.get("/{task_id}/stream")
async def stream_task_output(task_id: str, request: Request):
    """
    通过 Server-Sent Events (SSE) 流式输出任务的实时事件

    Args:
        task_id: 任务ID

    Returns:
        StreamingResponse: SSE 格式的事件流

    Raises:
        HTTPException 404: 任务不存在或事件队列未创建
        HTTPException 410: 任务已结束且事件队列已清理
    """
    durable_manager = get_durable_task_manager()
    if durable_manager:
        task_info = await durable_manager.get_task_status(task_id)
        if not task_info:
            raise HTTPException(status_code=404, detail=f"任务 '{task_id}' 不存在")
        after_id = request.headers.get("Last-Event-ID", "0-0")

        async def durable_event_generator():
            async for event in durable_manager.events(task_id, after_id):
                payload = event.model_dump(mode="json")
                yield (
                    f"id: {event.event_id}\n"
                    f"event: {event.type}\n"
                    f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
                )

        return StreamingResponse(
            durable_event_generator(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    task_manager = get_task_manager()

    task_info = task_manager.get_task_status(task_id)
    if not task_info:
        raise HTTPException(
            status_code=404,
            detail=f"任务 '{task_id}' 不存在"
        )

    if task_id not in task_manager._event_queues:
        if task_info.status not in [TaskStatus.QUEUED, TaskStatus.RUNNING]:
            raise HTTPException(
                status_code=410,
                detail=f"任务已结束，事件流不再可用。请使用 /api/analysis/{task_id}/result 获取最终结果"
            )
        else:
            raise HTTPException(
                status_code=404,
                detail="任务事件队列未创建，任务可能尚未启动"
            )

    def _format_sse(event: dict) -> tuple[str, dict]:
        """把事件格式化为 (sse_text, normalized_event_data)。"""
        event_type = event.get('type', 'message')
        event_data = {
            'type': str(event_type) if hasattr(event_type, 'value') else event_type,
            'timestamp': event.get('timestamp'),
            'step_name': event.get('step_name'),
            'message': event.get('message'),
            'data': event.get('data', {})
        }
        event_json = json.dumps(event_data, ensure_ascii=False)
        return f"event: {event_data['type']}\ndata: {event_json}\n\n", event_data

    def _is_terminal(event: dict, event_data: dict) -> bool:
        return (
            event_data['type'] in ['completed', 'error']
            and event.get('data', {}).get('task_id') == task_id
        )

    # 事件生成器
    async def event_generator():
        """先回放历史事件，再从队列读取新事件并格式化为 SSE。"""
        queue = task_manager._event_queues.get(task_id)
        if queue is None:
            return

        # 1) 回放已发生的历史事件，使中途连接的客户端不丢失前序进度
        replayed = 0
        for past_event in list(task_manager._task_events.get(task_id, [])):
            sse_text, event_data = _format_sse(past_event)
            yield sse_text
            replayed += 1
            if _is_terminal(past_event, event_data):
                # 历史里已包含终止事件，无需再消费队列
                return

        try:
            # 2) 消费实时队列。跳过已回放过的、仍留在队列中的历史事件，避免重复。
            while True:
                try:
                    event = await asyncio.wait_for(queue.get(), timeout=30.0)
                except asyncio.TimeoutError:
                    yield ": heartbeat\n\n"
                    continue

                if replayed > 0:
                    replayed -= 1
                    continue

                sse_text, event_data = _format_sse(event)
                yield sse_text

                if _is_terminal(event, event_data):
                    break

        except asyncio.CancelledError:
            pass
        except Exception as e:
            # 发送错误事件
            error_event = {
                'type': 'error',
                'timestamp': None,
                'step_name': 'stream',
                'message': f'流式传输错误: {str(e)}',
                'data': {'error': str(e)}
            }
            yield "event: error\n"
            yield f"data: {json.dumps(error_event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 禁用 nginx 缓冲
        }
    )
