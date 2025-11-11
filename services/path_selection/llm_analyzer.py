"""LLM 语义精排 + 解释模块."""

from __future__ import annotations

import inspect
import json
import logging
import sys
from datetime import datetime
from typing import Any, Awaitable, Callable, Dict, List, Optional, Tuple

from pydantic import BaseModel, Field, ValidationError

from .code_context_builder import CodeContextBuilder
from .path_ranker import PathScore
from .selection_formatter import score_to_payload

logger = logging.getLogger(__name__)


StreamCallback = Callable[[Dict[str, Any]], Awaitable[None]]


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
        event_callback: Optional[StreamCallback] = None,
        stream_meta: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        if not candidate_scores:
            return {"selected_paths": [], "reasoning": "", "coverage_analysis": {}}

        bundles = self._prepare_bundles(candidate_scores)
        prompt = self._build_prompt(cve_context, bundles, top_k)

        try:
            await self._emit_context_preview(
                event_callback=event_callback,
                stream_meta=stream_meta,
                bundles=bundles,
                limit=min(top_k, len(bundles)),
            )
            raw_response = await self._call_llm(
                prompt,
                event_callback=event_callback,
                stream_meta=stream_meta,
            )
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

    async def _call_llm(
        self,
        prompt: str,
        *,
        event_callback: Optional[StreamCallback] = None,
        stream_meta: Optional[Dict[str, Any]] = None,
    ) -> str:
        """调用LLM生成响应"""
        stream_consumed = False
        stream_meta = stream_meta or {}

        # 先尝试自定义客户端接口（可能接受纯字符串prompt）
        if hasattr(self.llm, "chat"):
            result = self.llm.chat(prompt)
            if inspect.isawaitable(result):
                result = await result
            text = self._normalize_llm_response(result)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        message = self._make_human_message(prompt)

        stream_method = getattr(self.llm, "astream", None)
        if stream_method:
            streamed, stream_consumed = await self._try_stream(
                stream_method,
                prompt,
                message,
                event_callback=event_callback,
                stream_meta=stream_meta,
            )
            if streamed is not None:
                if not stream_consumed:
                    await self._dispatch_non_stream_output(
                        streamed, event_callback, stream_meta, stream_consumed
                    )
                return streamed

        if hasattr(self.llm, "ainvoke"):
            response = await self.llm.ainvoke([message])
            text = self._normalize_llm_response(response)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        if hasattr(self.llm, "agenerate"):
            response = await self.llm.agenerate([[message]])
            text = self._normalize_llm_response(response)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        if hasattr(self.llm, "generate"):
            response = self.llm.generate([[message]])
            if inspect.isawaitable(response):
                response = await response
            text = self._normalize_llm_response(response)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        if hasattr(self.llm, "invoke"):
            response = self.llm.invoke([message])
            if inspect.isawaitable(response):
                response = await response
            text = self._normalize_llm_response(response)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        # 最终回退到 ainvoke（若可用）或直接字符串转换
        if hasattr(self.llm, "ainvoke"):
            response = await self.llm.ainvoke([message])
            text = self._normalize_llm_response(response)
            await self._dispatch_non_stream_output(
                text, event_callback, stream_meta, stream_consumed
            )
            return text

        # 无法识别的客户端，直接尝试调用并转换
        response = self.llm(prompt)
        if inspect.isawaitable(response):
            response = await response
        text = self._normalize_llm_response(response)
        await self._dispatch_non_stream_output(
            text, event_callback, stream_meta, stream_consumed
        )
        return text

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

    async def _try_stream(
        self,
        stream_method,
        prompt: str,
        message: Any,
        *,
        event_callback: Optional[StreamCallback],
        stream_meta: Dict[str, Any],
    ) -> Tuple[Optional[str], bool]:
        """尝试使用流式接口获取响应，成功则返回完整文本，失败返回None"""
        try:
            stream = None
            try:
                stream = stream_method([message])
            except TypeError:
                stream = stream_method(prompt)

            if inspect.isawaitable(stream):
                stream = await stream

            if stream is None:
                return "", False

            if hasattr(stream, "__aiter__"):
                chunks: List[str] = []
                display_buffer: List[str] = []
                flush_count = 0
                chunk_index = 0
                self._stream_display_started = False
                async for chunk in stream:
                    text = self._normalize_llm_response(chunk)
                    if text:
                        chunks.append(text)
                        display_buffer.append(text)
                        chunk_index += 1
                        if self._should_flush_stream(display_buffer, text):
                            flush_count += 1
                            await self._emit_stream_output(
                                "".join(display_buffer),
                                flush_count,
                                final=False,
                                event_callback=event_callback,
                                stream_meta=stream_meta,
                            )
                            display_buffer.clear()

                if display_buffer:
                    flush_count += 1
                    await self._emit_stream_output(
                        "".join(display_buffer),
                        flush_count,
                        final=True,
                        event_callback=event_callback,
                        stream_meta=stream_meta,
                    )
                elif chunks:
                    # 如果没有残留缓存，仍然输出一次总览
                    await self._emit_stream_output(
                        "".join(chunks),
                        chunk_index,
                        final=True,
                        event_callback=event_callback,
                        stream_meta=stream_meta,
                    )
                return "".join(chunks), True

            # 如果流式调用返回的是最终结果，直接归一化
            normalized = self._normalize_llm_response(stream)
            return normalized, False

        except Exception as exc:
            logger.debug("LLM流式输出失败，回退到非流式接口: %s", exc, exc_info=True)
        return None, False

    async def _emit_stream_output(
        self,
        text: str,
        index: int,
        *,
        final: bool,
        event_callback: Optional[StreamCallback],
        stream_meta: Dict[str, Any],
    ) -> None:
        """将流式输出直接打印至控制台，贴近对话体验。"""
        if not text:
            return

        if event_callback:
            payload = {
                "type": "agent_thinking",
                "timestamp": datetime.now().isoformat(),
                "agent_name": stream_meta.get("agent_name") or "PathSelection",
                "agent_type": stream_meta.get("agent_type") or "path_selection",
                "step_name": stream_meta.get("step_name") or "LLM 点读精排",
                "message": text,
                "data": {
                    "stream_chunk": text,
                    "is_final": final,
                    "stream_index": index,
                },
            }
            try:
                await event_callback(payload)
            except Exception:
                logger.debug("event_callback 处理流式输出失败", exc_info=True)
            return

        if not getattr(self, "_stream_display_started", False):
            sys.stdout.write("\nLLM: ")
            sys.stdout.flush()
            self._stream_display_started = True

        sys.stdout.write(text)
        if final:
            sys.stdout.write("\n\n")
            self._stream_display_started = False
        sys.stdout.flush()

    def _should_flush_stream(self, buffer: List[str], latest_text: str) -> bool:
        """判断是否需要输出缓存."""
        joined = "".join(buffer)
        if len(joined.strip()) >= 120:
            return True
        # 若最近一次包含句号/逗号/换行，优先输出
        return any(punc in latest_text for punc in {"。", "！", "？", "!", "?", "\n", "，", ","})

    def _make_human_message(self, prompt: str):
        """构造 LangChain HumanMessage，兼容旧版导入路径"""
        try:
            from langchain_core.messages import HumanMessage
        except ImportError:
            try:
                from langchain.schema import HumanMessage  # type: ignore
            except ImportError:
                raise ImportError(
                    "无法导入 HumanMessage，请安装 langchain-core 或 langchain"
                )
        return HumanMessage(content=prompt)

    def _normalize_llm_response(self, response: Any) -> str:
        """统一转换不同LLM响应结构为字符串"""
        if response is None:
            return ""

        if isinstance(response, str):
            return response

        content = getattr(response, "content", None)
        if content is not None:
            if isinstance(content, list):
                parts = []
                for chunk in content:
                    if isinstance(chunk, dict):
                        parts.append(
                            chunk.get("text")
                            or chunk.get("value")
                            or chunk.get("content")
                            or ""
                        )
                    else:
                        parts.append(str(chunk))
                return "".join(parts)
            return str(content)

        message = getattr(response, "message", None)
        if message is not None:
            return self._normalize_llm_response(message)

        generations = getattr(response, "generations", None)
        if generations:
            texts = []
            for generation in generations:
                texts.append(self._normalize_llm_response(getattr(generation, "message", generation)))
            return "\n".join(filter(None, texts))

        if isinstance(response, dict):
            for key in ("content", "text", "message"):
                if key in response and response[key] is not None:
                    value = response[key]
                    if isinstance(value, list):
                        return "".join(str(item) for item in value)
                    return str(value)

        return str(response)

    async def _emit_context_preview(
        self,
        *,
        event_callback: Optional[StreamCallback],
        stream_meta: Optional[Dict[str, Any]],
        bundles: List[Dict[str, Any]],
        limit: int,
    ) -> None:
        if not event_callback or not bundles or limit <= 0:
            return
        meta = stream_meta or {}
        for bundle in bundles[:limit]:
            score: PathScore = bundle["score"]
            feature = score.feature
            payload = {
                "type": "info",
                "timestamp": datetime.now().isoformat(),
                "agent_name": meta.get("agent_name") or "PathSelection",
                "agent_type": meta.get("agent_type") or "path_selection",
                "step_name": meta.get("step_name") or "LLM 点读精排",
                "message": f"候选 {bundle['rank']} 点读上下文",
                "data": {
                    "candidate_rank": bundle["rank"],
                    "deterministic_score": round(score.total, 4),
                    "flow_summary": feature.flow_summary,
                    "dangerous_apis": feature.dangerous_apis,
                    "matched_keywords": feature.matched_keywords,
                    "blocks": bundle["context_blocks"],
                },
            }
            try:
                await event_callback(payload)
            except Exception:
                logger.debug("event_callback 推送上下文失败", exc_info=True)

    async def _dispatch_non_stream_output(
        self,
        text: str,
        event_callback: Optional[StreamCallback],
        stream_meta: Dict[str, Any],
        stream_consumed: bool,
    ) -> None:
        if not text or stream_consumed or not event_callback:
            return
        await self._emit_stream_output(
            text,
            index=1,
            final=True,
            event_callback=event_callback,
            stream_meta=stream_meta,
        )


__all__ = ["LLMPathAnalyzer"]
