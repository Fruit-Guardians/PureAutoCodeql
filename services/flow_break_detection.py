"""
Flow break detection and automatic additional flow clause generation.

该模块按照 OpenSpec `add-flow-break-detection` 变更要求，实现以下能力：
- 从现有 CodeQL 查询中抽取 Source/Sink/isAdditionalFlowStep 定义
- 构建断流检测查询骨架并执行，解析返回的断流点
- 基于断流点生成 isAdditionalFlowStep 子句，进行去重与限额控制
- 将新增子句安全合并回原查询，并提供遥测信息

实际实现中尽量保持语言无关，必要时根据语言作轻量差异化处理。
"""

from __future__ import annotations

import json
import logging
import os
import re
import textwrap
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from utils.codeql import execute_codeql_query
from utils.sarif_utils import to_path

logger = logging.getLogger(__name__)


class FlowBreakDetectionError(RuntimeError):
    """Raised when flow break detection cannot proceed."""


def _strtobool(value: str) -> bool:
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


@dataclass
class FlowBreakDetectionConfig:
    """配置断流检测与补边生成的参数。"""

    enable: bool = True
    max_rounds: int = 3
    max_clauses_per_round: int = 5
    max_total_clauses: int = 12
    candidate_soft_limit: int = 200
    candidate_cap: int = 50
    detection_timeout: int = 120
    candidate_min_column_gap: int = 1
    telemetry: bool = True

    @classmethod
    def from_environment(cls) -> "FlowBreakDetectionConfig":
        env = os.environ
        return cls(
            enable=_strtobool(env.get("ENABLE_FLOW_BREAK_DETECTION", "true")),
            max_rounds=int(env.get("MAX_FLOW_PATCH_ROUNDS", "3")),
            max_clauses_per_round=int(env.get("MAX_FLOW_PATCH_CLAUSES_PER_ROUND", "5")),
            max_total_clauses=int(env.get("MAX_FLOW_PATCH_TOTAL_CLAUSES", "12")),
            candidate_soft_limit=int(env.get("FLOW_BREAK_SOFT_LIMIT", "200")),
            candidate_cap=int(env.get("FLOW_BREAK_CANDIDATE_CAP", "50")),
            detection_timeout=int(env.get("FLOW_BREAK_TIMEOUT", "120")),
            candidate_min_column_gap=int(env.get("FLOW_BREAK_MIN_COL_GAP", "1")),
            telemetry=_strtobool(env.get("FLOW_BREAK_TELEMETRY", "true")),
        )

    def to_dict(self) -> Dict[str, int | bool]:
        return {
            "enable": self.enable,
            "max_rounds": self.max_rounds,
            "max_clauses_per_round": self.max_clauses_per_round,
            "max_total_clauses": self.max_total_clauses,
            "candidate_soft_limit": self.candidate_soft_limit,
            "candidate_cap": self.candidate_cap,
            "detection_timeout": self.detection_timeout,
            "candidate_min_column_gap": self.candidate_min_column_gap,
            "telemetry": self.telemetry,
        }


@dataclass
class FlowQueryComponents:
    """抽取自原始 QL 查询的关键组件。"""

    source_body: str
    sink_body: str
    addition_body: str
    source_span: Tuple[int, int]
    sink_span: Tuple[int, int]
    addition_span: Tuple[int, int]
    addition_header: str
    indentation: str


@dataclass(frozen=True)
class FlowBreakCandidate:
    """断流检测返回的候选信息。"""

    file: str
    start_line: int
    start_column: int
    end_line: int
    end_column: int
    message: str = ""
    rule_id: str = ""
    classification: str = "unknown"
    snippet: str = ""

    @property
    def key(self) -> Tuple[str, int, int, str]:
        return (self.file, self.start_line, self.start_column, self.classification)


@dataclass(frozen=True)
class GeneratedFlowClause:
    """生成的 isAdditionalFlowStep 子句结果。"""

    clause: str
    key: Tuple[str, int, int, str]
    candidate: FlowBreakCandidate


@dataclass
class FlowBreakDetectionResult:
    """断流检测执行结果。"""

    applied: bool
    patched_query: Optional[str] = None
    clauses: Sequence[GeneratedFlowClause] = field(default_factory=list)
    candidates: Sequence[FlowBreakCandidate] = field(default_factory=list)
    skipped: bool = False
    reason: Optional[str] = None
    telemetry: Dict[str, object] = field(default_factory=dict)


class FlowQueryExtractor:
    """负责从原始查询解析 Source/Sink/isAdditionalFlowStep 定义。"""

    _SOURCE_PATTERN = re.compile(
        r"predicate\s+isSource\s*\([^)]*\)\s*\{", re.IGNORECASE
    )
    _SINK_PATTERN = re.compile(
        r"predicate\s+isSink\s*\([^)]*\)\s*\{", re.IGNORECASE
    )
    _ADDITION_PATTERN = re.compile(
        r"predicate\s+isAdditionalFlowStep\s*\([^)]*\)\s*\{", re.IGNORECASE
    )

    @staticmethod
    def _extract_block(source: str, pattern: re.Pattern[str]) -> Tuple[str, Tuple[int, int], str, str]:
        match = pattern.search(source)
        if not match:
            raise FlowBreakDetectionError(
                f"未找到 {pattern.pattern} 对应的谓词定义，无法执行断流检测"
            )

        start_brace = source.find("{", match.start())
        if start_brace == -1:
            raise FlowBreakDetectionError("谓词定义缺失 '{'，QL 语法不完整")

        depth = 0
        end_idx = None
        for idx in range(start_brace, len(source)):
            char = source[idx]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    end_idx = idx
                    break
        if end_idx is None:
            raise FlowBreakDetectionError("谓词定义缺失对应的 '}'，QL 语法不完整")

        header = source[match.start():start_brace].strip()
        body = source[start_brace + 1:end_idx].strip()
        span = (match.start(), end_idx + 1)
        indentation = FlowQueryExtractor._infer_indentation(source, match.start())
        return body, span, header, indentation

    @staticmethod
    def _infer_indentation(source: str, index: int) -> str:
        line_start = source.rfind("\n", 0, index)
        if line_start == -1:
            return ""
        indentation = []
        idx = line_start + 1
        while idx < len(source) and source[idx] in (" ", "\t"):
            indentation.append(source[idx])
            idx += 1
        return "".join(indentation)

    def extract(self, query: str) -> FlowQueryComponents:
        src_body, src_span, _, indentation = self._extract_block(query, self._SOURCE_PATTERN)
        sink_body, sink_span, _, _ = self._extract_block(query, self._SINK_PATTERN)
        try:
            addition_body, addition_span, addition_header, _ = self._extract_block(
                query, self._ADDITION_PATTERN
            )
        except FlowBreakDetectionError:
            # 如果 isAdditionalFlowStep 缺失，则构造一个默认的 none() 定义
            addition_header = "predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst)"
            addition_body = "none()"
            insertion_point = self._determine_insertion_point(query, sink_span[1])
            addition_span = (insertion_point, insertion_point)
        return FlowQueryComponents(
            source_body=src_body,
            sink_body=sink_body,
            addition_body=addition_body,
            source_span=src_span,
            sink_span=sink_span,
            addition_span=addition_span,
            addition_header=addition_header,
            indentation=indentation,
        )

    @staticmethod
    def _determine_insertion_point(query: str, sink_end: int) -> int:
        """在 sink 谓词之后找到一个合理的插入点。"""
        remainder = query[sink_end:]
        match = re.search(r"\bmodule\b", remainder, re.IGNORECASE)
        if match:
            return sink_end + match.start()
        return len(query)


class FlowDetectionSkeletonBuilder:
    """构造断流检测 QL 查询骨架。"""

    _TEMPLATE = textwrap.dedent(
        """
        import {language}
        import semmle.{language}.dataflow.new.DataFlow
        import semmle.{language}.dataflow.new.TaintTracking

        /** ====== 与上个查询相同的 Source/Sink 和补边 ====== */
        class FixedSourceNode extends DataFlow::Node { FixedSourceNode() { <SINK> } }
        class FixedSinkNode   extends DataFlow::Node { FixedSinkNode()   { <SOURCE> } }

        /** 任意节点的简便定义（配合 Config 使用） */
        predicate anyNode(DataFlow::Node n) { exists(DataFlow::Node m | m = n) }

        /** 前向：fixed source -> ANY */
        module ForwardCfg implements DataFlow::ConfigSig {{
          predicate isSource(DataFlow::Node src) {{ src instanceof FixedSourceNode }}
          predicate isSink(DataFlow::Node sink)  {{ anyNode(sink) }}

          predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {{ <ISADDITION> }}
        }}
        module F = TaintTracking::Global<ForwardCfg>;

        /** “后向”：ANY -> fixed sink（仍然正向求解，只是把 sink 当成目标） */
        module BackwardCfg implements DataFlow::ConfigSig {{
          predicate isSource(DataFlow::Node src) {{ anyNode(src) }}
          predicate isSink(DataFlow::Node sink)  {{ sink instanceof FixedSinkNode }}
        }}
        module B = TaintTracking::Global<BackwardCfg>;

        /** 前向可达：从固定 source 能到达的节点 n */
        predicate forwardReachable(DataFlow::Node n) {{
          exists(DataFlow::Node s, DataFlow::Node t |
            F::flow(s, t) and t = n
          )
        }}

        /** “后向”可达：能流向固定 sink 的节点，把这些节点所在的文件的所有节点定义为可达，最后取交集可以找到 source 流到的断流处 n */
        predicate backwardReachable(DataFlow::Node n) {{
          exists(DataFlow::Node s, DataFlow::Node t |
            B::flow(s, t) and s.getLocation().getFile() = n.getLocation().getFile()
          )
        }}

        module FlowBreakSupport {{
          predicate connectSameLine(
            string relativePath, int line, int pivotColumn,
            DataFlow::Node src, DataFlow::Node dst
          ) {{
            src.getLocation().getFile().getRelativePath() = relativePath and
            dst.getLocation().getFile().getRelativePath() = relativePath and
            src.getLocation().getStartLine() = line and
            dst.getLocation().getStartLine() = line and
            src.getLocation().getStartColumn() <= pivotColumn and
            dst.getLocation().getStartColumn() >= pivotColumn and
            src != dst
          }}
        }}

        from DataFlow::Node n, DataFlow::Node srcGuess, DataFlow::Node dstGuess
        where
          forwardReachable(n) and
          backwardReachable(n) and
          FlowBreakSupport::connectSameLine(
            n.getLocation().getFile().getRelativePath(),
            n.getLocation().getStartLine(),
            n.getLocation().getStartColumn(),
            srcGuess,
            dstGuess
          )
        select
          n,
          n.getLocation(),
          "Flow break candidate",
          srcGuess,
          dstGuess
        """
    ).strip()

    _LANGUAGE_IMPORT_MAP = {
        "java": "code.java",
        "python": "python",
        "cpp": "code.cpp",
        "c": "code.cpp",
    }

    def build(self, components: FlowQueryComponents, language: str) -> str:
        lang_key = (language or "").lower()
        if lang_key not in self._LANGUAGE_IMPORT_MAP:
            lang_key = "java"

        sink = components.sink_body or "none()"
        source = components.source_body or "none()"
        addition = components.addition_body or "none()"

        query = self._TEMPLATE.replace(
            "{language}", self._LANGUAGE_IMPORT_MAP[lang_key]
        )
        query = query.replace("<SINK>", sink.strip() or "none()")
        query = query.replace("<SOURCE>", source.strip() or "none()")
        query = query.replace("<ISADDITION>", addition.strip() or "none()")
        return query


class FlowBreakClauseGenerator:
    """根据断流候选生成 isAdditionalFlowStep 子句。"""

    CLAUSE_TEMPLATE = textwrap.dedent(
        """
        exists(
          DataFlow::Node __fb_src,
          DataFlow::Node __fb_dst |
          FlowBreakSupport::connectSameLine("{file}", {line}, {pivot}, __fb_src, __fb_dst)
        | src = __fb_src and dst = __fb_dst
        )
        """
    ).strip()

    def __init__(self, config: FlowBreakDetectionConfig) -> None:
        self._config = config

    def generate_clauses(
        self,
        candidates: Sequence[FlowBreakCandidate],
        *,
        max_count: int,
        existing_keys: Iterable[Tuple[str, int, int, str]],
    ) -> List[GeneratedFlowClause]:
        existing = set(existing_keys)
        clauses: List[GeneratedFlowClause] = []

        for candidate in candidates:
            if len(clauses) >= max_count:
                break

            if candidate.key in existing:
                continue

            pivot = max(candidate.start_column, self._config.candidate_min_column_gap)
            clause = self.CLAUSE_TEMPLATE.format(
                file=candidate.file,
                line=candidate.start_line,
                pivot=pivot,
            )

            clauses.append(
                GeneratedFlowClause(
                    clause=clause,
                    key=candidate.key,
                    candidate=candidate,
                )
            )
            existing.add(candidate.key)
        return clauses


class FlowBreakQueryMerger:
    """负责将新增子句安全合并至原查询。"""

    _HELPER_MARKER = "module FlowBreakSupport"

    def __init__(self, extractor: FlowQueryExtractor) -> None:
        self._extractor = extractor

    @staticmethod
    def _indent(text: str, indent: str) -> str:
        lines = text.splitlines()
        return "\n".join(indent + line if line.strip() else line for line in lines)

    def merge(
        self,
        query: str,
        components: FlowQueryComponents,
        clauses: Sequence[GeneratedFlowClause],
    ) -> str:
        if not clauses:
            return query

        # 保证 FlowBreakSupport helper 存在
        if self._HELPER_MARKER not in query:
            helper = textwrap.dedent(
                """

                module FlowBreakSupport {
                  predicate connectSameLine(
                    string relativePath, int line, int pivotColumn,
                    DataFlow::Node src, DataFlow::Node dst
                  ) {
                    src.getLocation().getFile().getRelativePath() = relativePath and
                    dst.getLocation().getFile().getRelativePath() = relativePath and
                    src.getLocation().getStartLine() = line and
                    dst.getLocation().getStartLine() = line and
                    src.getLocation().getStartColumn() <= pivotColumn and
                    dst.getLocation().getStartColumn() >= pivotColumn and
                    src != dst
                  }
                }
                """
            ).rstrip()
            insertion = components.addition_span[0]
            query = query[:insertion] + helper + "\n\n" + query[insertion:]

            # 重新提取，因为插入 helper 后 span 发生变化
            components = self._extractor.extract(query)

        addition_body = components.addition_body.strip()
        formatted_clauses = [
            "(\n" + self._indent(clause.clause.strip(), components.indentation + "  ") + "\n" + components.indentation + ")"
            for clause in clauses
        ]

        if addition_body.lower() == "none()":
            merged_body = " or\n".join(
                self._indent(clause.clause.strip(), components.indentation + "  ") for clause in clauses
            )
            merged_body = self._indent(merged_body, "")
        elif not addition_body:
            merged_body = " or\n".join(formatted_clauses)
        else:
            existing = "(\n" + self._indent(addition_body, components.indentation + "  ") + "\n" + components.indentation + ")"
            merged_body = "\n".join(
                [existing, components.indentation + "or"] + formatted_clauses
            )

        new_block = (
            components.indentation
            + components.addition_header
            + " {\n"
            + self._indent(merged_body.strip(), components.indentation + "  ")
            + "\n"
            + components.indentation
            + "}\n"
        )

        start, end = components.addition_span
        return query[:start] + new_block + query[end:]


class FlowBreakDetectionManager:
    """协调断流检测流程。"""

    def __init__(self, config: Optional[FlowBreakDetectionConfig] = None) -> None:
        self._config = config or FlowBreakDetectionConfig.from_environment()
        self._extractor = FlowQueryExtractor()
        self._skeleton = FlowDetectionSkeletonBuilder()
        self._clause_generator = FlowBreakClauseGenerator(self._config)
        self._merger = FlowBreakQueryMerger(self._extractor)

    def try_patch(
        self,
        *,
        original_query: str,
        language: str,
        database_path: str,
        pack_root: Path,
        source_root: Optional[Path],
        existing_clause_keys: Iterable[Tuple[str, int, int, str]],
        total_clause_count: int,
        round_index: int,
    ) -> FlowBreakDetectionResult:
        telemetry: Dict[str, object] = {
            "round": round_index,
            "config": self._config.to_dict(),
        }

        if not self._config.enable:
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason="Flow break detection disabled by configuration",
                telemetry=telemetry,
            )

        try:
            components = self._extractor.extract(original_query)
        except FlowBreakDetectionError as exc:
            telemetry["extract_error"] = str(exc)
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason=str(exc),
                telemetry=telemetry,
            )

        detection_query = self._skeleton.build(components, language)
        detection_file = pack_root / f"flow_break_detection_{uuid.uuid4().hex}.ql"

        exec_result = execute_codeql_query(
            detection_query,
            database_path=database_path,
            language=language,
            query_file=detection_file,
        )

        if not exec_result.get("success"):
            telemetry["execution_error"] = exec_result.get("output", "Unknown execution failure")
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason=telemetry["execution_error"],
                telemetry=telemetry,
            )

        sarif_path = exec_result.get("sarif_path")
        if not sarif_path or not Path(sarif_path).exists():
            telemetry["execution_warning"] = "SARIF 输出缺失"
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason="未生成断流检测结果",
                telemetry=telemetry,
            )

        candidates = self._parse_candidates(Path(sarif_path), source_root=source_root)
        telemetry["candidate_total"] = len(candidates)

        if len(candidates) == 0:
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason="未发现断流候选",
                candidates=candidates,
                telemetry=telemetry,
            )

        if len(candidates) > self._config.candidate_soft_limit:
            candidates = candidates[: self._config.candidate_cap]
            telemetry["candidate_truncated"] = len(candidates)

        remaining_capacity = max(0, self._config.max_total_clauses - total_clause_count)
        if remaining_capacity <= 0:
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason="已达到补边子句总上限",
                candidates=candidates,
                telemetry=telemetry,
            )

        clauses = self._clause_generator.generate_clauses(
            candidates,
            max_count=min(self._config.max_clauses_per_round, remaining_capacity),
            existing_keys=existing_clause_keys,
        )

        telemetry["generated_clause_count"] = len(clauses)

        if not clauses:
            return FlowBreakDetectionResult(
                applied=False,
                skipped=True,
                reason="无新增补边子句",
                candidates=candidates,
                telemetry=telemetry,
            )

        merged_query = self._merger.merge(original_query, components, clauses)

        return FlowBreakDetectionResult(
            applied=True,
            patched_query=merged_query,
            clauses=clauses,
            candidates=candidates,
            telemetry=telemetry,
        )

    def _parse_candidates(
        self,
        sarif_path: Path,
        *,
        source_root: Optional[Path],
    ) -> List[FlowBreakCandidate]:
        data = json.loads(sarif_path.read_text(encoding="utf-8"))
        runs = data.get("runs", []) or []
        candidates: List[FlowBreakCandidate] = []
        for run in runs:
            results = run.get("results", []) or []
            for result in results:
                locations = result.get("locations", []) or []
                if not locations:
                    continue
                phys = (locations[0] or {}).get("physicalLocation", {}) or {}
                region = phys.get("region", {}) or {}
                artifact = phys.get("artifactLocation", {}) or {}
                file_path = to_path(artifact.get("uri", ""))

                candidate = FlowBreakCandidate(
                    file=file_path,
                    start_line=int(region.get("startLine") or 0),
                    start_column=int(region.get("startColumn") or 0),
                    end_line=int(region.get("endLine") or region.get("startLine") or 0),
                    end_column=int(region.get("endColumn") or region.get("startColumn") or 0),
                    message=(result.get("message", {}) or {}).get("text", "") or "",
                    rule_id=result.get("ruleId", ""),
                    snippet=self._load_snippet(file_path, region.get("startLine"), source_root=source_root),
                )
                candidates.append(candidate)
        return candidates

    def _load_snippet(
        self,
        relative_path: str,
        start_line: Optional[int],
        *,
        source_root: Optional[Path],
        context_lines: int = 0,
    ) -> str:
        if not source_root or not start_line:
            return ""
        try:
            target = Path(source_root) / relative_path
            if not target.exists():
                return ""
            with target.open("r", encoding="utf-8", errors="ignore") as handler:
                lines = handler.readlines()
            index = max(0, start_line - 1)
            begin = max(0, index - context_lines)
            end = min(len(lines), index + context_lines + 1)
            return "".join(lines[begin:end]).strip()
        except Exception:
            return ""


__all__ = [
    "FlowBreakDetectionConfig",
    "FlowBreakDetectionManager",
    "FlowBreakDetectionResult",
    "FlowBreakCandidate",
    "GeneratedFlowClause",
]


