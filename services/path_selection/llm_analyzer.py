"""LLM 语义精排 + 解释模块."""

from __future__ import annotations

import json
import logging
from typing import Any, Dict, List

from pydantic import BaseModel, Field, ValidationError

from .code_context_builder import CodeContextBuilder
from .path_ranker import PathScore
from .selection_formatter import score_to_payload

logger = logging.getLogger(__name__)


class SelectedPathModel(BaseModel):
    candidate_rank: int = Field(ge=0)
    llm_alignment_score: float = Field(ge=0.0, le=1.0)
    reason: str
    coverage_tags: List[str] = []
    notes: str | None = None


class AnalyzerResponseModel(BaseModel):
    selected_paths: List[SelectedPathModel]
    overall_reasoning: str
    coverage_analysis: Dict[str, Any] | None = None


class LLMPathAnalyzer:
    """LLM 路径分析器."""

    def __init__(self, llm_client, language: str):
        self.llm = llm_client
        self.language = (language or "").lower()
        self.context_builder = CodeContextBuilder()

    async def analyze_and_select(
        self,
        cve_context: Dict[str, Any],
        candidate_scores: List[PathScore],
        top_k: int = 3,
    ) -> Dict[str, Any]:
        if not candidate_scores:
            return {"selected_paths": [], "reasoning": "", "coverage_analysis": {}}

        bundles = self._prepare_bundles(candidate_scores)
        prompt = self._build_prompt(cve_context, bundles, top_k)

        try:
            raw_response = await self._call_llm(prompt)
            parsed = self._parse_response(raw_response, top_k)
            selected_payloads = self._merge_selection(parsed, bundles, top_k)
            coverage = parsed.coverage_analysis or self._infer_coverage(selected_payloads)
            return {
                "selected_paths": selected_payloads,
                "reasoning": parsed.overall_reasoning,
                "coverage_analysis": coverage,
            }
        except Exception as exc:
            logger.error("LLM分析失败, 使用备用策略: %s", exc)
            fallback = [
                score_to_payload(score, self.language) for score in candidate_scores[:top_k]
            ]
            for item in fallback:
                info = item.setdefault("selection_info", {})
                info["reason"] = "LLM fallback (exception)"
                info["confidence"] = info.get("deterministic_score")
            return {
                "selected_paths": fallback,
                "reasoning": "LLM fallback: 使用确定性评分的结果",
                "coverage_analysis": {},
            }

    def _prepare_bundles(self, candidate_scores: List[PathScore]) -> List[Dict[str, Any]]:
        bundles: List[Dict[str, Any]] = []
        for rank, score in enumerate(candidate_scores):
            payload = score_to_payload(score, self.language)
            payload["selection_info"]["candidate_rank"] = rank
            context_blocks = self.context_builder.build_blocks(score.feature)
            bundles.append(
                {
                    "rank": rank,
                    "score": score,
                    "payload": payload,
                    "context_blocks": context_blocks,
                }
            )
        return bundles

    def _build_prompt(
        self,
        cve_context: Dict[str, Any],
        bundles: List[Dict[str, Any]],
        top_k: int,
    ) -> str:
        parts: List[str] = []
        parts.append("# CVE 信息")
        parts.append(f"- CVE ID: {cve_context.get('cve_id', 'N/A')}")
        parts.append(f"- 漏洞类型: {cve_context.get('vulnerability_type', 'N/A')}")
        parts.append(f"- 预期的 Sink: {cve_context.get('expected_sink', 'N/A')}")
        parts.append(f"- 预期的 Source: {cve_context.get('expected_source', 'N/A')}")
        parts.append("")
        parts.append("# 候选路径")

        for bundle in bundles:
            score = bundle["score"]
            payload = bundle["payload"]
            parts.append(
                f"## 候选 {bundle['rank']} (path_index={payload['index']}, "
                f"det_score={score.total:.3f})"
            )
            parts.append(f"- 流程摘要: {score.feature.flow_summary}")
            parts.append(f"- 关键API: {', '.join(score.feature.dangerous_apis) or '未发现'}")
            parts.append(f"- 确信原因: {', '.join(score.reasons) or '未发现'}")
            parts.append("- 代码上下文:")
            for block in bundle["context_blocks"]:
                parts.append(
                    f"### {block['role'].title()} ({block['location']})"
                )
                parts.append(block["snippet"] or "_未获取代码片段_")
            parts.append("")

        parts.append("# 指令")
        parts.append(
            f"选择 **{top_k}** 个最相关的候选路径进行详细分析。 "
            "应该考虑deterministic score, CVE 同步性以及代码实际可读性"
        )
        parts.append("请以 JSON 格式回应:")
        parts.append(
            json.dumps(
                {
                    "selected_paths": [
                        {
                            "candidate_rank": 0,
                            "llm_alignment_score": 0.85,
                            "reason": "从request.body 到 httpx.get(URL) 的路径匹配 SSRF",
                            "coverage_tags": ["sink:serde.py", "source:request"],
                        }
                    ],
                    "overall_reasoning": "该路径包含...",
                    "coverage_analysis": {"sinks": ["serde.py"]},
                },
                ensure_ascii=False,
                indent=2,
            )
        )

        return "\n".join(parts)

    async def _call_llm(self, prompt: str) -> str:
        if hasattr(self.llm, "chat"):
            return await self.llm.chat(prompt)
        if hasattr(self.llm, "generate"):
            return await self.llm.generate(prompt)
        from langchain.schema import HumanMessage  # type: ignore

        response = await self.llm.agenerate([[HumanMessage(content=prompt)]])
        return response.generations[0][0].text

    def _parse_response(self, response: str, top_k: int) -> AnalyzerResponseModel:
        json_str = self._extract_json(response)
        try:
            parsed = AnalyzerResponseModel.model_validate_json(json_str)
        except ValidationError as exc:
            logger.error("LLM JSON 校验失败: %s", exc)
            raise
        if len(parsed.selected_paths) > top_k:
            parsed.selected_paths = parsed.selected_paths[:top_k]
        return parsed

    def _merge_selection(
        self,
        parsed: AnalyzerResponseModel,
        bundles: List[Dict[str, Any]],
        top_k: int,
    ) -> List[Dict[str, Any]]:
        bundle_map = {bundle["rank"]: bundle for bundle in bundles}
        selected_payloads: List[Dict[str, Any]] = []

        for item in parsed.selected_paths:
            bundle = bundle_map.get(item.candidate_rank)
            if not bundle:
                continue
            payload = json.loads(json.dumps(bundle["payload"]))  # deep copy
            selection_info = payload.setdefault("selection_info", {})
            deterministic_score = selection_info.get("deterministic_score", 0.0)
            blended = 0.7 * deterministic_score + 0.3 * item.llm_alignment_score
            selection_info.update(
                {
                    "llm_alignment_score": item.llm_alignment_score,
                    "reason": item.reason,
                    "coverage_tags": item.coverage_tags,
                    "notes": item.notes,
                    "confidence": round(blended, 4),
                }
            )
            selected_payloads.append(payload)

        if not selected_payloads:
            # Fallback to deterministic top_k ordering
            logger.warning("LLM JSON输出未匹配任何候选, 使用确定性评分的结果")
            return [
                score_to_payload(bundle["score"], self.language)
                for bundle in bundles[:top_k]
            ]

        return selected_payloads[:top_k]

    def _extract_json(self, text: str) -> str:
        text = text.strip()
        if text.startswith("```json"):
            text = text[7:]
        elif text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]
        return text.strip()

    def _infer_coverage(self, selected_paths: List[Dict[str, Any]]) -> Dict[str, Any]:
        sinks = []
        sources = []
        apis = []
        for path in selected_paths:
            sink_loc = path.get("sink_location", {})
            if sink_loc.get("file"):
                sinks.append(sink_loc["file"])
            src_analysis = path.get("source_analysis", {})
            if isinstance(src_analysis, dict) and src_analysis.get("type"):
                sources.append(src_analysis["type"])
            apis.extend(path.get("dangerous_apis") or [])
        return {
            "sink_files": sorted(set(sinks)),
            "source_types": sorted(set(sources)),
            "dangerous_apis": sorted(set(apis)),
        }


__all__ = ["LLMPathAnalyzer"]
