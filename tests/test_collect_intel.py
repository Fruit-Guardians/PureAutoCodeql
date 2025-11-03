import argparse
from pathlib import Path

from utils.case import discover_cve_assets, resolve_case
from utils.intel import collect_intel


def format_size(path: Path) -> str:
    try:
        size = path.stat().st_size
    except OSError:
        return "unknown size"
    units = ["B", "KB", "MB", "GB"]
    idx = 0
    value = float(size)
    while value >= 1024 and idx < len(units) - 1:
        value /= 1024
        idx += 1
    return f"{value:.1f}{units[idx]}"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Collect vulnerability intelligence for a case workspace."
    )
    parser.add_argument(
        "--case",
        required=True,
        help="Case identifier located under projects/<case-id>/",
    )
    parser.add_argument(
        "--cve",
        help="Specific CVE to use when multiple JSON files exist inside inputs/",
    )
    parser.add_argument(
        "--github-token",
        help="GitHub token for GHSA fetch (overrides env var if provided).",
    )
    parser.add_argument(
        "--nvd-api-key",
        help="NVD API key (overrides env var if provided).",
    )
    parser.add_argument(
        "--refresh-intel",
        action="store_true",
        help="Force refresh GHSA/NVD intelligence even if cache files already exist.",
    )
    args = parser.parse_args()

    case_paths = resolve_case(args.case)

    assets = discover_cve_assets(case_paths, preferred_cve=args.cve)
    print(f"[case]  {case_paths.root}")
    print(f"[cve ]  {assets.cve_id}")
    print(f"[json]  {assets.json_path} ({format_size(assets.json_path)})")
    if assets.diff_path:
        print(f"[diff]  {assets.diff_path} ({format_size(assets.diff_path)})")
    else:
        print("[diff]  <not provided>")

    bundle = collect_intel(
        case_paths,
        assets,
        github_token=args.github_token,
        nvd_api_key=args.nvd_api_key,
        use_cache=not args.refresh_intel,
    )

    status = "cache" if bundle.used_cache else "fetched"
    print(f"\n[intel] bundle ({status}): {bundle.bundle_path}")

    sources = bundle.data.get("sources", {})
    ghsa = sources.get("ghsa", {})
    print("\n[GHSA]")
    print(f"  status : {ghsa.get('status')}")
    if ghsa.get("status") == "success":
        print(f"  summary: {ghsa.get('summary_path')}")
        print(f"  raw    : {ghsa.get('raw_path')}")
        if ghsa.get("warnings"):
            for warning in ghsa["warnings"]:
                print(f"  warning: {warning}")
    else:
        print(f"  error  : {ghsa.get('error', 'unknown error')}")

    nvd = sources.get("nvd", {})
    print("\n[NVD]")
    print(f"  status : {nvd.get('status')}")
    if nvd.get("status") == "success":
        print(f"  summary: {nvd.get('summary_path')}")
        print(f"  raw    : {nvd.get('raw_path')}")
    else:
        print(f"  error  : {nvd.get('error', 'unknown error')}")

    if bundle.data.get("sources_failed"):
        failed = ", ".join(bundle.data["sources_failed"])
        print(f"\n[warn] sources failed: {failed}")


if __name__ == "__main__":
    main()
