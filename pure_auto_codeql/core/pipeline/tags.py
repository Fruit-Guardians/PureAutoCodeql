"""标签规范化工具。"""

import re


def sanitize_tag(value: str) -> str:
    cleaned = (value or "UNKNOWN").strip().upper()
    cleaned = re.sub(r"[^A-Z0-9_-]+", "_", cleaned)
    return cleaned or "UNKNOWN"
