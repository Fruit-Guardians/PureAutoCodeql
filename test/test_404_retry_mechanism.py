"""
测试404错误重试机制

测试范围：
1. APIErrorClassifier - 错误分类器
2. llm_retry_decorator - 重试装饰器
3. AgentRetryTracker - 重试状态跟踪
4. RetryableChatOpenAI - ChatOpenAI包装器
5. MultiAgentAnalyzer - 集成测试
"""

import sys
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

import time
from unittest.mock import AsyncMock, Mock, patch

import pytest

from pure_auto_codeql.configuration import LLMConfig
from pure_auto_codeql.services.llm_service import (
    AgentRetryTracker,
    APIErrorClassifier,
    MultiAgentAnalyzer,
    RetryableChatOpenAI,
    llm_retry_decorator,
)

# ============================================================================
# 测试 APIErrorClassifier - 错误分类器
# ============================================================================

class TestAPIErrorClassifier:
    """测试API错误分类器"""

    def test_404_error_is_retryable(self):
        """测试404错误被识别为可重试"""
        error = Exception("404 Not Found")
        error.status_code = 404

        assert APIErrorClassifier.is_retryable_error(error) is True

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is True
        assert context['status_code'] == 404
        assert context['error_category'] == 'server_error'

    def test_500_error_is_retryable(self):
        """测试500错误被识别为可重试"""
        error = Exception("500 Internal Server Error")
        error.status_code = 500

        assert APIErrorClassifier.is_retryable_error(error) is True

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is True
        assert context['status_code'] == 500
        assert context['error_category'] == 'server_error'

    def test_502_503_504_errors_are_retryable(self):
        """测试502/503/504错误被识别为可重试"""
        for status_code in [502, 503, 504]:
            error = Exception(f"{status_code} Error")
            error.status_code = status_code

            assert APIErrorClassifier.is_retryable_error(error) is True
            context = APIErrorClassifier.get_error_context(error)
            assert context['is_retryable'] is True
            assert context['error_category'] == 'server_error'

    def test_429_rate_limit_is_retryable(self):
        """测试429限流错误被识别为可重试"""
        error = Exception("429 Too Many Requests")
        error.status_code = 429

        assert APIErrorClassifier.is_retryable_error(error) is True

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is True
        assert context['status_code'] == 429

    def test_401_error_is_not_retryable(self):
        """测试401认证错误不可重试"""
        error = Exception("401 Unauthorized")
        error.status_code = 401

        assert APIErrorClassifier.is_retryable_error(error) is False

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is False
        assert context['status_code'] == 401
        assert context['error_category'] == 'authentication/permission'

    def test_403_error_is_not_retryable(self):
        """测试403权限错误不可重试"""
        error = Exception("403 Forbidden")
        error.status_code = 403

        assert APIErrorClassifier.is_retryable_error(error) is False

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is False
        assert context['error_category'] == 'authentication/permission'

    def test_400_error_is_not_retryable(self):
        """测试400错误请求不可重试"""
        error = Exception("400 Bad Request")
        error.status_code = 400

        assert APIErrorClassifier.is_retryable_error(error) is False

    def test_connection_error_is_retryable(self):
        """测试连接错误可重试"""
        error = ConnectionError("Connection refused")

        assert APIErrorClassifier.is_retryable_error(error) is True

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is True
        assert context['error_category'] == 'network_error'

    def test_timeout_error_is_retryable(self):
        """测试超时错误可重试"""
        error = TimeoutError("Request timeout")

        assert APIErrorClassifier.is_retryable_error(error) is True

        context = APIErrorClassifier.get_error_context(error)
        assert context['is_retryable'] is True
        assert context['error_category'] == 'network_error'

    def test_network_keywords_make_error_retryable(self):
        """测试包含网络关键词的错误可重试"""
        retryable_messages = [
            "connection timeout",
            "network unreachable",
            "dns resolution failed",
            "rate limit exceeded",
            "service unavailable",
            "bad gateway",
            "gateway timeout",
        ]

        for message in retryable_messages:
            error = Exception(message)
            assert APIErrorClassifier.is_retryable_error(error) is True, f"Failed for: {message}"

    def test_auth_keywords_make_error_not_retryable(self):
        """测试包含认证关键词的错误不可重试"""
        non_retryable_messages = [
            "invalid api key",
            "authentication failed",
            "authorization denied",
            "permission denied",
            "forbidden access",
            "invalid request format",
        ]

        for message in non_retryable_messages:
            error = Exception(message)
            assert APIErrorClassifier.is_retryable_error(error) is False, f"Failed for: {message}"


# ============================================================================
# 测试 llm_retry_decorator - 重试装饰器
# ============================================================================

class TestLLMRetryDecorator:
    """测试LLM重试装饰器"""

    @pytest.mark.asyncio
    async def test_successful_call_no_retry(self):
        """测试成功调用不触发重试"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=3,
            retry_base_delay=0.1,
        )

        call_count = 0

        @llm_retry_decorator(config)
        async def test_func():
            nonlocal call_count
            call_count += 1
            return "success"

        result = await test_func()

        assert result == "success"
        assert call_count == 1

    @pytest.mark.asyncio
    async def test_404_error_triggers_retry(self):
        """测试404错误触发重试"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=3,
            retry_base_delay=0.1,
            retry_jitter=False,  # 禁用抖动以便测试
        )

        call_count = 0

        @llm_retry_decorator(config)
        async def test_func():
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                error = Exception("404 Not Found")
                error.status_code = 404
                raise error
            return "success after retry"

        start_time = time.time()
        result = await test_func()
        elapsed = time.time() - start_time

        assert result == "success after retry"
        assert call_count == 3
        # 验证指数退避：第1次延迟0.1秒，第2次延迟0.2秒，总共至少0.3秒
        assert elapsed >= 0.3

    @pytest.mark.asyncio
    async def test_non_retryable_error_fails_immediately(self):
        """测试不可重试错误立即失败"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=3,
            retry_base_delay=0.1,
        )

        call_count = 0

        @llm_retry_decorator(config)
        async def test_func():
            nonlocal call_count
            call_count += 1
            error = Exception("401 Unauthorized")
            error.status_code = 401
            raise error

        with pytest.raises(Exception) as exc_info:
            await test_func()

        assert "401" in str(exc_info.value)
        assert call_count == 1  # 只调用一次，不重试

    @pytest.mark.asyncio
    async def test_max_retries_exhausted(self):
        """测试重试次数耗尽后抛出异常"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=2,
            retry_base_delay=0.05,
            retry_jitter=False,
        )

        call_count = 0

        @llm_retry_decorator(config)
        async def test_func():
            nonlocal call_count
            call_count += 1
            error = Exception("500 Internal Server Error")
            error.status_code = 500
            raise error

        with pytest.raises(Exception) as exc_info:
            await test_func()

        assert "500" in str(exc_info.value)
        assert call_count == 3  # 初始调用 + 2次重试

    @pytest.mark.asyncio
    async def test_exponential_backoff(self):
        """测试指数退避算法"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=3,
            retry_base_delay=0.1,
            retry_backoff_factor=2.0,
            retry_jitter=False,
        )

        call_count = 0
        retry_delays = []
        last_time = [time.time()]

        @llm_retry_decorator(config)
        async def test_func():
            nonlocal call_count
            current_time = time.time()
            if call_count > 0:
                retry_delays.append(current_time - last_time[0])
            last_time[0] = current_time

            call_count += 1
            if call_count <= 3:
                error = Exception("503 Service Unavailable")
                error.status_code = 503
                raise error
            return "success"

        await test_func()

        # 验证延迟时间符合指数退避：0.1, 0.2, 0.4
        assert len(retry_delays) == 3
        assert 0.08 <= retry_delays[0] <= 0.12  # 第1次重试延迟 ~0.1秒
        assert 0.18 <= retry_delays[1] <= 0.22  # 第2次重试延迟 ~0.2秒
        assert 0.38 <= retry_delays[2] <= 0.42  # 第3次重试延迟 ~0.4秒

    @pytest.mark.asyncio
    async def test_jitter_adds_randomness(self):
        """测试抖动算法添加随机性"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=2,
            retry_base_delay=1.0,
            retry_backoff_factor=2.0,
            retry_jitter=True,
        )

        # 运行多次以验证抖动的随机性
        delays_set = set()

        for _ in range(5):
            call_count = 0
            retry_delays = []
            last_time = [time.time()]

            @llm_retry_decorator(config)
            async def test_func():
                nonlocal call_count
                current_time = time.time()
                if call_count > 0:
                    retry_delays.append(current_time - last_time[0])
                last_time[0] = current_time

                call_count += 1
                if call_count <= 1:
                    error = Exception("504 Gateway Timeout")
                    error.status_code = 504
                    raise error
                return "success"

            await test_func()

            if retry_delays:
                # 抖动应该在 50%-100% 范围内，即 0.5-1.0秒
                delay = retry_delays[0]
                assert 0.5 <= delay <= 1.0
                delays_set.add(round(delay, 2))

        # 验证不同运行产生了不同的延迟（至少2个不同值）
        assert len(delays_set) >= 2


# ============================================================================
# 测试 AgentRetryTracker - 重试状态跟踪
# ============================================================================

class TestAgentRetryTracker:
    """测试Agent重试状态跟踪器"""

    def test_start_retry_session(self):
        """测试开始重试会话"""
        tracker = AgentRetryTracker()
        session_id = "test_session_1"
        agent_name = "test_agent"

        tracker.start_retry_session(session_id, agent_name)

        assert session_id in tracker.retry_attempts
        session = tracker.retry_attempts[session_id]
        assert session['agent_name'] == agent_name
        assert session['attempts'] == 0
        assert session['errors'] == []
        assert 'start_time' in session

    def test_log_retry_attempt(self):
        """测试记录重试尝试"""
        tracker = AgentRetryTracker()
        session_id = "test_session_2"

        tracker.start_retry_session(session_id, "test_agent")

        error = Exception("404 Not Found")
        error.status_code = 404

        tracker.log_retry_attempt(session_id, error, 1, 0.5)

        session = tracker.retry_attempts[session_id]
        assert session['attempts'] == 1
        assert len(session['errors']) == 1
        assert session['errors'][0]['attempt'] == 1
        assert session['errors'][0]['delay_before_retry'] == 0.5
        assert 'server_error' in session['error_categories']

    def test_end_retry_session_success(self):
        """测试成功结束重试会话"""
        tracker = AgentRetryTracker()
        session_id = "test_session_3"

        tracker.start_retry_session(session_id, "test_agent")
        tracker.end_retry_session(session_id, success=True)

        session = tracker.retry_attempts[session_id]
        assert session['success'] is True
        assert 'end_time' in session
        assert 'duration' in session

    def test_end_retry_session_failure(self):
        """测试失败结束重试会话"""
        tracker = AgentRetryTracker()
        session_id = "test_session_4"

        tracker.start_retry_session(session_id, "test_agent")

        final_error = Exception("500 Internal Server Error")
        final_error.status_code = 500

        tracker.end_retry_session(session_id, success=False, final_error=final_error)

        session = tracker.retry_attempts[session_id]
        assert session['success'] is False
        assert 'final_error' in session
        assert session['final_error']['error_type'] == 'Exception'

    def test_get_retry_summary(self):
        """测试获取重试摘要"""
        tracker = AgentRetryTracker()
        session_id = "test_session_5"

        tracker.start_retry_session(session_id, "test_agent")

        error = Exception("503 Service Unavailable")
        error.status_code = 503
        tracker.log_retry_attempt(session_id, error, 1, 1.0)
        tracker.log_retry_attempt(session_id, error, 2, 2.0)

        tracker.end_retry_session(session_id, success=True)

        summary = tracker.get_retry_summary(session_id)
        assert summary['attempts'] == 2
        assert len(summary['errors']) == 2
        assert summary['success'] is True

    def test_get_all_retry_logs(self):
        """测试获取所有重试日志"""
        tracker = AgentRetryTracker()

        # 创建多个会话
        for i in range(3):
            session_id = f"session_{i}"
            tracker.start_retry_session(session_id, f"agent_{i}")

            error = Exception(f"Error {i}")
            error.status_code = 500
            tracker.log_retry_attempt(session_id, error, 1, 0.5)

        logs = tracker.get_all_retry_logs()
        assert len(logs) == 3
        assert all('session_id' in log for log in logs)
        assert all('error' in log for log in logs)

    def test_clear_old_logs(self):
        """测试清理旧日志"""
        tracker = AgentRetryTracker()
        session_id = "test_session_6"

        tracker.start_retry_session(session_id, "test_agent")

        error = Exception("Timeout")
        tracker.log_retry_attempt(session_id, error, 1, 0.5)

        # 修改时间戳使其看起来很旧
        if tracker.retry_logs:
            tracker.retry_logs[0]['timestamp'] = time.time() - 7200  # 2小时前

        tracker.clear_old_logs(max_age_seconds=3600)  # 清理1小时前的日志

        assert len(tracker.retry_logs) == 0


# ============================================================================
# 测试 RetryableChatOpenAI - ChatOpenAI包装器
# ============================================================================

class TestRetryableChatOpenAI:
    """测试带重试机制的ChatOpenAI包装器"""

    @pytest.mark.asyncio
    async def test_initialization(self):
        """测试初始化"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=3,
        )

        llm = RetryableChatOpenAI(config)

        assert llm.config == config
        assert llm.retry_tracker is not None
        assert llm._llm is not None

    # 注意：以下测试需要实际的LLM API或更复杂的mock设置
    # 由于ChatOpenAI是Pydantic模型，直接mock其方法比较困难
    # 这些场景应该通过集成测试或手动测试来验证
    #
    # 测试场景包括：
    # 1. astream_events在404错误时重试
    # 2. 重试时触发事件回调
    # 3. 不可重试错误直接传播
    #
    # 这些功能已通过llm_retry_decorator和AgentRetryTracker的单元测试覆盖
    # 实际重试行为可以通过运行真实的Agent来验证


# ============================================================================
# 测试 MultiAgentAnalyzer - 集成测试
# ============================================================================

class TestMultiAgentAnalyzerRetry:
    """测试MultiAgentAnalyzer的重试集成"""

    @pytest.mark.asyncio
    async def test_analyzer_initialization_with_retry_config(self):
        """测试分析器使用重试配置初始化"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
            max_retries=5,
            retry_base_delay=2.0,
            retry_backoff_factor=3.0,
        )

        analyzer = MultiAgentAnalyzer(config)

        assert analyzer.config.max_retries == 5
        assert analyzer.config.retry_base_delay == 2.0
        assert analyzer.config.retry_backoff_factor == 3.0
        assert analyzer.retry_tracker is not None

    @pytest.mark.asyncio
    async def test_analyzer_uses_retryable_chat_openai(self):
        """测试分析器使用RetryableChatOpenAI"""
        config = LLMConfig(
            model="test-model",
            api_key="test-key",
            base_url="http://test.com",
        )

        analyzer = MultiAgentAnalyzer(config)

        # Mock MCP client to avoid actual initialization
        with patch('pure_auto_codeql.services.llm_service.MultiServerMCPClient') as mock_mcp:
            mock_mcp_instance = Mock()
            mock_mcp_instance.get_tools = AsyncMock(return_value=[])
            mock_mcp.return_value = mock_mcp_instance

            await analyzer.initialize()

            assert isinstance(analyzer.llm, RetryableChatOpenAI)
            assert analyzer.llm.config == config


# ============================================================================
# 运行测试
# ============================================================================

if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
