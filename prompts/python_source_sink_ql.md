# Python Source-Sink Fallback CodeQL 模板

> 作用：在常规 Python CodeQL 生成/修复失败后，用于回退地识别 source 和 sink。
> 
> 输入由外层 Agent 注入（通过占位符）：
> - [[CVE_ANALYSIS_REPORT]]：CVE 分析报告（漏洞与场景描述）
> - [[SOURCE_ANALYSIS_REPORT]]：Source 分析报告（列出候选污点源）
> - [[SINK_ANALYSIS_REPORT]]：Sink 分析报告（列出候选危险调用）
> - [[PREVIOUS_ATTEMPTS_CONTEXT]]：之前轮次的错误日志和上下文（可为空）
>
> 目标：
> - 直接根据上述报告 **精确建模 isSource / isSink**，不要发明通用 "any input" 模式。
> - **不实现复杂的 isAdditionalFlowStep 逻辑**，保持 `none()`，避免额外路径追踪开销。
> - **直接使用上次一次生成的 isSource / isSink**，不要发明新的。
> - 生成的查询使用标准 **7 参数 select**，输出格式与正常 path-problem 查询一致，但仅用于枚举 source-sink 对。
>
---

## 使用说明

- 最终响应必须且仅能是一个 ```ql 代码块，不能包含任何额外解释文本。
- 必须使用下方骨架，并用报告内容替换：
  - `<HELPER_PREDICATES>`：基于文件/模块/函数名等的辅助谓词（可为空）。
  - `<SOURCE_DEFINITION>`：根据 [[SOURCE_ANALYSIS_REPORT]]、[[CVE_ANALYSIS_REPORT]] 精确定义 Source。
  - `<SINK_DEFINITION>`：根据 [[SINK_ANALYSIS_REPORT]]、[[CVE_ANALYSIS_REPORT]] 精确定义 Sink。
- **准确性要求（生成代码前必须执行）**：
  - **第一步：必须使用 `lsplookup` 工具验证所有 CodeQL 类型和谓词**：
    - 例如：查询 `Call`、`Function`、`Attribute`、`Name` 等
    - 例如：查询如何正确获取函数调用的参数、返回值等
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
  - 不要引入与报告无关的泛化模式（如任意 `os.system` 调用）。

---

## 查询骨架（只生成下方的 QL 代码）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称 - Source-Sink Fallback>
 * @description <详细描述> (Python Source-Sink Only Fallback - No Custom Path Tracing)
 * @id python/<project>-<identifier>-source-sink
 * @tags security, taint, source-sink-only
 * @problem.severity <error|warning|recommendation>
 * @precision <medium|low>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking

/** ---------- Helper predicates ---------- */
// 利用 CVE / Source / Sink 报告中给出的模块路径、函数名、参数位置等信息，
// 精确锁定潜在的 source / sink 所在位置。
<HELPER_PREDICATES>

/** ---------- Config ---------- */
module SourceSinkConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源 */
  predicate isSource(DataFlow::Node source) {
    // 使用 [[SOURCE_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中的具体函数/参数信息
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点 */
  predicate isSink(DataFlow::Node sink) {
    // 使用 [[SINK_ANALYSIS_REPORT]] 和 [[CVE_ANALYSIS_REPORT]] 中的具体危险调用信息
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

module SourceSinkFlow = TaintTracking::Global<SourceSinkConfig>;
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
