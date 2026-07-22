#!/usr/bin/env python3
"""Fetch and print processed information for a GitHub security advisory."""

from __future__ import annotations

import argparse
import copy
import json
import os
import sys
import textwrap
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

API_URL = "https://api.github.com/advisories"
USER_AGENT = "ghsa-info-fetcher/1.0 (+https://github.com/advisories)"


class AdvisoryLookupError(RuntimeError):
    """Raised when fetching a GitHub advisory fails."""


def _build_headers(token: Optional[str]) -> Dict[str, str]:
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _request_json(
    url: str,
    token: Optional[str],
    *,
    not_found_message: Optional[str] = None,
) -> Any:
    headers = _build_headers(token)
    req = urllib.request.Request(url, headers=headers)

    last_error: Optional[Exception] = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                charset = response.headers.get_content_charset("utf-8")
                payload = response.read().decode(charset)
                return json.loads(payload)
        except urllib.error.HTTPError as exc:
            if exc.code == 404 and not_found_message:
                raise AdvisoryLookupError(not_found_message) from exc
            if exc.code == 401:
                raise AdvisoryLookupError(
                    "Authentication failed. Provide a valid GitHub token via --token or GITHUB_TOKEN."
                ) from exc
            if exc.code == 403 and attempt < 2:
                retry_after = _parse_retry_after(exc.headers.get("Retry-After"))
                if retry_after:
                    time.sleep(retry_after)
                    continue
                reset_header = exc.headers.get("X-RateLimit-Reset")
                if reset_header:
                    try:
                        reset_ts = int(reset_header)
                        wait_for = max(1, reset_ts - int(time.time()))
                    except ValueError:
                        wait_for = 5
                    wait_for = max(1, min(wait_for, 60))
                    time.sleep(wait_for)
                    continue
            last_error = exc
            break
        except urllib.error.URLError as exc:
            last_error = exc
            time.sleep(1)
        except json.JSONDecodeError as exc:
            raise AdvisoryLookupError("Received invalid JSON from GitHub.") from exc

    if last_error:
        raise AdvisoryLookupError(f"Failed to fetch data from GitHub: {last_error}") from last_error

    raise AdvisoryLookupError("Failed to fetch data from GitHub due to an unknown error.")


def fetch_advisory_payload(ghsa_id: str, token: Optional[str]) -> Dict[str, Any]:
    """Fetch raw JSON payload for the provided GHSA identifier."""
    quoted_id = urllib.parse.quote(ghsa_id, safe="")
    url = f"{API_URL}/{quoted_id}"
    not_found = f"Advisory {ghsa_id} not found in GitHub Security Advisories."
    return _request_json(url, token, not_found_message=not_found)


def fetch_advisories_by_cve(cve_id: str, token: Optional[str]) -> List[Dict[str, Any]]:
    """Fetch advisories matching the provided CVE identifier."""
    params = urllib.parse.urlencode({"cve_id": cve_id})
    url = f"{API_URL}?{params}"
    payload = _request_json(url, token)
    if not isinstance(payload, list):
        raise AdvisoryLookupError("Unexpected response while searching advisories by CVE.")
    advisories = [item for item in payload if isinstance(item, dict)]
    if not advisories:
        raise AdvisoryLookupError(f"CVE {cve_id} is not linked to any GitHub advisory.")
    return advisories


def fetch_repository_advisory(url: str, token: Optional[str]) -> Dict[str, Any]:
    """Fetch repository-level advisory details."""
    payload = _request_json(
        url,
        token,
        not_found_message="Repository advisory not found or inaccessible.",
    )
    if not isinstance(payload, dict):
        raise AdvisoryLookupError("Unexpected response while fetching repository advisory data.")
    return payload


def extract_repository_advisory_info(payload: Dict[str, Any]) -> Dict[str, Any]:
    repo_links: List[Tuple[str, str]] = []
    html_url = payload.get("html_url")
    if html_url:
        repo_links.append(("Repository Advisory", html_url))
    api_url = payload.get("url")
    if api_url:
        repo_links.append(("Repository Advisory API", api_url))

    repo_slug = _parse_repo_slug(api_url, html_url)

    return {
        "repo_slug": repo_slug,
        "state": payload.get("state"),
        "severity": payload.get("severity"),
        "published": payload.get("published_at"),
        "updated": payload.get("updated_at"),
        "withdrawn": payload.get("withdrawn_at"),
        "cvss": _extract_cvss_metrics(
            payload.get("cvss"),
            payload.get("cvss_severities"),
        ),
        "cwes": _extract_cwes(payload.get("cwes") or []),
        "vulnerabilities": _extract_vulnerabilities(payload.get("vulnerabilities") or []),
        "links": repo_links,
    }


def _parse_repo_slug(*urls: Optional[str]) -> Optional[str]:
    for url in urls:
        if not url:
            continue
        parsed = urllib.parse.urlparse(url)
        segments = [segment for segment in parsed.path.split("/") if segment]
        if not segments:
            continue
        if segments[0] == "repos" and len(segments) >= 3:
            return f"{segments[1]}/{segments[2]}"
        if parsed.netloc.endswith("github.com") and len(segments) >= 2:
            return f"{segments[0]}/{segments[1]}"
    return None


def _parse_retry_after(value: Optional[str]) -> int:
    if not value:
        return 0
    try:
        return max(1, int(value))
    except ValueError:
        return 0


def extract_advisory_info(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Transform the GitHub advisory payload into a simplified structure."""
    aliases = _extract_aliases(payload)
    links: List[Tuple[str, str]] = []
    html_url = payload.get("html_url")
    if html_url:
        links.append(("GitHub Advisory", html_url))
    source_code = payload.get("source_code_location")
    if source_code:
        links.append(("Source", source_code))

    return {
        "id": payload.get("ghsa_id"),
        "query_aliases": aliases,
        "summary": payload.get("summary"),
        "description": payload.get("description"),
        "severity": payload.get("severity"),
        "type": payload.get("type"),
        "published": payload.get("published_at"),
        "updated": payload.get("updated_at"),
        "withdrawn": payload.get("withdrawn_at"),
        "cvss": _extract_cvss_metrics(
            payload.get("cvss"),
            payload.get("cvss_severities"),
        ),
        "cwes": _extract_cwes(payload.get("cwes") or []),
        "references": _extract_references(payload.get("references") or []),
        "vulnerabilities": _extract_vulnerabilities(payload.get("vulnerabilities") or []),
        "links": links,
        "html_url": html_url,
        "repository_api_url": payload.get("repository_advisory_url"),
    }


def _extract_aliases(payload: Dict[str, Any]) -> List[str]:
    aliases: List[str] = []

    identifiers = payload.get("identifiers") or []
    if isinstance(identifiers, list):
        for item in identifiers:
            if not isinstance(item, dict):
                continue
            value = item.get("value")
            if not value:
                continue
            if value != payload.get("ghsa_id"):
                aliases.append(str(value))

    cve_id = payload.get("cve_id")
    if cve_id:
        aliases.append(str(cve_id))

    seen: set[str] = set()
    result: List[str] = []
    for alias in aliases:
        upper = alias.upper()
        if upper in seen:
            continue
        seen.add(upper)
        result.append(alias)
    return result


def _extract_cvss_metrics(
    cvss: Optional[Dict[str, Any]],
    cvss_severities: Optional[Dict[str, Any]],
) -> List[Dict[str, Optional[str]]]:
    metrics: List[Dict[str, Optional[str]]] = []

    def add_entry(entry: Optional[Dict[str, Any]], label: Optional[str] = None) -> None:
        if not entry or not isinstance(entry, dict):
            return
        vector = entry.get("vector_string")
        score = entry.get("score")
        if score is None and not vector:
            return
        version = label.replace("_", " ") if label else None
        if vector and vector.startswith("CVSS:"):
            version = vector.split("/")[0]
        metrics.append(
            {
                "version": version,
                "score": score,
                "vector": vector,
            }
        )

    add_entry(cvss)
    if isinstance(cvss_severities, dict):
        for key, value in cvss_severities.items():
            add_entry(value if isinstance(value, dict) else None, label=key.upper())

    # Remove duplicates while preserving order
    seen: set[tuple] = set()
    unique_metrics: List[Dict[str, Optional[str]]] = []
    for metric in metrics:
        key = (
            metric.get("version"),
            metric.get("score"),
            metric.get("vector"),
        )
        if key in seen:
            continue
        seen.add(key)
        unique_metrics.append(metric)

    return unique_metrics


def _extract_cwes(cwes: Iterable[Dict[str, Any]]) -> List[str]:
    items: List[str] = []
    for entry in cwes:
        cwe_id = entry.get("cwe_id")
        name = entry.get("name")
        if cwe_id and name:
            items.append(f"{cwe_id} ({name})")
        elif cwe_id:
            items.append(cwe_id)
        elif name:
            items.append(name)
    return items


def _extract_references(refs: Sequence[Any]) -> List[str]:
    items: List[str] = []
    for ref in refs:
        if isinstance(ref, str) and ref:
            items.append(ref)
    return items


def _extract_vulnerabilities(entries: Iterable[Dict[str, Any]]) -> List[Dict[str, Any]]:
    items: List[Dict[str, Any]] = []
    for entry in entries:
        package = entry.get("package") or {}
        first_patched_raw = entry.get("first_patched_version")
        if isinstance(first_patched_raw, dict):
            first_patched = first_patched_raw.get("identifier")
        else:
            first_patched = first_patched_raw
        patched_versions = entry.get("patched_versions")
        if not first_patched and isinstance(patched_versions, str):
            first_patched = patched_versions

        items.append(
            {
                "ecosystem": package.get("ecosystem"),
                "name": package.get("name"),
                "range": entry.get("vulnerable_version_range"),
                "patched": first_patched,
                "functions": entry.get("vulnerable_functions") or [],
            }
        )
    return items


def _merge_links(
    existing: Optional[Sequence[Tuple[str, str]]],
    additional: Optional[Sequence[Tuple[str, str]]],
) -> List[Tuple[str, str]]:
    merged: List[Tuple[str, str]] = []
    seen: set[Tuple[str, str]] = set()

    for collection in (existing or [], additional or []):
        for label, url in collection:
            if not url:
                continue
            clean_label = label or "Link"
            key = (clean_label, url)
            if key in seen:
                continue
            seen.add(key)
            merged.append((clean_label, url))
    return merged


def _merge_unique(first: Optional[Sequence[str]], second: Optional[Sequence[str]]) -> List[str]:
    result: List[str] = []
    seen: set[str] = set()
    for collection in (first or [], second or []):
        for item in collection:
            if not item:
                continue
            if item in seen:
                continue
            seen.add(item)
            result.append(item)
    return result


def _vulnerability_key(vuln: Dict[str, Any]) -> Tuple[str, str, str, str]:
    return (
        (vuln.get("ecosystem") or "").upper(),
        vuln.get("name") or "",
        _normalize_version_text(vuln.get("range")),
        _normalize_version_text(vuln.get("patched")),
    )


def _normalize_version_text(value: Optional[str]) -> str:
    if value is None:
        return ""
    return "".join(str(value).split())


def _merge_vulnerabilities(
    primary: Optional[Sequence[Dict[str, Any]]],
    secondary: Optional[Sequence[Dict[str, Any]]],
) -> List[Dict[str, Any]]:
    primary_list = list(primary or [])
    seen = {_vulnerability_key(v) for v in primary_list}
    for vuln in secondary or []:
        key = _vulnerability_key(vuln)
        if key in seen:
            continue
        seen.add(key)
        primary_list.append(vuln)
    return primary_list


def _format_cvss_metrics(metrics: Sequence[Dict[str, Optional[str]]]) -> str:
    fragments: List[str] = []
    for metric in metrics:
        score = metric.get("score")
        vector = metric.get("vector")
        version = metric.get("version") or "CVSS"
        version = version.replace("_", " ")
        if score is not None:
            fragment = f"{version}: {score}"
        else:
            fragment = version
        if vector:
            fragment = f"{fragment} ({vector})"
        fragments.append(fragment)
    return "; ".join(fragments)


def _enrich_with_repository_details(
    info: Dict[str, Any],
    token: Optional[str],
    cache: Optional[Dict[str, Tuple[Optional[Dict[str, Any]], Optional[str]]]] = None,
) -> Tuple[Dict[str, Any], Optional[str]]:
    repo_url = info.get("repository_api_url")
    if not repo_url:
        return info, None

    repo_info: Optional[Dict[str, Any]] = None
    error: Optional[str] = None

    if cache is not None and repo_url in cache:
        cached_repo, cached_error = cache[repo_url]
        error = cached_error
        repo_info = copy.deepcopy(cached_repo) if cached_repo is not None else None
    else:
        try:
            repo_payload = fetch_repository_advisory(repo_url, token)
        except AdvisoryLookupError as exc:
            error = str(exc)
            if cache is not None:
                cache[repo_url] = (None, error)
        else:
            repo_info = extract_repository_advisory_info(repo_payload)
            if cache is not None:
                cache[repo_url] = (repo_info, None)
            repo_info = copy.deepcopy(repo_info)

    if error:
        return info, error
    if not repo_info:
        return info, None

    info["repository"] = repo_info
    info["links"] = _merge_links(info.get("links"), repo_info.get("links"))
    info["cwes"] = _merge_unique(info.get("cwes"), repo_info.get("cwes"))
    info["vulnerabilities"] = _merge_vulnerabilities(
        info.get("vulnerabilities"),
        repo_info.get("vulnerabilities"),
    )
    return info, None


def format_advisory_info(info: Dict[str, Any], *, query: Optional[str] = None) -> str:
    """Render advisory information into human-readable text."""
    lines: List[str] = []
    title = str(info.get("id") or "Unknown GHSA")
    if query and query.upper() != title.upper():
        lines.append(f"{title} (matched {query})")
    else:
        lines.append(title)

    timeline: List[str] = []
    if info.get("published"):
        timeline.append(f"Published: {info['published']}")
    if info.get("updated") and info["updated"] != info.get("published"):
        timeline.append(f"Updated: {info['updated']}")
    if info.get("withdrawn"):
        timeline.append(f"Withdrawn: {info['withdrawn']}")
    if timeline:
        lines.append(" | ".join(timeline))

    if info.get("summary"):
        lines.append(f"Summary: {info['summary']}")

    severity_line = info.get("severity")
    cvss_metrics = info.get("cvss") or []
    if info.get("type"):
        lines.append(f"Advisory type: {info['type']}")

    formatted_cvss = _format_cvss_metrics(cvss_metrics)
    if severity_line or formatted_cvss:
        parts: List[str] = []
        if severity_line:
            parts.append(f"Severity: {severity_line}")
        if formatted_cvss:
            parts.append(formatted_cvss)
        lines.append(" | ".join(parts))

    aliases = [alias for alias in info.get("query_aliases", []) if alias]
    if aliases:
        lines.append("Aliases: " + ", ".join(sorted(set(aliases))))

    links = info.get("links") or []
    if links:
        lines.append("")
        lines.append("Links:")
        for label, url in links:
            lines.append(f"- {label}: {url}")

    description = info.get("description")
    if description:
        lines.append("")
        lines.append("Description:")
        lines.extend(textwrap.wrap(description, width=100))

    cwes = info.get("cwes") or []
    if cwes:
        lines.append("")
        lines.append("CWE:")
        for cwe in cwes:
            lines.append(f"- {cwe}")

    vulnerabilities = info.get("vulnerabilities") or []
    if vulnerabilities:
        lines.append("")
        lines.append("Affected Packages:")
        for vuln in vulnerabilities:
            package_parts: List[str] = []
            ecosystem = vuln.get("ecosystem")
            name = vuln.get("name")
            if ecosystem and name:
                package_parts.append(f"{ecosystem}/{name}")
            elif name:
                package_parts.append(name)
            elif ecosystem:
                package_parts.append(ecosystem)
            if vuln.get("range"):
                package_parts.append(f"Range: {vuln['range']}")
            if vuln.get("patched"):
                package_parts.append(f"Patched: {vuln['patched']}")
            if vuln.get("functions"):
                package_parts.append("Functions: " + ", ".join(vuln["functions"]))
            lines.append("- " + " | ".join(package_parts))

    repo_info = info.get("repository") or {}
    if repo_info:
        lines.append("")
        header = "Repository Advisory"
        if repo_info.get("repo_slug"):
            header += f" ({repo_info['repo_slug']})"
        lines.append(header + ":")

        meta_parts: List[str] = []
        if repo_info.get("state"):
            meta_parts.append(f"State: {repo_info['state']}")
        if repo_info.get("severity"):
            meta_parts.append(f"Severity: {repo_info['severity']}")

        repo_timeline: List[str] = []
        if repo_info.get("published"):
            repo_timeline.append(f"Published: {repo_info['published']}")
        if repo_info.get("updated") and repo_info["updated"] != repo_info.get("published"):
            repo_timeline.append(f"Updated: {repo_info['updated']}")
        if repo_info.get("withdrawn"):
            repo_timeline.append(f"Withdrawn: {repo_info['withdrawn']}")
        if repo_timeline:
            meta_parts.append(" | ".join(repo_timeline))

        if meta_parts:
            lines.append(" ; ".join(meta_parts))

        repo_cvss = _format_cvss_metrics(repo_info.get("cvss") or [])
        if repo_cvss:
            lines.append(f"CVSS: {repo_cvss}")

    references = info.get("references") or []
    if references:
        lines.append("")
        lines.append("References:")
        for reference in references:
            lines.append(f"- {reference}")

    return "\n".join(lines)


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch processed GitHub advisory information for GHSA or CVE identifiers."
    )
    parser.add_argument(
        "identifiers",
        nargs="+",
        help="GitHub advisory GHSA ids or CVE ids (e.g. GHSA-xxxx-xxxx-xxxx or CVE-2024-1234)",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("GITHUB_TOKEN"),
        help="GitHub token (falls back to GITHUB_TOKEN environment variable).",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    exit_code = 0
    repo_cache: Dict[str, Tuple[Optional[Dict[str, Any]], Optional[str]]] = {}

    for idx, identifier in enumerate(args.identifiers):
        upper_identifier = identifier.upper()
        try:
            if upper_identifier.startswith("CVE-"):
                payloads = fetch_advisories_by_cve(upper_identifier, args.token)
                for payload_idx, payload in enumerate(payloads):
                    info = extract_advisory_info(payload)
                    info, warning = _enrich_with_repository_details(info, args.token, repo_cache)
                    if warning:
                        print(f"Warning: {warning}", file=sys.stderr)
                    print(format_advisory_info(info, query=upper_identifier))
                    if payload_idx != len(payloads) - 1:
                        print("\n" + "-" * 80 + "\n")
            else:
                payload = fetch_advisory_payload(identifier, args.token)
                info = extract_advisory_info(payload)
                info, warning = _enrich_with_repository_details(info, args.token, repo_cache)
                if warning:
                    print(f"Warning: {warning}", file=sys.stderr)
                print(format_advisory_info(info))
        except AdvisoryLookupError as exc:
            print(f"Error: {exc}", file=sys.stderr)
            exit_code = 1
        except Exception as exc:
            print(f"Unexpected error while processing {identifier}: {exc}", file=sys.stderr)
            exit_code = 1

        if idx != len(args.identifiers) - 1:
            print("\n" + "=" * 80 + "\n")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
