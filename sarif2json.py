#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Convert CodeQL SARIF to:
{
  "dataFlowPath": [
    { "threadFlows": [ { "steps": [ ... ] } ] },
    ...
  ]
}

- 仅处理 path-problem 结果（包含 codeFlows/threadFlows/locations）
- 默认最多输出 3 条路径（可用 --max-results 调整）
- 每条结果仅取第 1 个 threadFlow（可用 --threadflow-index 调整）
"""

import argparse
import sys

from utils.sarif_utils import sarif_to_paths


def main() -> None:
    """命令行入口，读取 SARIF 并输出路径 JSON。"""

    parser = argparse.ArgumentParser(description="Convert CodeQL SARIF to required path JSON.")
    parser.add_argument("sarif", help="Input SARIF file (from `codeql query run --format=sarifv2.1.0`).")
    parser.add_argument("-o", "--out", default="result.json", help="Output JSON file. Default: result.json")
    parser.add_argument("--max-results", type=int, default=3, help="Max number of paths (alerts) to export. Default: 3")
    parser.add_argument("--threadflow-index", type=int, default=0, help="Which threadFlow to pick per result. Default: 0")
    parser.add_argument("--rule-filter", help="Only include results whose ruleId contains this substring.")
    parser.add_argument("--relative-to", help="Make file paths relative to this directory.")
    args = parser.parse_args()

    try:
        from pathlib import Path
        import json

        sarif_path = Path(args.sarif)
        with sarif_path.open("r", encoding="utf-8") as handler:
            sarif = json.load(handler)
    except Exception as exc:
        print(f"Failed to read SARIF: {exc}", file=sys.stderr)
        sys.exit(1)

    data = sarif_to_paths(
        sarif,
        max_results=max(1, args.max_results),
        threadflow_index=max(0, args.threadflow_index),
        rule_filter=args.rule_filter,
        make_relative_to=args.relative_to,
    )

    try:
        from pathlib import Path
        import json

        json_path = Path(args.out)
        json_path.parent.mkdir(parents=True, exist_ok=True)
        with json_path.open("w", encoding="utf-8") as handler:
            json.dump(data, handler, ensure_ascii=False, indent=2)
    except Exception as exc:
        print(f"Failed to write output JSON: {exc}", file=sys.stderr)
        sys.exit(1)

    print(f"Wrote {args.out} with {len(data['dataFlowPath'])} path(s).")

if __name__ == "__main__":
    main()
