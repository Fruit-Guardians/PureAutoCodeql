"""Source 验证 Agent - 生成并执行 Source 验证查询。

该模块实现了 Source 点的验证逻辑，通过生成简单的 CodeQL 查询来验证
LLM 识别的 Source 点是否真实存在于代码库中。公共流程见 BaseVerificationAgent。
"""

from pure_auto_codeql.agents.base_verification_agent import BaseVerificationAgent


class SourceVerificationAgent(BaseVerificationAgent):
    """Source 点验证 Agent，生成并执行 isSource 验证查询。"""

    kind = "source"
    label = "Source"
    name_check_hint = "参数名"
