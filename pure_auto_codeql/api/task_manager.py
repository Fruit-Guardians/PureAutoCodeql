import asyncio
import json
import uuid
from dataclasses import asdict, replace
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from pure_auto_codeql.analysis_models import AnalysisOutcome
from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator
from pure_auto_codeql.services.process_control import ProcessScope, bind_process_scope
from pure_auto_codeql.utils.logger import get_logger

from .config import get_config
from .models import AnalysisResult, AnalysisTaskInfo, TaskStatus

logger = get_logger(__name__)


class TaskManager:

    def __init__(self):
        self._tasks: Dict[str, AnalysisTaskInfo] = {}
        self._results: Dict[str, AnalysisResult] = {}
        self._running_tasks: Dict[str, asyncio.Task] = {}
        self._task_configs: Dict[str, AnalysisConfig] = {}
        self._max_concurrent_tasks = max(1, get_config().max_concurrent_tasks)
        self._semaphore = asyncio.Semaphore(self._max_concurrent_tasks)
        self._event_queues: Dict[str, asyncio.Queue] = {}
        self._task_events: Dict[str, List[Dict]] = {}
        self._max_events_per_task = 1000

    def create_task(self, case_id: str, config: Optional[AnalysisConfig] = None) -> str:
        task_id = str(uuid.uuid4())
        analysis_config = config or AnalysisConfig()
        analysis_config.validate()
        safe_config = asdict(analysis_config)
        safe_config["api_key"] = "***" if safe_config.get("api_key") else None
        safe_config["event_callback"] = None

        task_info = AnalysisTaskInfo(
            task_id=task_id,
            run_id=task_id,
            case_id=case_id,
            status=TaskStatus.QUEUED,
            created_at=datetime.now(),
            progress="任务已创建，等待执行",
            effective_config=safe_config,
            event_url=f"/api/v1/analysis/{task_id}/stream",
        )

        self._tasks[task_id] = task_info
        self._task_configs[task_id] = analysis_config
        # 提前创建事件队列与历史，使得任务在排队（尚未获得信号量运行）阶段
        # 连接 SSE 也能拿到事件流，而不是收到 404。
        self._event_queues.setdefault(task_id, asyncio.Queue(maxsize=self._max_events_per_task))
        self._task_events.setdefault(task_id, [])
        return task_id

    async def start_task(self, task_id: str, config: Optional[AnalysisConfig] = None) -> bool:
        if task_id not in self._tasks:
            return False

        task_info = self._tasks[task_id]
        if task_info.status != TaskStatus.QUEUED or task_id in self._running_tasks:
            return False

        task_info.progress = "任务已排队，等待执行"

        effective_config = self._coerce_config(
            config or self._task_configs.get(task_id) or AnalysisConfig()
        )
        background_task = asyncio.create_task(
            self._run_queued_analysis(task_id, task_info.case_id, effective_config)
        )
        self._running_tasks[task_id] = background_task

        return True

    @staticmethod
    def _coerce_config(config) -> AnalysisConfig:
        """Accept the pre-v1 dictionary form during the compatibility window."""
        if isinstance(config, AnalysisConfig):
            return config
        if isinstance(config, dict):
            values = dict(config)
            if "max_rounds" in values:
                values["max_codeql_rounds"] = values.pop("max_rounds")
            return AnalysisConfig(**values)
        raise TypeError(f"Unsupported analysis config type: {type(config).__name__}")

    async def _run_queued_analysis(self, task_id: str, case_id: str, config: AnalysisConfig):
        task_info = self._tasks[task_id]
        try:
            async with self._semaphore:
                if task_info.status == TaskStatus.CANCELLED:
                    return
                task_info.status = TaskStatus.RUNNING
                task_info.started_at = datetime.now()
                task_info.progress = "正在初始化分析环境..."
                process_scope = ProcessScope()
                with bind_process_scope(process_scope):
                    try:
                        await asyncio.wait_for(
                            self._run_analysis(task_id, case_id, config),
                            timeout=config.task_timeout,
                        )
                    except asyncio.TimeoutError:
                        process_scope.terminate_all()
                        task_info.status = TaskStatus.TIMED_OUT
                        task_info.error = f"任务超过 {config.task_timeout} 秒超时"
                        task_info.progress = "任务执行超时"
                        task_info.completed_at = datetime.now()
                        await self._record_event(
                            task_id,
                            {
                                "type": "error",
                                "timestamp": datetime.now().isoformat(),
                                "step_name": "analysis",
                                "message": task_info.error,
                                "data": {"task_id": task_id, "timed_out": True},
                            },
                        )
                    finally:
                        process_scope.terminate_all()
        finally:
            self._running_tasks.pop(task_id, None)


    async def _run_analysis(self, task_id: str, case_id: str, config: AnalysisConfig):
        task_info = self._tasks[task_id]

        # Phase 4.1 & 4.2: 复用 create_task 时已建立的事件队列与历史
        # （若因兼容路径缺失则惰性创建）
        self._event_queues.setdefault(
            task_id,
            asyncio.Queue(maxsize=self._max_events_per_task),
        )
        self._task_events.setdefault(task_id, [])

        async def event_callback(event):
            """事件回调函数，将事件推送到队列和历史存储"""
            await self._record_event(task_id, event)

        try:
            # Phase 4.3: 推送分析开始事件
            from .models import StreamEventType
            await event_callback({
                "type": StreamEventType.STEP_START,
                "timestamp": datetime.now().isoformat(),
                "step_name": "analysis",
                "message": "开始执行分析任务",
                "data": {"case_id": case_id, "task_id": task_id}
            })

            analysis_config = replace(config, output_file=None, event_callback=event_callback)

            orchestrator = AnalysisOrchestrator(analysis_config)

            task_info.progress = "正在执行CVE分析..."
            result = await orchestrator.analyze_case(
                case_id,
                language=config.language,
            )

            self._results[task_id] = self._convert_to_api_result(task_id, case_id, result)

            task_info.status = self._status_from_outcome(result.outcome)
            if task_info.status != TaskStatus.FAILED:
                task_info.progress = "分析完成"
                # Phase 4.4: 推送完成事件
                await event_callback({
                    "type": StreamEventType.COMPLETED,
                    "timestamp": datetime.now().isoformat(),
                    "step_name": "analysis",
                    "message": "分析任务完成",
                    "data": {
                        "task_id": task_id,
                        "success": result.success,
                        "outcome": result.outcome.value,
                    }
                })
            else:
                task_info.status = TaskStatus.FAILED
                task_info.error = result.error_message
                task_info.progress = "分析失败"
                # Phase 4.5: 推送错误事件
                await event_callback({
                    "type": StreamEventType.ERROR,
                    "timestamp": datetime.now().isoformat(),
                    "step_name": "analysis",
                    "message": f"分析失败: {result.error_message}",
                    "data": {"task_id": task_id, "error": result.error_message}
                })

            task_info.completed_at = datetime.now()

        except asyncio.CancelledError:
            task_info.status = TaskStatus.CANCELLED
            task_info.progress = "任务已取消"
            task_info.completed_at = datetime.now()
            # Phase 4.6: 任务取消时推送事件并清理
            try:
                await event_callback({
                    "type": StreamEventType.ERROR,
                    "timestamp": datetime.now().isoformat(),
                    "step_name": "analysis",
                    "message": "任务已被取消",
                    "data": {"task_id": task_id, "cancelled": True}
                })
            except Exception:
                logger.debug("推送任务取消事件失败", exc_info=True)
            raise

        except Exception as e:
            task_info.status = TaskStatus.FAILED
            task_info.error = str(e)
            task_info.progress = f"执行错误: {str(e)}"
            task_info.completed_at = datetime.now()
            # Phase 4.5: 推送错误事件
            try:
                await event_callback({
                    "type": StreamEventType.ERROR,
                    "timestamp": datetime.now().isoformat(),
                    "step_name": "analysis",
                    "message": f"执行错误: {str(e)}",
                    "data": {"task_id": task_id, "error": str(e)}
                })
            except Exception:
                logger.debug("推送任务错误事件失败", exc_info=True)

        finally:
            self._running_tasks.pop(task_id, None)

    async def _record_event(self, task_id: str, event: dict) -> None:
        queue = self._event_queues.setdefault(
            task_id,
            asyncio.Queue(maxsize=self._max_events_per_task),
        )
        history = self._task_events.setdefault(task_id, [])
        if queue.full():
            try:
                queue.get_nowait()
            except asyncio.QueueEmpty:
                pass
        queue.put_nowait(event)
        history.append(event)
        if len(history) > self._max_events_per_task:
            del history[: len(history) - self._max_events_per_task]

    @staticmethod
    def _status_from_outcome(outcome: AnalysisOutcome) -> TaskStatus:
        return {
            AnalysisOutcome.COMPLETED_WITH_FINDINGS: TaskStatus.COMPLETED_WITH_FINDINGS,
            AnalysisOutcome.COMPLETED_NO_FINDINGS: TaskStatus.COMPLETED_NO_FINDINGS,
            AnalysisOutcome.PARTIAL: TaskStatus.PARTIAL,
            AnalysisOutcome.CANCELLED: TaskStatus.CANCELLED,
            AnalysisOutcome.TIMED_OUT: TaskStatus.TIMED_OUT,
            AnalysisOutcome.FAILED: TaskStatus.FAILED,
        }.get(outcome, TaskStatus.COMPLETED_NO_FINDINGS)

    def _convert_to_api_result(self, task_id: str, case_id: str, core_result) -> AnalysisResult:
        cve_analysis = None
        if hasattr(core_result, 'cve_result') and core_result.cve_result:
            cve_analysis = {
                'success': core_result.cve_result.success,
                'content': core_result.cve_result.content if hasattr(core_result.cve_result, 'content') else None,
                'error': core_result.cve_result.error if hasattr(core_result.cve_result, 'error') else None
            }

        sink_analysis = None
        if hasattr(core_result, 'sink_result') and core_result.sink_result:
            sink_analysis = {
                'success': core_result.sink_result.success,
                'content': core_result.sink_result.content if hasattr(core_result.sink_result, 'content') else None,
                'error': core_result.sink_result.error if hasattr(core_result.sink_result, 'error') else None
            }

        source_analysis = None
        if hasattr(core_result, 'source_result') and core_result.source_result:
            source_analysis = {
                'success': core_result.source_result.success,
                'content': core_result.source_result.content if hasattr(core_result.source_result, 'content') else None,
                'error': core_result.source_result.error if hasattr(core_result.source_result, 'error') else None
            }

        codeql_query = None
        if hasattr(core_result, 'codeql_result') and core_result.codeql_result:
            if hasattr(core_result.codeql_result, 'content'):
                codeql_query = core_result.codeql_result.content

        query_results = None
        if hasattr(core_result, 'codeql_execution_result') and core_result.codeql_execution_result:
            query_results = {
                'success': core_result.codeql_execution_result.get('success', False),
                'sarif_path': core_result.codeql_execution_result.get('sarif_path'),
                'findings_count': core_result.codeql_execution_result.get('findings_count', 0)
            }

        output_dir = None
        if hasattr(core_result, 'output_directory'):
            output_dir = core_result.output_directory

        return AnalysisResult(
            task_id=task_id,
            case_id=case_id,
            status=self._status_from_outcome(core_result.outcome),
            cve_analysis=cve_analysis,
            sink_analysis=sink_analysis,
            source_analysis=source_analysis,
            codeql_query=codeql_query,
            query_results=query_results,
            output_dir=output_dir,
            outcome=core_result.outcome.value,
            steps={
                name: step_result.to_dict()
                for name, step_result in core_result.step_results.items()
            },
            manifest=self._read_manifest(output_dir),
            artifacts=[
                artifact.to_dict()
                for artifact in getattr(core_result, "artifacts", [])
            ],
        )

    @staticmethod
    def _read_manifest(output_dir: Optional[str]) -> Optional[dict]:
        if not output_dir:
            return None
        try:
            manifest_path = Path(output_dir) / "manifest.json"
            return json.loads(manifest_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

    def get_task_status(self, task_id: str) -> Optional[AnalysisTaskInfo]:
        return self._tasks.get(task_id)

    def get_task_result(self, task_id: str) -> Optional[AnalysisResult]:
        return self._results.get(task_id)

    async def cancel_task(self, task_id: str) -> bool:
        if task_id not in self._tasks:
            return False

        task_info = self._tasks[task_id]

        if task_info.status not in [TaskStatus.QUEUED, TaskStatus.RUNNING]:
            return False

        if task_id in self._running_tasks:
            background_task = self._running_tasks[task_id]
            background_task.cancel()
            try:
                await background_task
            except asyncio.CancelledError:
                pass

        task_info.status = TaskStatus.CANCELLED
        task_info.progress = "任务已取消"
        task_info.completed_at = datetime.now()

        return True

    def list_tasks(self, status_filter: Optional[TaskStatus] = None,
                   limit: int = 20, offset: int = 0) -> tuple[List[AnalysisTaskInfo], int]:
        tasks = list(self._tasks.values())
        if status_filter:
            tasks = [t for t in tasks if t.status == status_filter]

        tasks.sort(key=lambda t: t.created_at, reverse=True)

        total = len(tasks)
        tasks = tasks[offset:offset + limit]

        return tasks, total

    def cleanup_old_tasks(self, max_age_hours: int = 24):
        now = datetime.now()
        to_remove = []

        for task_id, task_info in self._tasks.items():
            if task_info.status not in [TaskStatus.QUEUED, TaskStatus.RUNNING]:
                age = (now - task_info.created_at).total_seconds() / 3600
                if age > max_age_hours:
                    to_remove.append(task_id)

        for task_id in to_remove:
            del self._tasks[task_id]
            if task_id in self._results:
                del self._results[task_id]
            self._task_configs.pop(task_id, None)
            # Phase 4.6: 清理事件队列和历史
            if task_id in self._event_queues:
                del self._event_queues[task_id]
            if task_id in self._task_events:
                del self._task_events[task_id]


_task_manager: Optional[TaskManager] = None


def get_task_manager() -> TaskManager:
    global _task_manager
    if _task_manager is None:
        _task_manager = TaskManager()
    return _task_manager
