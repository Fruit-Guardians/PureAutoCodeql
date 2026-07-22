"""针对本轮质量优化所修复问题的回归测试。

覆盖：
- 验证 Agent 统一为三元返回，且 sink/source 行为参数化正确（防 arize 漂移复发）
- codeql 生成类 Agent 共享基类的占位符/事件行为
- cve_fetcher 使用 NVD 2.0 字段校验
- codeql._format_db_error 复用
- API 不安全配置告警
- SSE 历史事件回放
"""

import asyncio


# ---------------------------------------------------------------------------
# 验证 Agent：三元返回 + sink/source 参数化
# ---------------------------------------------------------------------------
def test_verification_agents_share_base_and_params():
    from pure_auto_codeql.agents.base_verification_agent import BaseVerificationAgent
    from pure_auto_codeql.agents.sink_verification_agent import SinkVerificationAgent
    from pure_auto_codeql.agents.source_verification_agent import SourceVerificationAgent

    assert issubclass(SinkVerificationAgent, BaseVerificationAgent)
    assert issubclass(SourceVerificationAgent, BaseVerificationAgent)

    sink = SinkVerificationAgent(analyzer=None, database_path="db", language="Java")
    src = SourceVerificationAgent(analyzer=None, database_path="db", language="Java")

    assert sink.kind == "sink" and sink.label == "Sink"
    assert src.kind == "source" and src.label == "Source"
    assert sink._default_agent_name == "Sink Verification Agent"
    assert src._default_agent_type == "source_verification"


def test_verification_missing_template_returns_three_tuple():
    """模板加载失败时必须返回 3 元组，否则 pipeline 三元解包会 ValueError。"""
    from pure_auto_codeql.agents.sink_verification_agent import SinkVerificationAgent
    from pure_auto_codeql.agents.source_verification_agent import SourceVerificationAgent

    for cls in (SinkVerificationAgent, SourceVerificationAgent):
        agent = cls(analyzer=None, database_path="db", language="java")
        # 强制模板加载抛错
        agent._load_verification_template = lambda: (_ for _ in ()).throw(RuntimeError("boom"))
        result = asyncio.run(agent.verify_analysis_result("{}"))
        assert isinstance(result, tuple) and len(result) == 3
        is_valid, error_message, verification_query = result  # 必须可三元解包
        assert is_valid is False
        assert "boom" in error_message
        assert verification_query is None


def test_verification_requirement_and_extract():
    from pure_auto_codeql.agents.sink_verification_agent import SinkVerificationAgent

    agent = SinkVerificationAgent(analyzer=None, database_path="db", language="java")
    req = agent._build_requirement("ANALYSIS")
    assert "Sink 分析结果" in req and "ANALYSIS" in req

    extracted = agent._extract_codeql_from_response("```ql\nimport java\nfrom X\n```")
    assert extracted == "import java\nfrom X"


# ---------------------------------------------------------------------------
# codeql 生成类 Agent 共享基类
# ---------------------------------------------------------------------------
def test_prompt_agent_fill_placeholders_and_emit():
    from pure_auto_codeql.agents.codeql_gen_agents.base import BasePromptAgent
    from pure_auto_codeql.agents.codeql_gen_agents.codeql_gen_agent import CodeQLGenAgent

    agent = CodeQLGenAgent(analyzer=None)
    assert isinstance(agent, BasePromptAgent)

    out = agent._fill_placeholders("a=[[A]] b=[[B]] c=[[C]]", {"A": "1", "B": None, "C": "3"})
    assert out == "a=1 b= c=3"

    # 事件收集：确认 event_type 与统一命名一致
    events = []

    async def cb(evt):
        events.append(evt)

    asyncio.run(agent._emit_event(cb, "agent_complete", "done", data={"ok": True}))
    assert len(events) == 1
    assert events[0]["type"] == "agent_complete"
    assert events[0]["agent_name"] == "CodeQL Generation Agent"
    assert events[0]["data"] == {"ok": True}

    # 无回调时应为 no-op（不抛异常）
    asyncio.run(agent._emit_event(None, "agent_start", "x"))


def test_gen_agents_use_canonical_agent_result():
    """生成类 Agent 应使用 llm_service.AgentResult，而非各自的本地重定义。"""
    import pure_auto_codeql.agents.codeql_gen_agents.codeql_gen_agent as gen_mod
    from pure_auto_codeql.services.llm_service import AgentResult

    assert gen_mod.AgentResult is AgentResult


# ---------------------------------------------------------------------------
# cve_fetcher：NVD 2.0 字段
# ---------------------------------------------------------------------------
def test_validate_cve_data_accepts_nvd2_fields():
    from pure_auto_codeql.utils.cve_fetcher import validate_cve_data

    good = {
        "resultsPerPage": 1,
        "totalResults": 1,
        "vulnerabilities": [
            {
                "cve": {
                    "id": "CVE-2021-0000",
                    "vulnStatus": "Analyzed",
                    "published": "2021-01-01T00:00:00",
                    "lastModified": "2021-02-01T00:00:00",
                    # 故意不含旧的 state / modified 字段，验证不再被要求
                }
            }
        ],
    }
    assert validate_cve_data(good) is True

    # 使用旧字段 state/modified 而缺少 NVD 2.0 字段时应校验失败
    legacy_only = {
        "resultsPerPage": 1,
        "totalResults": 1,
        "vulnerabilities": [
            {
                "cve": {
                    "id": "CVE-2021-0001",
                    "state": "PUBLISHED",
                    "published": "2021-01-01T00:00:00",
                    "modified": "2021-02-01T00:00:00",
                }
            }
        ],
    }
    assert validate_cve_data(legacy_only) is False


# ---------------------------------------------------------------------------
# codeql._format_db_error
# ---------------------------------------------------------------------------
def test_format_db_error_includes_path_and_suggestions():
    from pure_auto_codeql.utils.codeql import _format_db_error

    msg = _format_db_error("some failure", "/tmp/db")
    assert "数据库错误" in msg
    assert "some failure" in msg
    assert "/tmp/db" in msg
    assert "codeql database create" in msg


# ---------------------------------------------------------------------------
# API 不安全配置告警
# ---------------------------------------------------------------------------
def test_insecure_posture_warns_on_public_bind_without_token(caplog):
    import logging

    from pure_auto_codeql.api import server

    class _Cfg:
        host = "0.0.0.0"
        auth_token = ""
        workers = 1

    with caplog.at_level(logging.WARNING):
        server._warn_on_insecure_posture(_Cfg())
    assert any("非回环地址" in r.message or "auth_token" in r.message for r in caplog.records)


def test_insecure_posture_warns_on_multiworker(caplog):
    import logging

    from pure_auto_codeql.api import server

    class _Cfg:
        host = "127.0.0.1"
        auth_token = "secret"
        workers = 4

    with caplog.at_level(logging.WARNING):
        server._warn_on_insecure_posture(_Cfg())
    assert any("workers" in r.message for r in caplog.records)


# ---------------------------------------------------------------------------
# SSE 历史事件回放
# ---------------------------------------------------------------------------
def test_task_manager_creates_event_queue_on_create():
    """create_task 时即应建立事件队列，避免排队中的任务 SSE 连接 404。"""
    from pure_auto_codeql.api.task_manager import TaskManager

    tm = TaskManager()
    task_id = tm.create_task("CVE-TEST")
    assert task_id in tm._event_queues
    assert task_id in tm._task_events
