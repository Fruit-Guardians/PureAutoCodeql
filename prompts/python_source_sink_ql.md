# Python Source-Sink Fallback CodeQL 模板

> 作用：当所有常规 CodeQL 生成与修复尝试都失败后，作为 **回退方案** 使用，仅识别 source 和 sink，并尝试利用之前失败查询中的部分路径逻辑来构建“合成路径”。
> 
> 输入由外层 Agent 注入（通过占位符）：
> - [[CVE_ANALYSIS_REPORT]]：CVE 分析报告（包含漏洞描述、补丁 diff 摘要等）
> - [[SOURCE_ANALYSIS_REPORT]]：Source 分析报告（列出候选 source 定义与位置）
> - [[SINK_ANALYSIS_REPORT]]：Sink 分析报告（列出候选 sink 定义与位置）
> - [[PREVIOUS_ATTEMPTS_CONTEXT]]：之前生成/执行/修复尝试的错误日志与上下文（包含之前的 QL 代码片段，特别是 `isAdditionalFlowStep` 部分）
>
> 目标：
> - 直接根据上述报告 **精确建模 isSource / isSink**。
> - **利用之前的 isAdditionalFlowStep 逻辑**（如果有），将其放入 `ExtraSteps` 模块中，作为中间路径片段。
> - 构建 **合成路径**：强制连接 `Source -> ExtraSteps Start` 和 `ExtraSteps End -> Sink`，以及 `Source -> Sink` (如果没有 ExtraSteps)。
> - 生成的查询使用标准 **7 参数 select**，输出格式与正常 path-problem 查询一致。
>
> 注意：本模板适用于 Python New DataFlow API。

---

## 使用说明

- 只需要输出 **一个** ```ql 代码块，且不能包含任何解释文字或额外 Markdown**。
- 必须使用下面的查询骨架，并用报告中的真实符号和位置替换占位：
  - `<HELPER_PREDICATES>`：用于定位特定文件/类/方法的辅助谓词。
  - `<EXTRA_STEP_LOGIC>`：**关键**。提取上一次生成的 QL 中 `isAdditionalFlowStep` 的逻辑（如果有），填入 `ExtraSteps.extraStep` 谓词中。如果上一次没有或很简略，可以为空。
  - `<SOURCE_DEFINITION>`：基于 [[SOURCE_ANALYSIS_REPORT]]，精确定义污染源。
  - `<SINK_DEFINITION>`：基于 [[SINK_ANALYSIS_REPORT]]，精确定义汇聚点。
- **合成路径逻辑**：
  - 模板中已经内置了将 Source 连接到 ExtraSteps 起点，以及将 ExtraSteps 终点连接到 Sink 的逻辑。
  - 这样即使完整路径不通，只要 Source/Sink 定义正确，且中间有一部分 AdditionalStep 匹配，也能展示出“断续”的路径图。

---

## 查询骨架（只生成下方的 QL 代码）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称 - Source-Sink Fallback>
 * @description <详细描述> (Source-Sink Only Fallback - Synthetic Path Tracing)
 * @id python/<project>-<identifier>-source-sink
 * @tags security, taint, source-sink-only
 * @problem.severity <error|warning|recommendation>
 * @precision <medium|low>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking

/** ---------- Helper predicates ---------- */
// 使用 CVE / Source / Sink 报告中给出的文件路径、类名、方法名等信息，
// 辅助精确定位 source / sink 所在位置。例如:
// - inTargetFile()
// - inTargetFunction()
<HELPER_PREDICATES>

module ExtraSteps {
  /**
   * 原来另外一个查询里的 isAdditionalFlowStep 逻辑，
   * 这里改名为 extraStep，方便复用。
   */
  predicate extraStep(DataFlow::Node node1, DataFlow::Node node2) {
    // 填入上一次尝试中 isAdditionalFlowStep 的逻辑
    // 例如: Flow through specific getters/setters or partial flows
    <EXTRA_STEP_LOGIC>
  }

  /** 方便确定“中间图”的起点 / 终点 */
  predicate extraStart(DataFlow::Node n) {
    exists(DataFlow::Node m | extraStep(n, m))
  }

  predicate extraEnd(DataFlow::Node n) {
    exists(DataFlow::Node m | extraStep(m, n))
  }
}

/** ---------- Config ---------- */
module SourceSinkConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源（必须用报告中的具体符号） */
  predicate isSource(DataFlow::Node source) {
    // 直接根据 [[SOURCE_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中列出的输入点建模
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点（必须用报告中的具体符号） */
  predicate isSink(DataFlow::Node sink) {
    // 直接根据 [[SINK_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中列出的危险调用建模
    <SINK_DEFINITION>
  }

  /** 关键修改：人为连一条边，把任意 source 直接连到任意 sink，或者连接到中间步骤 */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // ① 原本那张“中间图”的边
    ExtraSteps::extraStep(src, dst)
    or
    // ② 把所有 source 接到“中间图”的起点节点上
    (isSource(src) and ExtraSteps::extraStart(dst))
    or
    // ③ 把“中间图”的终点节点接到所有 sink
    (ExtraSteps::extraEnd(src) and isSink(dst))
  }

  predicate isSanitizer(DataFlow::Node node) { none() }
}

module SourceSinkFlow = TaintTracking::Global<SourceSinkConfig>;
import SourceSinkFlow::PathGraph

from SourceSinkFlow::PathNode source, SourceSinkFlow::PathNode sink
where
  SourceSinkConfig::isSource(source.getNode()) and
  SourceSinkConfig::isSink(sink.getNode())
select sink.getNode(), source, sink,
  "Potential source-sink pair (fallback query - synthetic path)",
  source.getNode(), "source", sink.getNode(), "sink"
```
