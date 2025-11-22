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
> - 直接根据上述报告 **精确建模 isSource / isSink**，不要发明通用 "any input" 模式。
> - **不实现复杂的 isAdditionalFlowStep 逻辑**，保持 `none()`，避免额外路径追踪开销。
> - **直接使用上次一次生成的 isSource / isSink**，不要发明新的。
> - 生成的查询使用标准 **7 参数 select**，输出格式与正常 path-problem 查询一致，但仅用于枚举 source-sink 对。
>
---

## 使用说明

- 只输出一个 ```ql 代码块，不能包含额外文字。
- 使用以下骨架，并用报告内容替换：
  - `<HELPER_PREDICATES>`：如 inTargetFile/inTargetFunction 等，用于根据文件名、函数名、结构体字段等锁定位置。
  - `<SOURCE_DEFINITION>`：根据 [[SOURCE_ANALYSIS_REPORT]] 中列出的源点（如参数、返回值、缓冲区）建模。
  - `<SINK_DEFINITION>`：根据 [[SINK_ANALYSIS_REPORT]] 中列出的危险调用/写入位置建模。
- **准确性要求（生成代码前必须执行）**：
  - **第一步：必须使用 `lsplookup` 工具验证所有 CodeQL 类型和谓词**：
    - 例如：查询 `PointerType`、`getBaseType`、`Type`、`IntegralType` 等
    - 例如：查询如何正确检查 C/C++ 类型（如 `unsigned char`）
    - **禁止直接写 `unsigned char` 等 C 类型名**，必须使用 CodeQL API（如 `getUnspecifiedType().getName() = "unsigned char"`）
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
  - 不要引入与报告无关的泛化模式（如任意 `system` 调用）。

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
