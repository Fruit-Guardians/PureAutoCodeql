"""用于获取和缓存漏洞情报的工具函数。"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from pure_auto_codeql.information import ghsa_fetch, nvd_info_fetch
from .case import CasePaths, CveAssets


@dataclass
class IntelBundle:
    """CVE的缓存情报包。"""

    cve_id: str
    data: Dict[str, Any]
    bundle_path: Path
    ghsa_summary_path: Optional[Path]
    nvd_summary_path: Optional[Path]
    used_cache: bool

    def prompt_block(self) -> str:
        """返回供LLM使用的紧凑提示块。"""

        sources = self.data.get("sources", {})
        lines: List[str] = []
        lines.append(f"CVE Intelligence Snapshot ({self.cve_id})")
        lines.append(f"Generated: {self.data.get('generated_at', 'unknown')}")

        ghsa = sources.get("ghsa", {})
        if ghsa.get("status") == "success":
            lines.append("")
            lines.append("GHSA Highlights:")
            for highlight in ghsa.get("highlights", []):
                lines.append(f"- {highlight}")
            if ghsa.get("warnings"):
                lines.append("Warnings:")
                for warning in ghsa["warnings"]:
                    lines.append(f"  - {warning}")
        elif ghsa.get("status") == "error":
            lines.append("")
            lines.append(f"GHSA fetch failed: {ghsa.get('error')}")

        nvd = sources.get("nvd", {})
        if nvd.get("status") == "success":
            lines.append("")
            lines.append("NVD Summary:")
            summary = nvd.get("summary_text")
            if summary:
                lines.append(summary)
            metrics = nvd.get("metrics")
            if metrics:
                lines.append(f"Metrics: {metrics}")
        elif nvd.get("status") == "error":
            lines.append("")
            lines.append(f"NVD fetch failed: {nvd.get('error')}")

        failed = self.data.get("sources_failed") or []
        if failed:
            lines.append("")
            lines.append("Sources failed: " + ", ".join(failed))

        return "\n".join(lines)


def collect_intel(
    case_paths: CasePaths,
    assets: CveAssets,
    *,
    github_token: Optional[str] = None,
    nvd_api_key: Optional[str] = None,
    use_cache: bool = True,
) -> IntelBundle:
    """为给定案例获取或复用GHSA/NVD情报。"""

    github_token = github_token or os.environ.get("GITHUB_TOKEN")
    nvd_api_key = nvd_api_key or os.environ.get("NVD_API_KEY")

    intel_dir = case_paths.intel
    intel_dir.mkdir(parents=True, exist_ok=True)

    bundle_path = intel_dir / f"{assets.cve_id}_bundle.json"
    ghsa_raw_path = intel_dir / f"{assets.cve_id}_ghsa_raw.json"
    ghsa_summary_path = intel_dir / f"{assets.cve_id}_ghsa_summary.md"
    nvd_raw_path = intel_dir / f"{assets.cve_id}_nvd_raw.json"
    nvd_summary_path = intel_dir / f"{assets.cve_id}_nvd_summary.md"

    if use_cache and bundle_path.exists():
        with bundle_path.open("r", encoding="utf-8") as fp:
            data = json.load(fp)
        return IntelBundle(
            cve_id=assets.cve_id,
            data=data,
            bundle_path=bundle_path,
            ghsa_summary_path=ghsa_summary_path if ghsa_summary_path.exists() else None,
            nvd_summary_path=nvd_summary_path if nvd_summary_path.exists() else None,
            used_cache=True,
        )

    bundle: Dict[str, Any] = {
        "cve_id": assets.cve_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "sources": {},
        "sources_failed": [],
    }

    ghsa_result = _fetch_ghsa(assets.cve_id, github_token, ghsa_raw_path, ghsa_summary_path)
    bundle["sources"]["ghsa"] = ghsa_result
    if ghsa_result["status"] == "error":
        bundle["sources_failed"].append("ghsa")

    nvd_result = _fetch_nvd(assets.cve_id, nvd_api_key, nvd_raw_path, nvd_summary_path)
    bundle["sources"]["nvd"] = nvd_result
    if nvd_result["status"] == "error":
        bundle["sources_failed"].append("nvd")

    with bundle_path.open("w", encoding="utf-8") as fp:
        json.dump(bundle, fp, indent=2, ensure_ascii=False)

    return IntelBundle(
        cve_id=assets.cve_id,
        data=bundle,
        bundle_path=bundle_path,
        ghsa_summary_path=ghsa_summary_path if ghsa_summary_path.exists() else None,
        nvd_summary_path=nvd_summary_path if nvd_summary_path.exists() else None,
        used_cache=False,
    )


def _fetch_ghsa(
    cve_id: str,
    github_token: Optional[str],
    raw_path: Path,
    summary_path: Path,
) -> Dict[str, Any]:
    """获取GitHub安全公告信息。"""
    result: Dict[str, Any] = {"status": "error"}
    try:
        repo_cache: Dict[str, Any] = {}
        payloads = ghsa_fetch.fetch_advisories_by_cve(cve_id, github_token)
        processed = []
        warnings: List[str] = []
        for payload in payloads:
            info = ghsa_fetch.extract_advisory_info(payload)
            info, warning = ghsa_fetch._enrich_with_repository_details(info, github_token, repo_cache)
            if warning:
                warnings.append(warning)
            processed.append(info)

        highlights = []
        summaries = []
        for info in processed:
            summaries.append(ghsa_fetch.format_advisory_info(info, query=cve_id))
            key_summary = info.get("summary") or ""
            if key_summary:
                highlights.append(key_summary.strip())

        with raw_path.open("w", encoding="utf-8") as fp:
            json.dump({"items": processed, "warnings": warnings}, fp, indent=2, ensure_ascii=False)
        summary_text = "\n\n---\n\n".join(summaries)
        summary_path.write_text(summary_text, encoding="utf-8")

        result.update(
            {
                "status": "success",
                "count": len(processed),
                "raw_path": str(raw_path),
                "summary_path": str(summary_path),
                "warnings": warnings,
                "highlights": highlights[:5],
            }
        )
    except ghsa_fetch.AdvisoryLookupError as exc:
        result.update({"error": str(exc)})
    except Exception as exc:  # pylint: disable=broad-except
        result.update({"error": f"GHSA fetch error: {exc}"})
    return result


def _fetch_nvd(
    cve_id: str,
    api_key: Optional[str],
    raw_path: Path,
    summary_path: Path,
) -> Dict[str, Any]:
    """获取NVD漏洞信息。"""
    result: Dict[str, Any] = {"status": "error"}
    try:
        payload = nvd_info_fetch.fetch_cve_payload(cve_id, api_key)
        info = nvd_info_fetch.extract_vulnerability_info(payload)
        summary_text = nvd_info_fetch.format_vulnerability_info(info)

        with raw_path.open("w", encoding="utf-8") as fp:
            json.dump(payload, fp, indent=2, ensure_ascii=False)
        summary_path.write_text(summary_text, encoding="utf-8")

        metrics = info.get("metrics")
        result.update(
            {
                "status": "success",
                "raw_path": str(raw_path),
                "summary_path": str(summary_path),
                "metrics": metrics,
                "summary_text": summary_text,
            }
        )
    except nvd_info_fetch.CveLookupError as exc:
        result.update({"error": str(exc)})
    except Exception as exc:  # pylint: disable=broad-except
        result.update({"error": f"NVD fetch error: {exc}"})
    return result
