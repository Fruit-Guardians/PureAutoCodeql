"""ChatOpenAI wrapper that adds retry/backoff around streaming calls."""

import asyncio
import random
import time

from langchain_openai import ChatOpenAI

from pure_auto_codeql.configuration import LLMConfig

from .retry import AgentRetryTracker, APIErrorClassifier, llm_retry_decorator


class RetryableChatOpenAI:
    """带有重试机制的ChatOpenAI包装器"""

    def __init__(self, config: LLMConfig, retry_tracker: AgentRetryTracker = None, event_callback=None):
        self.config = config
        self.retry_tracker = retry_tracker or AgentRetryTracker()
        self.event_callback = event_callback
        self._llm = ChatOpenAI(
            model=config.model,
            api_key=config.api_key,
            base_url=config.base_url,
            temperature=config.temperature,
            streaming=config.streaming,
            max_tokens=config.max_tokens,
            max_retries=0,  # 我们自己控制重试
        )

    @llm_retry_decorator
    async def astream_events(self, *args, **kwargs):
        """带有重试机制的astream_events方法"""
        # 创建会话ID用于跟踪
        session_id = f"llm_session_{int(time.time() * 1000)}"
        self.retry_tracker.start_retry_session(session_id)

        # 发送重试会话开始事件
        if self.event_callback:
            from datetime import datetime
            await self.event_callback({
                "type": "retry_session_start",
                "timestamp": datetime.now().isoformat(),
                "session_id": session_id,
                "max_retries": self.config.max_retries,
                "data": {
                    "base_delay": self.config.retry_base_delay,
                    "backoff_factor": self.config.retry_backoff_factor,
                    "jitter": self.config.retry_jitter
                }
            })

        try:
            # 创建一个包装的函数来跟踪重试
            async def tracked_astream():
                return self._llm.astream_events(*args, **kwargs)

            # 使用装饰器包装的函数
            async def retry_wrapper():
                for attempt in range(self.config.max_retries + 1):
                    try:
                        return await tracked_astream()
                    except Exception as error:
                        # 检查是否可重试
                        if not APIErrorClassifier.is_retryable_error(error):
                            self.retry_tracker.end_retry_session(session_id, False)
                            raise error

                        # 如果是最后一次尝试，直接抛出异常
                        if attempt == self.config.max_retries:
                            self.retry_tracker.end_retry_session(session_id, False)
                            raise error

                        # 计算延迟时间（指数退避 + 抖动）
                        delay = self.config.retry_base_delay * (self.config.retry_backoff_factor ** attempt)
                        if self.config.retry_jitter:
                            delay *= (0.5 + random.random() * 0.5)

                        # 记录重试尝试
                        self.retry_tracker.log_retry_attempt(session_id, error, attempt + 1, delay)

                        # 发送重试尝试事件
                        if self.event_callback:
                            from datetime import datetime
                            await self.event_callback({
                                "type": "retry_attempt",
                                "timestamp": datetime.now().isoformat(),
                                "session_id": session_id,
                                "attempt": attempt + 1,
                                "max_attempts": self.config.max_retries + 1,
                                "error": str(error),
                                "error_type": type(error).__name__,
                                "delay_before_retry": delay,
                                "data": {
                                    "retryable": True,
                                    "next_attempt_in": delay
                                }
                            })

                        await asyncio.sleep(delay)

            result = await retry_wrapper()
            self.retry_tracker.end_retry_session(session_id, True)

            # 发送重试会话成功事件
            if self.event_callback:
                from datetime import datetime
                await self.event_callback({
                    "type": "retry_session_end",
                    "timestamp": datetime.now().isoformat(),
                    "session_id": session_id,
                    "success": True,
                    "data": self.retry_tracker.get_retry_summary(session_id)
                })

            return result

        except Exception as e:
            self.retry_tracker.end_retry_session(session_id, False, e)

            # 发送重试会话失败事件
            if self.event_callback:
                from datetime import datetime
                error_context = APIErrorClassifier.get_error_context(e)
                await self.event_callback({
                    "type": "retry_session_end",
                    "timestamp": datetime.now().isoformat(),
                    "session_id": session_id,
                    "success": False,
                    "error": str(e),
                    "error_type": type(e).__name__,
                    "error_context": error_context,
                    "data": self.retry_tracker.get_retry_summary(session_id)
                })

            raise e

    def __getattr__(self, name):
        """代理其他方法到底层ChatOpenAI实例"""
        return getattr(self._llm, name)
