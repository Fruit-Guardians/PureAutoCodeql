# Java Source-Sink Fallback CodeQL 模板

> 作用：当所有常规 CodeQL 生成与修复尝试都失败后，作为 **回退方案** 使用，仅识别 source 和 sink。
> 
> 输入由外层 Agent 注入（通过占位符）：
> - [[CVE_ANALYSIS_REPORT]]：CVE 分析报告（包含漏洞描述、补丁 diff 摘要等）
> - [[SOURCE_ANALYSIS_REPORT]]：Source 分析报告（列出候选 source 定义与位置）
> - [[SINK_ANALYSIS_REPORT]]：Sink 分析报告（列出候选 sink 定义与位置）
> - [[PREVIOUS_ATTEMPTS_CONTEXT]]：之前生成/执行/修复尝试的错误日志与上下文（可为空）
>
> 目标：
> - 直接根据上述报告 **精确建模 isSource / isSink**，不要发明通用 "any input" 模式。
> - **不实现复杂的 isAdditionalFlowStep 逻辑**，保持 `none()`，避免额外路径追踪开销。
> - **直接使用上次一次生成的 isSource / isSink**，不要发明新的。
> - 生成的查询使用标准 **7 参数 select**，输出格式与正常 path-problem 查询一致，但仅用于枚举 source-sink 对。
>
---

## 使用说明

- 只需要输出 **一个** ```ql 代码块，且不能包含任何解释文字或额外 Markdown**。
- 必须使用下面的查询骨架，并用 CVE / Source / Sink 报告中的真实符号和位置替换占位：
  - `<HELPER_PREDICATES>`：用于定位特定文件/类/方法的辅助谓词（可为空）。
  - `<SOURCE_DEFINITION>`：基于 [[SOURCE_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]]，精确定义污染源。
  - `<SINK_DEFINITION>`：基于 [[SINK_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]]，精确定义汇聚点。
- **准确性要求（生成代码前必须执行）**：
  - **第一步：必须使用 `lsplookup` 工具验证所有 CodeQL 类型和谓词**：
    - 例如：查询 `MethodAccess`、`Method`、`Parameter`、`Call` 等
    - 例如：查询如何正确获取方法调用的参数、返回值等
    - **禁止捏造不存在的类名或方法名**
  - **第二步：基于 `lsplookup` 返回的正确 API 编写代码**
  - 必须确保 QL 中使用的类、谓词在标准库中真实存在。
  - **`isAdditionalFlowStep` 必须逐字复制下方骨架中的实现**：
    ```ql
    predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
      //即使Source不通Sink，也认为是额外流步骤，可以得到头尾节点路径图
      isSource(src) and isSink(dst)
    }
    ```
    **绝对禁止改为 `none()`**，必须保持 `isSource(src) and isSink(dst)` 的逻辑。
- **禁止**：
  - 不要在 `isAdditionalFlowStep` 中实现复杂的自定义流步骤逻辑（除了骨架中的 `isSource(src) and isSink(dst)`）。
  - 不要添加 `flowPath` 以外的额外路径追踪逻辑（本模板完全不调用 `flowPath`）。
  - 不要引入与报告无关的泛化模式（如任意 `Runtime.exec`）。

---

## 查询骨架（只生成下方的 QL 代码）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称 - Source-Sink Fallback>
 * @description <详细描述> (Source-Sink Only Fallback - No Custom Path Tracing)
 * @id java/<project>-<identifier>-source-sink
 * @tags security, taint, source-sink-only
 * @problem.severity <error|warning|recommendation>
 * @precision <medium|low>
 */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

/** ---------- Helper predicates ---------- */
// 使用 CVE / Source / Sink 报告中给出的文件路径、类名、方法名等信息，
// 辅助精确定位 source / sink 所在位置。例如:
// - inTargetFile()
// - inTargetMethod()
<HELPER_PREDICATES>

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

  /** Additional flow steps: 回退查询中不建模额外流步骤 */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    //即使Source不通Sink，也认为是额外流步骤，可以得到头尾节点路径图
    isSource(src) and isSink(dst)
  }

  /** Sanitizers: 回退查询中不使用净化器 */
  predicate isSanitizer(DataFlow::Node node) {
    none()
  }
}

// 回退查询仍使用标准 TaintTracking PathGraph，以保持 path-problem 兼容性。
module SourceSinkFlow = TaintTracking::Global<SourceSinkConfig>;
import SourceSinkFlow::PathGraph

// 注意：此处 **不调用 flowPath**，而是直接枚举所有满足 isSource / isSink 的点对。
from SourceSinkFlow::PathNode source, SourceSinkFlow::PathNode sink
where
  SourceSinkConfig::isSource(source.getNode()) and
  SourceSinkConfig::isSink(sink.getNode())
select sink.getNode(), source, sink,
  "Potential source-sink pair (fallback query - path not traced)",
  source.getNode(), "source", sink.getNode(), "sink"
```
