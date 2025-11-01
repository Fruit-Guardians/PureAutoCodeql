"""SARIF 转 JSON 的工具函数，供复用与自动转换使用。"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse, unquote

__all__ = [
    "to_path",
    "sarif_to_paths",
    "write_paths_json",
]


def to_path(uri: str, make_relative_to: str | None = None) -> str:
    """将 SARIF 中的 URI 规范化为文件路径。"""
    if not uri:
        return ""
    parsed = urlparse(uri)
    if parsed.scheme in ("file", ""):
        path = unquote(parsed.path or uri)
        if os.name == "nt" and path.startswith("/") and len(path) > 3 and path[2] == ":":
            path = path[1:]
    else:
        path = unquote(uri)

    path = path.replace("\\", "/")
    if make_relative_to:
        try:
            path = os.path.relpath(path, start=make_relative_to).replace("\\", "/")
        except Exception:
            pass
    return path


def _get_region(location: Dict[str, Any]) -> Dict[str, Any]:
    """提取物理位置信息，形成统一的区域描述。"""
    phys = (location or {}).get("physicalLocation", {})
    region = phys.get("region", {}) or {}
    return {
        "startLine": region.get("startLine") or 0,
        "startColumn": region.get("startColumn") or 0,
        "endColumn": region.get("endColumn") or 0,
        "file": to_path((phys.get("artifactLocation", {}) or {}).get("uri", "")),
        "description": (location.get("message", {}) or {}).get("text", "") or "",
    }


def _extract_threadflow_steps(threadflow: Dict[str, Any]) -> List[Dict[str, Any]]:
    """将 threadFlow 转换为步骤列表，并标注 Source/Sink。"""
    locations = (threadflow or {}).get("locations", []) or []
    steps: List[Dict[str, Any]] = []
    total = len(locations)
    for idx, item in enumerate(locations):
        region = _get_region(item.get("location", {}))
        node_type = "Intermediate"
        if idx == 0:
            node_type = "Source"
        elif idx == total - 1:
            node_type = "Sink"
        steps.append(
            {
                "stepNumber": idx + 1,
                "location": {
                    "file": region["file"],
                    "startLine": region["startLine"],
                    "startColumn": region["startColumn"],
                    "endColumn": region["endColumn"],
                    "description": region["description"],
                    "nodeType": node_type,
                },
            }
        )
    return steps


def sarif_to_paths(
    sarif: Dict[str, Any],
    max_results: int,
    threadflow_index: int,
    rule_filter: Optional[str],
    make_relative_to: Optional[str],
) -> Dict[str, Any]:
    """将 SARIF 数据转换为路径 JSON 结构。"""
    limit = max(1, max_results or 0)
    tf_index = max(0, threadflow_index or 0)

    output: Dict[str, Any] = {"dataFlowPath": []}
    runs = sarif.get("runs", []) or []
    for run in runs:
        results = run.get("results", []) or []
        if rule_filter:
            results = [r for r in results if rule_filter in (r.get("ruleId") or "")]
        for res in results[:limit]:
            code_flows = res.get("codeFlows", []) or []
            thread_flows = []
            if code_flows:
                thread_flows = code_flows[0].get("threadFlows", []) or []
            if not thread_flows:
                thread_flows = res.get("threadFlows", []) or []
            if not thread_flows:
                continue

            thread_flow = thread_flows[min(tf_index, len(thread_flows) - 1)]
            steps = _extract_threadflow_steps(thread_flow)
            if make_relative_to:
                for step in steps:
                    step["location"]["file"] = to_path(
                        step["location"].get("file", ""),
                        make_relative_to=make_relative_to,
                    )
            output["dataFlowPath"].append({"threadFlows": [{"steps": steps}]})
    return output


def write_paths_json(
    sarif_path: str,
    json_out: str,
    max_results: int,
    threadflow_index: int,
    rule_filter: Optional[str],
    relative_to: Optional[str],
) -> int:
    """读取 SARIF 并写出路径 JSON，返回写入的路径数量。"""
    sarif_file = Path(sarif_path)
    if not sarif_file.exists():
        raise FileNotFoundError(f"未找到 SARIF 文件: {sarif_path}")

    with sarif_file.open("r", encoding="utf-8") as handler:
        sarif = json.load(handler)

    data = sarif_to_paths(
        sarif,
        max_results=max_results,
        threadflow_index=threadflow_index,
        rule_filter=rule_filter,
        make_relative_to=relative_to,
    )

    json_file = Path(json_out)
    json_file.parent.mkdir(parents=True, exist_ok=True)
    with json_file.open("w", encoding="utf-8") as handler:
        json.dump(data, handler, ensure_ascii=False, indent=2)

    return len(data.get("dataFlowPath", []) or [])
