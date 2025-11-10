"""路径验证器

对最终选择的路径做完整性、语义、置信度校验。
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Tuple

logger = logging.getLogger(__name__)

USER_INPUT_KEYWORDS = ("request", "input", "param", "arg", "user", "body", "query")


class PathVerifier:
    """路径验证器."""

    def __init__(self, language: str):
        self.language = (language or "").lower()

    def verify_paths(
        self,
        selected_paths: List[Dict[str, Any]],
        cve_context: Dict[str, Any],
        all_paths: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        logger.info("��֤ѡ��·��...")

        verified_paths: List[Dict[str, Any]] = []
        blocking_issues: List[str] = []

        for path in selected_paths:
            verification = self._verify_single_path(path, cve_context)
            verified_paths.append({**path, "verification": verification})
            if not verification["is_valid"]:
                blocking_issues.extend(verification["issues"])

        summary = {
            "all_valid": len(blocking_issues) == 0,
            "issues": blocking_issues,
            "total_verified": len(verified_paths),
            "valid_count": sum(1 for p in verified_paths if p["verification"]["is_valid"]),
            "invalid_count": sum(1 for p in verified_paths if not p["verification"]["is_valid"]),
            "warning_count": sum(
                len(p["verification"].get("warnings", [])) for p in verified_paths
            ),
        }

        logger.info(
            "  ��֤���: %s/%s ��·����Ч",
            summary["valid_count"],
            summary["total_verified"],
        )

        return {
            "paths": verified_paths,
            "summary": summary,
        }

    def _verify_single_path(
        self,
        path: Dict[str, Any],
        cve_context: Dict[str, Any],
    ) -> Dict[str, Any]:
        completeness = self._check_completeness(path)
        correctness = self._check_correctness(path, cve_context)
        semantics = self._check_semantics(path, cve_context)
        confidence = self._check_confidence(path)

        issues: List[str] = []
        warnings: List[str] = []
        for check in (completeness, correctness, semantics):
            issues.extend(check["issues"])
            warnings.extend(check.get("warnings", []))
        warnings.extend(confidence.get("warnings", []))

        return {
            "is_valid": len(issues) == 0,
            "issues": issues,
            "warnings": warnings,
            "completeness": completeness,
            "correctness": correctness,
            "semantics": semantics,
            "confidence": confidence,
        }

    def _check_completeness(self, path: Dict[str, Any]) -> Dict[str, Any]:
        issues: List[str] = []

        source_loc = path.get("source_location") or {}
        sink_loc = path.get("sink_location") or {}
        if not source_loc:
            issues.append("ȱ��Source����Ϣ")
        if not sink_loc:
            issues.append("ȱ��Sink����Ϣ")

        path_length = path.get("path_length", 0)
        if path_length < 2:
            issues.append(f"·������ (ֻ��{path_length}��)")

        for label, loc in (("Source", source_loc), ("Sink", sink_loc)):
            if loc and not loc.get("file"):
                issues.append(f"{label}��ȱ���ļ���Ϣ")
            if loc and not loc.get("startLine"):
                issues.append(f"{label}��ȱ���к���Ϣ")

        return {"valid": len(issues) == 0, "issues": issues}

    def _check_correctness(
        self,
        path: Dict[str, Any],
        cve_context: Dict[str, Any],
    ) -> Dict[str, Any]:
        issues: List[str] = []
        warnings: List[str] = []

        sink_loc = path.get("sink_location") or {}
        source_an = path.get("source_analysis") or {}
        sink_desc = (sink_loc.get("description") or "").lower()
        source_desc = (source_an.get("description") or "").lower()

        expected_sink = self._keyword_tokens(cve_context.get("expected_sink", ""))
        expected_source = self._keyword_tokens(cve_context.get("expected_source", ""))

        if expected_sink:
            if not sink_loc.get("file") or not any(
                token in sink_loc.get("file", "").lower() or token in sink_desc
                for token in expected_sink
            ):
                issues.append("Sink��δƥ��CVEԤ�������ļ�/����")

        if expected_source and not any(token in source_desc for token in expected_source):
            warnings.append("Source��δ��ʾԤ�������ص㣨����Ϊwarning��")

        return {"valid": len(issues) == 0, "issues": issues, "warnings": warnings}

    def _check_semantics(
        self,
        path: Dict[str, Any],
        cve_context: Dict[str, Any],
    ) -> Dict[str, Any]:
        issues: List[str] = []
        warnings: List[str] = []

        dangerous_apis = path.get("dangerous_apis") or []
        if not dangerous_apis:
            issues.append("δ��ʾ���κ�Σ��API���޷�����©����")

        matched_keywords = set(path.get("matched_keywords") or [])
        cve_keywords = set(self._keyword_tokens(cve_context.get("technical_details", "")))
        if cve_keywords and not matched_keywords:
            issues.append("·����δ����CVE �����ؼ���")

        source_an = path.get("source_analysis") or {}
        source_type = (source_an.get("type") or "").lower()
        if source_type in ("unknown", "", None):
            issues.append("Source����δ��ʾ�û������ͣ����ܲ�ɿ�")
        elif not any(keyword in (source_an.get("description", "") or "").lower() for keyword in USER_INPUT_KEYWORDS):
            warnings.append("Source�����������ٴ��û���ػ����")

        return {"valid": len(issues) == 0, "issues": issues, "warnings": warnings}

    def _check_confidence(self, path: Dict[str, Any]) -> Dict[str, Any]:
        issues: List[str] = []
        warnings: List[str] = []

        selection_info = path.get("selection_info") or {}
        deterministic = float(selection_info.get("deterministic_score") or 0.0)
        llm_score = float(selection_info.get("llm_alignment_score") or 0.0)
        blended = float(selection_info.get("confidence") or (0.7 * deterministic + 0.3 * llm_score))

        if blended < 0.5:
            issues.append(f"���Ŷȹ��� ({blended:.2f})")
        elif blended < 0.7:
            warnings.append(f"���ŶȽϵ� ({blended:.2f})")

        return {
            "valid": len(issues) == 0,
            "issues": issues,
            "warnings": warnings,
            "confidence_score": round(blended, 4),
        }

    def _keyword_tokens(self, text: str | None) -> List[str]:
        if not text:
            return []
        tokens = []
        for token in text.replace(",", " ").split():
            token = token.strip().lower()
            if len(token) >= 3:
                tokens.append(token)
        return tokens


__all__ = ["PathVerifier"]
