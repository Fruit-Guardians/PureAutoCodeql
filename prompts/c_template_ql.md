/**
 * @name [漏洞名称 / CVE]
 * @description [用 2~3 句话说明漏洞成因、补丁逻辑与本查询给出的路径语义]
 * @kind path-problem
 * @id cpp/[自定义的唯一 ID]
 * @problem.severity [error|warning|recommendation]
 * @security-severity [0.0-10.0]
 * @precision [high|medium|low]
 * @tags security
 *       vulnerability
 *       external/cwe/cwe-[CWE-ID]
 *       external/cve/cve-[CVE-ID]
 */

import cpp
import semmle.code.cpp.dataflow.new.TaintTracking  // 若需要精细控制，可改为 DataFlow
// import semmle.code.cpp.dataflow.new.DataFlow
// import semmle.code.cpp.controlflow.Guards

// --- 目标范围（可选） --------------------------------------------------------

/** 仅匹配受影响的函数或文件，避免全库误报。 */
predicate inTarget(Function f) {
  f.hasGlobalName("[受影响函数名]") or
  f.getFile().getRelativePath().regexpMatch(".*[受影响文件]$")
}

// --- Source / Sink 定义 -----------------------------------------------------

/** Source：外部可控或缺乏上界约束的表达式。 */
predicate isSourceExpr(Expr expr) {
  exists(FunctionCall fc |
    fc.getTarget().hasGlobalName("[source-API]") and
    expr = fc
  )
  or
  exists(VariableAccess va |
    va.getTarget() instanceof Parameter and
    inTarget(va.getTarget().(Parameter).getFunction()) and
    va.getTarget().getName() = "[参数名]"
  )
}

/** Sink：危险调用的关键参数（例如 memcpy 第三个参数、vsprintf 目标缓冲等）。 */
predicate isSinkExpr(Expr expr) {
  exists(FunctionCall call |
    call.getTarget().hasGlobalName("[sink-API]") and
    expr = call.getArgument([索引])
  )
}

// --- 数据流配置 -------------------------------------------------------------

module VulnConfig implements TaintTracking::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(Expr e | isSourceExpr(e) and source.asExpr() = e)
  }

  predicate isSink(DataFlow::Node sink) {
    exists(Expr e | isSinkExpr(e) and sink.asExpr() = e)
  }

  /** 可选：补充 memcpy 第三个参数等特殊流转，或字段别名。 */
  // predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
  //   exists(FieldAccess fa1, FieldAccess fa2 |
  //     n1.asExpr() = fa1 and
  //     n2.asExpr() = fa2 and
  //     fa1.getTarget() = fa2.getTarget()
  //   )
  // }
}

module VulnFlow = TaintTracking::Global<VulnConfig>;
import VulnFlow::PathGraph

// --- 查询输出 ---------------------------------------------------------------

from VulnFlow::PathNode src, VulnFlow::PathNode snk, Expr sinkExpr
where
  VulnFlow::flowPath(src, snk) and
  sinkExpr = snk.getNode().asExpr()
select sinkExpr,
  src, snk,
  "[一句话提示：$@（Source 描述） → 危险使用。建议的修复措施...]",
  src.getNode(), "[Source 标签]",
  snk.getNode(), "[Sink 标签]"