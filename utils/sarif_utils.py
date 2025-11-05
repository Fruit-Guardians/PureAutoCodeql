"""Utilities for working with CodeQL SARIF path results."""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set
from urllib.parse import unquote, urlparse

__all__ = [
    "to_path",
    "sarif_to_paths",
    "write_paths_json",
    "collect_path_candidates",
    "filter_paths_by_sink_scope",
]


def to_path(uri: str, make_relative_to: str | None = None) -> str:
    """Normalise a SARIF artifactLocation URI to a filesystem-like path."""
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


def _get_region(location: Dict[str, Any], make_relative_to: Optional[str] = None) -> Dict[str, Any]:
    """Extract region metadata from a SARIF location record."""
    phys = (location or {}).get("physicalLocation", {}) or {}
    region = phys.get("region", {}) or {}
    artifact = (phys.get("artifactLocation", {}) or {}).get("uri", "")
    message = (location.get("message", {}) or {}).get("text", "") or ""

    return {
        "startLine": region.get("startLine") or 0,
        "startColumn": region.get("startColumn") or 0,
        "endColumn": region.get("endColumn") or 0,
        "file": to_path(artifact, make_relative_to=make_relative_to),
        "description": message,
    }


def _extract_threadflow_steps(threadflow: Dict[str, Any], make_relative_to: Optional[str] = None) -> List[Dict[str, Any]]:
    """Turn a threadFlow entry into the simplified step representation."""
    locations = (threadflow or {}).get("locations", []) or []
    steps: List[Dict[str, Any]] = []
    total = len(locations)

    for idx, item in enumerate(locations):
        region = _get_region(item.get("location", {}), make_relative_to=make_relative_to)
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


def _gather_threadflows(result: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Retrieve every threadFlow entry for a SARIF result."""
    thread_flows: List[Dict[str, Any]] = []
    code_flows = result.get("codeFlows", []) or []
    for cf in code_flows:
        thread_flows.extend(cf.get("threadFlows", []) or [])
    if not thread_flows:
        thread_flows = result.get("threadFlows", []) or []
    return thread_flows


def collect_path_candidates(
    sarif: Dict[str, Any],
    threadflow_index: int = -1,
    rule_filter: Optional[str] = None,
    make_relative_to: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """Collect every path candidate along with useful metadata."""

    runs = sarif.get("runs", []) or []
    candidates: List[Dict[str, Any]] = []

    for run_idx, run in enumerate(runs):
        driver = (run.get("tool", {}) or {}).get("driver", {}) or {}
        rules: Dict[str, Dict[str, Any]] = {}
        for rule in driver.get("rules", []) or []:
            rule_id = rule.get("id")
            if rule_id:
                rules[rule_id] = rule

        results = run.get("results", []) or []
        for result_idx, res in enumerate(results):
            rule_id = res.get("ruleId") or ""
            if rule_filter and rule_filter not in rule_id:
                continue

            thread_flows = _gather_threadflows(res)
            if not thread_flows:
                continue

            if threadflow_index >= 0:
                selected_indices = [min(threadflow_index, len(thread_flows) - 1)]
            else:
                selected_indices = range(len(thread_flows))

            rule_info = rules.get(rule_id, {})
            rule_props = (rule_info.get("properties") or {})
            result_props = (res.get("properties") or {})
            default_conf = (rule_info.get("defaultConfiguration") or {})

            for tf_order, idx in enumerate(selected_indices):
                thread_flow = thread_flows[idx]
                steps = _extract_threadflow_steps(thread_flow, make_relative_to=make_relative_to)
                if not steps:
                    continue

                source_file = steps[0]["location"]["file"]
                sink_file = steps[-1]["location"]["file"]

                candidate: Dict[str, Any] = {
                    "runIndex": run_idx,
                    "resultIndex": result_idx,
                    "threadFlowIndex": idx,
                    "selectionIndex": tf_order,
                    "ruleId": rule_id,
                    "ruleName": rule_info.get("name") or "",
                    "level": res.get("level") or default_conf.get("level") or "",
                    "precision": result_props.get("precision") or rule_props.get("precision") or "",
                    "securitySeverity": result_props.get("security-severity") or rule_props.get("security-severity") or "",
                    "message": (res.get("message", {}) or {}).get("text", "") or "",
                    "shortDescription": (rule_info.get("shortDescription", {}) or {}).get("text", "") or "",
                    "fullDescription": (rule_info.get("fullDescription", {}) or {}).get("text", "") or "",
                    "partialFingerprints": res.get("partialFingerprints", {}),
                    "steps": steps,
                    "pathLength": len(steps),
                    "sourceFile": source_file,
                    "sinkFile": sink_file,
                    "firstNodeType": steps[0]["location"].get("nodeType"),
                    "lastNodeType": steps[-1]["location"].get("nodeType"),
                }
                candidates.append(candidate)
    return candidates


def filter_paths_by_sink_scope(
    candidates: List[Dict[str, Any]],
    allowed_sink_files: Optional[Iterable[str]] = None,
    require_same_file: bool = False,
) -> List[Dict[str, Any]]:
    """First-stage filter that removes paths whose sink node is out of scope."""

    if not candidates:
        return []

    normalised_allowed: Optional[Set[str]] = None
    if allowed_sink_files:
        normalised_allowed = {to_path(path) for path in allowed_sink_files}

    filtered: List[Dict[str, Any]] = []
    for candidate in candidates:
        source_file = candidate.get("sourceFile", "")
        sink_file = candidate.get("sinkFile", "")
        if not sink_file:
            continue
        if require_same_file and sink_file != source_file:
            continue
        if normalised_allowed is not None and sink_file not in normalised_allowed:
            continue
        filtered.append(candidate)
    return filtered


def sarif_to_paths(
    sarif: Dict[str, Any],
    max_results: int,
    threadflow_index: int,
    rule_filter: Optional[str],
    make_relative_to: Optional[str],
) -> Dict[str, Any]:
    """Convert SARIF content to the legacy simplified JSON format."""

    limit = max(1, max_results or 0)
    candidates = collect_path_candidates(
        sarif,
        threadflow_index=threadflow_index,
        rule_filter=rule_filter,
        make_relative_to=make_relative_to,
    )
    trimmed = candidates[:limit]

    data_flow_paths: List[Dict[str, Any]] = []
    for candidate in trimmed:
        data_flow_paths.append({"threadFlows": [{"steps": candidate["steps"]}]})
    return {"dataFlowPath": data_flow_paths}


def write_paths_json(
    sarif_path: str,
    json_out: str,
    max_results: int,
    threadflow_index: int,
    rule_filter: Optional[str],
    relative_to: Optional[str],
    all_json_out: Optional[str] = None,
    filter_same_file: bool = False,
    allowed_sink_files: Optional[Iterable[str]] = None,
    write_all_paths: bool = True,
) -> int:
    """Read SARIF and write both the simplified JSON and the full candidate dump."""

    sarif_file = Path(sarif_path)
    if not sarif_file.exists():
        raise FileNotFoundError(f"未找到 SARIF 文件: {sarif_path}")

    with sarif_file.open("r", encoding="utf-8") as handler:
        sarif = json.load(handler)

    candidates = collect_path_candidates(
        sarif,
        threadflow_index=threadflow_index,
        rule_filter=rule_filter,
        make_relative_to=relative_to,
    )

    if write_all_paths:
        target_path = all_json_out or str(sarif_file.with_suffix(".all_paths.json"))
        try:
            all_json_file = Path(target_path)
            all_json_file.parent.mkdir(parents=True, exist_ok=True)
            with all_json_file.open("w", encoding="utf-8") as handler:
                json.dump({"paths": candidates}, handler, ensure_ascii=False, indent=2)
        except Exception:
            pass

    filtered_candidates = filter_paths_by_sink_scope(
        candidates,
        allowed_sink_files=allowed_sink_files,
        require_same_file=filter_same_file,
    )

    limit = max(1, max_results or 0)
    trimmed = filtered_candidates[:limit] if filtered_candidates else []
    data = {"dataFlowPath": [{"threadFlows": [{"steps": candidate["steps"]}]} for candidate in trimmed]}

    json_file = Path(json_out)
    json_file.parent.mkdir(parents=True, exist_ok=True)
    with json_file.open("w", encoding="utf-8") as handler:
        json.dump(data, handler, ensure_ascii=False, indent=2)

    return len(data.get("dataFlowPath", []) or [])
