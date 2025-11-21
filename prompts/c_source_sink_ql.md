# C/C++ Source-Sink Fallback CodeQL 模板

> 作用：在 C/C++ CodeQL 常规路径查询失败后，用于 **仅识别 source 与 sink** 的回退查询。
> 
> 输入（由外层 Agent 通过占位符注入）：
> - [[CVE_ANALYSIS_REPORT]]：CVE 与补丁分析报告
> - [[SOURCE_ANALYSIS_REPORT]]：Source 分析报告（源点函数、参数、文件位置）
> - [[SINK_ANALYSIS_REPORT]]：Sink 分析报告（危险调用及位置）
> - [[PREVIOUS_ATTEMPTS_CONTEXT]]：此前 CodeQL 生成/执行/修复过程中的错误与上下文（可为空）
>
> 目标：
> - 直接根据上述报告定义 `isSource` / `isSink`，不做复杂路径建模。
> - `isAdditionalFlowStep` 和 `isSanitizer` 使用最小实现（`none()`）。
> - 使用标准 7 参数 `select`，输出格式与正常 path-problem 查询相同，但语义上视作 "source-sink 列表"。
>
---

## 使用说明

- 只输出一个 ```ql 代码块，不能包含额外文字。
- 使用以下骨架，并用报告内容替换：
  - `<HELPER_PREDICATES>`：如 inTargetFile/inTargetFunction 等，用于根据文件名、函数名、结构体字段等锁定位置。
  - `<SOURCE_DEFINITION>`：根据 [[SOURCE_ANALYSIS_REPORT]] 中列出的源点（如参数、返回值、缓冲区）建模。
  - `<SINK_DEFINITION>`：根据 [[SINK_ANALYSIS_REPORT]] 中列出的危险调用/写入位置建模。
- 回退查询不需要实现复杂 `isAdditionalFlowStep`，保持 `none()` 即可。

---

## 查询骨架（只生成下方的 QL 代码）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称 - Source-Sink Fallback>
 * @description <详细描述> (C/C++ Source-Sink Only Fallback - No Custom Path Tracing)
 * @id cpp/<project>-<identifier>-source-sink
 * @tags security, taint, source-sink-only
 * @problem.severity <error|warning|recommendation>
 * @precision <medium|low>
 */

import cpp
import semmle.code.cpp.dataflow.new.DataFlow
import semmle.code.cpp.dataflow.new.TaintTracking

/** ---------- Helper predicates ---------- */
// 利用 CVE / Source / Sink 报告给出的文件路径、函数名、结构体字段等，
// 编写用于筛选目标文件/函数/调用点的辅助谓词。
<HELPER_PREDICATES>

/** ---------- Config ---------- */
module SourceSinkConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源 */
  predicate isSource(DataFlow::Node source) {
    // 直接根据 [[SOURCE_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中的源点信息建模
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点 */
  predicate isSink(DataFlow::Node sink) {
    // 直接根据 [[SINK_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中的危险调用信息建模
    <SINK_DEFINITION>
  }

  /** Additional flow steps: 回退查询不建模额外流步骤 */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    none()
  }

  /** Sanitizers: 回退查询中不使用净化器 */
  predicate isSanitizer(DataFlow::Node node) {
    none()
  }
}

// 定义全局污点追踪模块（仅为满足 path-problem 要求和统一输出格式）
module SourceSinkFlow = TaintTracking::Global<SourceSinkConfig>;
// 必须在 module 定义之后导入 PathGraph
import SourceSinkFlow::PathGraph

// 不调用 flowPath，而是直接根据 isSource / isSink 的结果枚举所有 source-sink 组合。
from SourceSinkFlow::PathNode source, SourceSinkFlow::PathNode sink
where
  SourceSinkConfig::isSource(source.getNode()) and
  SourceSinkConfig::isSink(sink.getNode())
select sink.getNode(), source, sink,
  "Potential source-sink pair (fallback query - path not traced)",
  source.getNode(), "source", sink.getNode(), "sink"
```
