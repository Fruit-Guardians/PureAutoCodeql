"""Retry classification, backoff decorator, and per-session retry tracking."""

import asyncio
import random
import time
from typing import Callable, List

from pure_auto_codeql.configuration import LLMConfig


class APIErrorClassifier:
    """API错误分类器，用于判断错误是否可重试"""
    NON_RETRYABLE_STATUS_CODES = {400, 401, 403, 422}
    RETRYABLE_SERVER_ERRORS = {404, 429, 500, 502, 503, 504}
    NETWORK_EXCEPTIONS = (
        ConnectionError,
        TimeoutError,
        OSError,
    )

    OPENAI_EXCEPTIONS = (
        'openai.RateLimitError',
        'openai.APIConnectionError',
        'openai.APITimeoutError',
        'openai.InternalServerError',
    )

    @classmethod
    def is_retryable_error(cls, error: Exception) -> bool:
        """判断错误是否可重试"""
        if hasattr(error, 'status_code'):
            status_code = error.status_code
            if status_code in cls.NON_RETRYABLE_STATUS_CODES:
                return False
            if status_code in cls.RETRYABLE_SERVER_ERRORS:
                return True

        if isinstance(error, cls.NETWORK_EXCEPTIONS):
            return True

        error_full_name = f"{type(error).__module__}.{type(error).__name__}"
        if error_full_name in cls.OPENAI_EXCEPTIONS:
            return True

        error_str = str(error).lower()
        retryable_keywords = [
            'connection', 'timeout', 'network', 'dns', 'unreachable',
            'rate limit', 'too many requests', 'service unavailable',
            'bad gateway', 'gateway timeout', 'internal server error'
        ]
        non_retryable_keywords = [
            'invalid api key', 'authentication', 'authorization',
            'permission denied', 'forbidden', 'invalid request'
        ]

        if any(keyword in error_str for keyword in retryable_keywords):
            return True

        if any(keyword in error_str for keyword in non_retryable_keywords):
            return False

        return False

    @classmethod
    def get_error_context(cls, error: Exception) -> dict:
        """获取错误的详细上下文信息"""
        context = {
            'error_type': type(error).__name__,
            'error_message': str(error),
            'is_retryable': cls.is_retryable_error(error),
            'status_code': None,
            'error_category': 'unknown'
        }

        if hasattr(error, 'status_code'):
            context['status_code'] = error.status_code
            if error.status_code in cls.NON_RETRYABLE_STATUS_CODES:
                context['error_category'] = 'authentication/permission'
            elif error.status_code in cls.RETRYABLE_SERVER_ERRORS:
                context['error_category'] = 'server_error'

        if isinstance(error, cls.NETWORK_EXCEPTIONS):
            context['error_category'] = 'network_error'

        error_full_name = f"{type(error).__module__}.{type(error).__name__}"
        if error_full_name in cls.OPENAI_EXCEPTIONS:
            context['error_category'] = 'openai_error'

        return context


def llm_retry_decorator(config: LLMConfig):
    """LLM API重试装饰器"""
    def decorator(func: Callable) -> Callable:
        async def async_wrapper(*args, **kwargs):
            last_error = None

            for attempt in range(config.max_retries + 1):
                try:
                    return await func(*args, **kwargs)
                except Exception as error:
                    last_error = error

                    error_context = APIErrorClassifier.get_error_context(error)

                    if not error_context['is_retryable']:
                        raise error

                    if attempt == config.max_retries:
                        print(f"❌ 重试失败 ({attempt + 1}/{config.max_retries + 1}): {error}")
                        raise error

                    delay = config.retry_base_delay * (config.retry_backoff_factor ** attempt)
                    if config.retry_jitter:
                        delay *= (0.5 + random.random() * 0.5)  # 50%-100%的随机抖动

                    print(f"🔄 重试 ({attempt + 1}/{config.max_retries + 1})")

                    await asyncio.sleep(delay)

            raise last_error

        return async_wrapper
    return decorator


class AgentRetryTracker:
    """Agent重试状态跟踪器"""

    def __init__(self):
        self.retry_attempts = {}
        self.retry_logs = []

    def start_retry_session(self, session_id: str, agent_name: str = None):
        """开始重试会话"""
        self.retry_attempts[session_id] = {
            "agent_name": agent_name,
            "start_time": time.time(),
            "attempts": 0,
            "errors": [],
            "error_categories": []
        }

    def log_retry_attempt(self, session_id: str, error: Exception, attempt: int, delay: float = None):
        """记录重试尝试"""
        if session_id not in self.retry_attempts:
            return

        error_context = APIErrorClassifier.get_error_context(error)

        self.retry_attempts[session_id]["attempts"] = attempt
        self.retry_attempts[session_id]["errors"].append({
            "attempt": attempt,
            "error": str(error),
            "error_type": type(error).__name__,
            "timestamp": time.time(),
            "delay_before_retry": delay,
            "error_context": error_context
        })

        if error_context['error_category'] not in self.retry_attempts[session_id]["error_categories"]:
            self.retry_attempts[session_id]["error_categories"].append(error_context['error_category'])

        log_entry = {
            "session_id": session_id,
            "agent_name": self.retry_attempts[session_id]["agent_name"],
            "attempt": attempt,
            "error": str(error),
            "error_type": type(error).__name__,
            "timestamp": time.time(),
            "delay_before_retry": delay,
            "error_context": error_context
        }
        self.retry_logs.append(log_entry)

    def end_retry_session(self, session_id: str, success: bool = False, final_error: Exception = None):
        """结束重试会话"""
        if session_id not in self.retry_attempts:
            return

        session = self.retry_attempts[session_id]
        session["end_time"] = time.time()
        session["duration"] = session["end_time"] - session["start_time"]
        session["success"] = success

        if final_error:
            session["final_error"] = {
                "error": str(final_error),
                "error_type": type(final_error).__name__,
                "error_context": APIErrorClassifier.get_error_context(final_error)
            }

        if not success and session.get("final_error"):
            print(f"❌ 最终失败 ({session['attempts']}/{session['attempts']}): {session['final_error']['error']}")

    def get_retry_summary(self, session_id: str) -> dict:
        """获取重试会话摘要"""
        return self.retry_attempts.get(session_id, {})

    def get_all_retry_logs(self) -> List[dict]:
        """获取所有重试日志"""
        return self.retry_logs

    def clear_old_logs(self, max_age_seconds: int = 3600):
        """清理旧的重试日志"""
        current_time = time.time()
        self.retry_logs = [
            log for log in self.retry_logs
            if current_time - log["timestamp"] <= max_age_seconds
        ]
