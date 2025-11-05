"""Prompt builders for LLM-based path validation."""

from __future__ import annotations

from typing import Dict, List, Optional


def _format_step(step: Dict[str, any]) -> str:
    location = step.get("location", {}) or {}
    file_path = location.get("file", "")
    line = location.get("startLine", 0)
    node_type = location.get("nodeType", "Unknown")
    desc = (location.get("description") or "").strip().replace("\n", " ")
    desc = desc[:160] + ("..." if len(desc) > 160 else "")
    return f"- Step {step.get('stepNumber', '?')} [{node_type}] {file_path}:{line} -> {desc}"


def build_path_review_prompt(
    cve_summary: str,
    candidate: Dict[str, any],
    contexts: Optional[List[Dict[str, any]]] = None,
    max_steps: int = 16,
) -> str:
    """Return a structured instruction for the path-evaluation LLM."""

    steps: List[Dict[str, any]] = candidate.get("steps", [])[:max_steps]
    step_lines = "\n".join(_format_step(step) for step in steps)
    rule_id = candidate.get("ruleId") or "(unknown rule)"
    severity = candidate.get("level") or candidate.get("securitySeverity") or "unknown"
    path_len = candidate.get("pathLength") or len(steps)
    message = (candidate.get("message") or "").strip()

    summary = cve_summary.strip() if cve_summary else "未提供 CVE 摘要。"

    context_lines: List[str] = []
    for ctx in contexts or []:
        snippet = (ctx.get("snippet") or "").rstrip()
        if not snippet:
            continue
        file_path = ctx.get("file") or ""
        line_span = ctx.get("line_span") or ""
        header = f"[{file_path}:{line_span}]"
        context_lines.append(f"{header}\n{snippet}")
    context_block = "\n\n".join(context_lines) if context_lines else "(未提取到源码上下文)"

    return (
        "你是一名高级漏洞分析师，需要判断数据流路径是否完整体现目标漏洞。"
        "必须严格按照要求输出 JSON，键名使用英文小写，不要附加额外解释。\n\n"
        f"CVE 概要:\n{summary}\n\n"
        f"规则: {rule_id} | 严重级别: {severity} | 步数: {path_len}\n"
        f"Result message: {message}\n\n"
        "路径节点:\n"
        f"{step_lines}\n\n"
        "相关源码片段:\n"
        f"{context_block}\n\n"
        "统一输出 JSON 格式如下（不要包含 Markdown 代码块）:\n"
        '{'
        '"status": "valid|partial|invalid", '
        '"reason": "简要说明", '
        '"missing_checks": ["缺失检查"...], '
        '"confidence": 0.0-1.0'
        "}\n"
    )
