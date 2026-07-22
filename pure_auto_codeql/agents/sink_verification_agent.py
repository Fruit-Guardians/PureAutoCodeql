"""Sink 验证 Agent - 生成并执行 Sink 验证查询。

该模块实现了 Sink 点的验证逻辑，通过生成简单的 CodeQL 查询来验证
LLM 识别的 Sink 点是否真实存在于代码库中。公共流程见 BaseVerificationAgent。
"""

from pure_auto_codeql.agents.base_verification_agent import BaseVerificationAgent


class SinkVerificationAgent(BaseVerificationAgent):
    """Sink 点验证 Agent，生成并执行 isSink 验证查询。"""

    kind = "sink"
    label = "Sink"
    name_check_hint = "类名"
