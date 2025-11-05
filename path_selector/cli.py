"""CLI entry that delegates to PathSelectionAgent."""

from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
from typing import List, Optional

from Analyze import MultiAgentAnalyzer  # type: ignore
from path_selector.agent import PathSelectionAgent
from path_selector.selector import build_dataflow_json, build_report


def _normalize_list(values: Optional[List[str]]) -> Optional[List[str]]:
    if not values:
        return None
    items = [value for value in values if value]
    return items or None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Select optimal data-flow paths from a CodeQL SARIF file.")
    parser.add_argument("--sarif", required=True, help="Path to the input SARIF file.")
    parser.add_argument("--out", default="result.json", help="Output JSON path for the selected paths.")
    parser.add_argument("--report", help="Optional detailed report path.")
    parser.add_argument("--cve-summary", help="Inline CVE summary text.")
    parser.add_argument("--cve-file", help="File containing CVE summary/analysis.")
    parser.add_argument("--allowed-sink", nargs="*", help="List of sink files to keep (first-stage filter).")
    parser.add_argument("--require-same-file", action="store_true", help="Keep only paths whose source and sink share the same file.")
    parser.add_argument("--max-candidates", type=int, help="Limit number of candidates reviewed by the model.")
    parser.add_argument("--top-k", type=int, default=3, help="Number of optimal paths to keep. Default: 3")
    parser.add_argument("--threadflow-index", type=int, default=-1, help="ThreadFlow selection (-1 for all).")
    parser.add_argument("--rule-filter", help="Only consider results whose ruleId contains this substring.")
    parser.add_argument("--relative-to", help="Make emitted paths relative to this directory.")
    parser.add_argument("--repo-root", help="Repository root (defaults to current working directory).")
    parser.add_argument("--source-root", help="Source root for resolving file paths (defaults to repo root).")
    parser.add_argument("--context-radius", type=int, default=5, help="Lines of context to capture around each step (default: 5).")
    parser.add_argument("--skip-llm", action="store_true", help="Skip LLM review and rely on heuristics only.")
    parser.add_argument("--show-thinking", action="store_true", help="Print intermediate reasoning events from the agent.")
    return parser.parse_args()


def _load_cve_summary(args: argparse.Namespace) -> str:
    parts: List[str] = []
    if args.cve_summary:
        parts.append(args.cve_summary)
    if args.cve_file:
        parts.append(Path(args.cve_file).read_text(encoding="utf-8"))
    return "\n".join(part.strip() for part in parts if part.strip())


async def _run(args: argparse.Namespace) -> None:
    analyzer = MultiAgentAnalyzer()
    agent = PathSelectionAgent(
        analyzer=analyzer,
        repo_root=args.repo_root,
        source_root=args.source_root,
        context_radius=args.context_radius,
    )

    sarif_path = Path(args.sarif)
    cve_summary = _load_cve_summary(args)

    summary = await agent.select_from_file(
        sarif_path,
        cve_summary,
        top_k=args.top_k,
        max_candidates=args.max_candidates,
        threadflow_index=args.threadflow_index,
        rule_filter=args.rule_filter,
        make_relative_to=args.relative_to,
        allowed_sink_files=_normalize_list(args.allowed_sink),
        require_same_file=args.require_same_file,
        use_llm=not args.skip_llm,
        show_thinking=args.show_thinking,
    )

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_data = build_dataflow_json(summary.selected)
    out_path.write_text(json.dumps(out_data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已输出 {len(summary.selected)} 条候选路径 -> {out_path}")

    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(build_report(summary), ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"详细报告已保存 -> {report_path}")


def main() -> None:
    args = parse_args()
    asyncio.run(_run(args))


if __name__ == "__main__":
    main()