"""CodeQL 生成类 Agent 的共享基类。

历史上 codeql_gen_agents 下的每个 Agent 都各自复制了一份 `_load_prompt`、
`_fill_placeholders` 以及 agent_start / agent_complete / error 事件发射样板。
该基类把这些公共逻辑集中到一处，子类只需实现各自的 build_prompt / 业务方法。
"""

from __future__ import annotations

import logging
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, Optional

logger = logging.getLogger(__name__)


class BasePromptAgent:
    """提供提示词加载、占位符填充与事件发射的公共能力。"""

    #: 子类可覆盖，作为事件中默认的 agent 名称 / 类型
    default_agent_name: str = "CodeQL Agent"
    default_agent_type: str = "codeql_agent"

    def __init__(self, analyzer: Any, prompt_file: Optional[Path] = None):
        self.analyzer = analyzer
        self.prompt_file = prompt_file

    def _load_prompt(self, prompt_file: Optional[Path] = None) -> str:
        """从 Markdown 文件加载提示词模板内容。"""
        target = prompt_file or self.prompt_file
        if target is None:
            return "Error loading prompt file: prompt_file 未配置"
        try:
            return Path(target).read_text(encoding="utf-8")
        except Exception as e:
            return f"Error loading prompt file: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        """用给定值替换模板中的 [[KEY]] 占位符。

        使用 [[...]] 标记而非 str.format，避免与 JSON / Markdown 中的花括号冲突。
        """
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, (v or ""))
        return result

    async def _emit_event(
        self,
        event_callback: Optional[Callable],
        event_type: str,
        message: str,
        *,
        agent_name: Optional[str] = None,
        agent_type: Optional[str] = None,
        data: Optional[Dict[str, Any]] = None,
    ) -> None:
        """发射一个标准结构的 Agent 事件（若提供了回调）。

        event_type 使用全局统一的取值：agent_start / agent_complete / error。
        """
        if not event_callback:
            return
        try:
            await event_callback({
                "type": event_type,
                "timestamp": datetime.now().isoformat(),
                "agent_name": agent_name or self.default_agent_name,
                "agent_type": agent_type or self.default_agent_type,
                "message": message,
                "data": data or {},
            })
        except Exception:
            logger.debug("发射 Agent 事件失败: %s", event_type, exc_info=True)
