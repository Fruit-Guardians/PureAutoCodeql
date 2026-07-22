#!/usr/bin/env python3
"""Fetch and print processed information for a CVE from the NVD API."""

from __future__ import annotations

import argparse
import json
import os
import ssl
import sys
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, Iterable, List, Optional, Set

API_URL = "https://services.nvd.nist.gov/rest/json/cves/2.0"
USER_AGENT = "nvd-cve-info-fetcher/1.0 (+https://nvd.nist.gov)"


class CveLookupError(RuntimeError):
    """Raised when fetching a CVE fails."""


def _create_ssl_context() -> ssl.SSLContext:
    """Create SSL context with compatibility workarounds."""
    ctx = ssl.create_default_context()
    try:
        ctx.options |= ssl.OP_NO_SSLv2
        ctx.options |= ssl.OP_NO_SSLv3
        ctx.set_ciphers('DEFAULT@SECLEVEL=1')
    except AttributeError:
        pass
    return ctx


def fetch_cve_payload(cve_id: str, api_key: Optional[str]) -> Dict[str, Any]:
    """Fetch raw JSON payload for the provided CVE ID."""
    params = urllib.parse.urlencode({"cveId": cve_id})
    url = f"{API_URL}?{params}"
    headers = {"User-Agent": USER_AGENT}
    if api_key:
        headers["apiKey"] = api_key

    req = urllib.request.Request(url, headers=headers)

    # Create SSL context with workarounds for Python 3.13 compatibility issues
    ssl_context = _create_ssl_context()

    last_error: Optional[Exception] = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30, context=ssl_context) as response:
                charset = response.headers.get_content_charset("utf-8")
                payload = response.read().decode(charset)
                return json.loads(payload)
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                raise CveLookupError(f"CVE {cve_id} not found in NVD data.") from exc
            if exc.code == 429 and attempt < 2:
                retry_after = _parse_retry_after(exc.headers.get("Retry-After"))
                time.sleep(retry_after)
                continue
            last_error = exc
            break
        except urllib.error.URLError as exc:
            last_error = exc
            time.sleep(1)
        except json.JSONDecodeError as exc:
            raise CveLookupError("Received invalid JSON from NVD.") from exc

    if last_error:
        raise CveLookupError(f"Failed to fetch {cve_id}: {last_error}") from last_error

    raise CveLookupError(f"Failed to fetch {cve_id}: unknown error.")


def _parse_retry_after(value: Optional[str]) -> int:
    """Parse Retry-After header and return seconds to sleep."""
    if not value:
        return 5
    try:
        return max(1, int(value))
    except ValueError:
        return 5


def extract_vulnerability_info(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Transform the raw API payload into a simplified structure."""
    vulnerabilities = payload.get("vulnerabilities")
    if not vulnerabilities:
        raise CveLookupError("NVD response did not contain vulnerability data.")

    entry = vulnerabilities[0].get("cve")
    if not entry:
        raise CveLookupError("Malformed NVD response: missing CVE object.")

    description = _first_english_description(entry.get("descriptions", []))
    metrics = _extract_primary_metrics(entry.get("metrics", {}))
    weaknesses = _extract_weaknesses(entry.get("weaknesses", []))
    references = _extract_references(entry.get("references", []))
    products = sorted(_extract_products(entry.get("configurations", [])))

    return {
        "id": entry.get("id"),
        "source": entry.get("sourceIdentifier"),
        "published": entry.get("published"),
        "last_modified": entry.get("lastModified"),
        "status": entry.get("vulnStatus"),
        "description": description,
        "metrics": metrics,
        "weaknesses": weaknesses,
        "references": references,
        "products": products,
    }


def _first_english_description(descriptions: Iterable[Dict[str, Any]]) -> Optional[str]:
    for item in descriptions:
        if item.get("lang") == "en":
            return item.get("value")
    return None


def _extract_primary_metrics(metrics: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    for key in ("cvssMetricV31", "cvssMetricV30", "cvssMetricV2"):
        entries = metrics.get(key)
        if entries:
            metric = entries[0]
            cvss_data = metric.get("cvssData", {})
            severity = (
                metric.get("baseSeverity")
                or metric.get("severity")
                or cvss_data.get("baseSeverity")
            )
            return {
                "version": cvss_data.get("version"),
                "base_score": cvss_data.get("baseScore"),
                "vector": cvss_data.get("vectorString"),
                "severity": severity,
            }
    return None


def _extract_weaknesses(weaknesses: Iterable[Dict[str, Any]]) -> List[str]:
    result: List[str] = []
    seen: Set[str] = set()
    for weakness in weaknesses:
        descriptions = weakness.get("description", [])
        for item in descriptions:
            value = item.get("value")
            if item.get("lang") == "en" and value and value not in seen:
                result.append(value)
                seen.add(value)
                break
    return result


def _extract_references(references: Iterable[Dict[str, Any]]) -> List[Dict[str, Any]]:
    combined: Dict[str, Dict[str, Any]] = {}
    for reference in references:
        url = reference.get("url")
        if not url:
            continue
        entry = combined.setdefault(
            url,
            {
                "url": url,
                "sources": set(),
                "tags": set(),
            },
        )
        source = reference.get("source")
        if source:
            entry["sources"].add(source)
        entry["tags"].update(reference.get("tags") or [])

    result: List[Dict[str, Any]] = []
    for ref in combined.values():
        filtered_sources = sorted(
            source for source in ref["sources"] if not _looks_like_uuid(source)
        )
        result.append(
            {
                "url": ref["url"],
                "source": ", ".join(filtered_sources) if filtered_sources else None,
                "tags": sorted(ref["tags"]),
            }
        )
    return sorted(result, key=lambda item: item["url"])


def _looks_like_uuid(value: str) -> bool:
    """Return True if the value looks like an auto-generated UUID."""
    parts = value.split("-")
    if len(parts) != 5:
        return False
    hex_lengths = [8, 4, 4, 4, 12]
    for part, length in zip(parts, hex_lengths):
        if len(part) != length:
            return False
        if not all(c in "0123456789abcdefABCDEF" for c in part):
            return False
    return True


def _extract_products(config_nodes: Iterable[Dict[str, Any]]) -> Set[str]:
    products: Set[str] = set()

    def walk(nodes: Iterable[Dict[str, Any]]) -> None:
        for node in nodes:
            for match in node.get("cpeMatch", []):
                if match.get("vulnerable") and match.get("criteria"):
                    products.add(match["criteria"])
            children = node.get("children", [])
            if children:
                walk(children)

    walk(config_nodes)
    return products


def format_vulnerability_info(info: Dict[str, Any]) -> str:
    """Render the simplified structure into human-readable text."""
    lines: List[str] = []
    lines.append(str(info.get("id")))

    published = info.get("published")
    last_modified = info.get("last_modified")
    if published or last_modified:
        timestamp_parts: List[str] = []
        if published:
            timestamp_parts.append(f"Published: {published}")
        if last_modified and last_modified != published:
            timestamp_parts.append(f"Updated: {last_modified}")
        lines.append(" | ".join(timestamp_parts))

    metrics = info.get("metrics")
    if metrics:
        line = "CVSS {version}: {base_score} ({severity})".format(
            version=metrics.get("version"),
            base_score=metrics.get("base_score"),
            severity=metrics.get("severity"),
        )
        vector = metrics.get("vector")
        if vector:
            line = f"{line} | Vector: {vector}"
        lines.append(line)

    description = info.get("description")
    if description:
        lines.append("")
        lines.append("Description:")
        lines.extend(textwrap.wrap(description, width=100))

    weaknesses = info.get("weaknesses") or []
    if weaknesses:
        lines.append("")
        lines.append("CWE:")
        for item in weaknesses:
            lines.append(f"- {item}")

    products = info.get("products") or []
    if products:
        lines.append("")
        lines.append("Affected CPEs:")
        for product in products:
            lines.append(f"- {product}")

    references = info.get("references") or []
    if references:
        lines.append("")
        lines.append("References:")
        for reference in references:
            tag_str = f" ({', '.join(reference['tags'])})" if reference["tags"] else ""
            source_str = f"[{reference['source']}] " if reference["source"] else ""
            lines.append(f"- {source_str}{reference['url']}{tag_str}")

    return "\n".join(lines)


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch processed NVD information for a (list of) CVE ID(s)."
    )
    parser.add_argument("cve_ids", nargs="+", help="CVE identifiers (e.g. CVE-2024-1234)")
    parser.add_argument(
        "--api-key",
        default=os.environ.get("NVD_API_KEY"),
        help="NVD API key (falls back to NVD_API_KEY environment variable).",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    exit_code = 0

    for cve_id in args.cve_ids:
        try:
            payload = fetch_cve_payload(cve_id, args.api_key)
            info = extract_vulnerability_info(payload)
            output = format_vulnerability_info(info)
            print(output)
        except CveLookupError as exc:
            print(f"Error: {exc}", file=sys.stderr)
            exit_code = 1
        except Exception as exc:
            print(f"Unexpected error while processing {cve_id}: {exc}", file=sys.stderr)
            exit_code = 1

        if cve_id != args.cve_ids[-1]:
            print("\n" + "=" * 80 + "\n")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
