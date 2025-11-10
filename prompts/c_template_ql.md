/**
 * @name [漏洞名称 / CVE]
 * @description [用 2~3 句话描述漏洞成因、补丁逻辑与本查询展示的路径语义]
 * @kind path-problem
 * @id cpp/[唯一 ID]
 * @problem.severity [error|warning|recommendation]
 * @security-severity [0.0-10.0]
 * @precision [high|medium|low]
 * @tags security
 *       vulnerability
 *       external/cwe/cwe-[CWE-ID]
 *       external/cve/cve-[CVE-ID]
 */

import cpp
import semmle.code.cpp.dataflow.new.TaintTracking
import semmle.code.cpp.dataflow.new.DataFlow

// --- 目标范围（可选） --------------------------------------------------------

/**
 * 限制到受影响的函数或文件，减少误报。
 * 可根据补丁信息添加多个 OR 条件。
 */
predicate inTarget(Function f) {
  f.hasGlobalName("[受影响函数名]") or
  f.getFile().getRelativePath().regexpMatch(".*[受影响文件]$")
}

// --- 危险调用建模 ------------------------------------------------------------

/**
 * 用类来封装危险调用，便于访问关键参数。
 * ⚠️ 请使用 diff/案例情报中真实出现的危险 API 与参数索引（例如补丁强调的 memcpy 第三个参数 s）。
 */
class VulnerableCall extends FunctionCall {
  VulnerableCall() {
    this.getTarget().hasGlobalName("[补丁中的危险 API，例如 memcpy]") and
    inTarget(this.getEnclosingFunction())
  }

  /** 如需追踪源参数，可按 diff 指定的参数索引返回；无需求可移除本方法。 */
  Expr getDataArg() { result = this.getArgument([根据 diff 指定的索引]) }

  /** 长度或危险参数，必须对应补丁指出的实参（如 memcpy 的第 2/3 参数）。 */
  Expr getDangerArg() { result = this.getArgument([根据 diff 指定的索引]) }
}

// --- Source 定义 -------------------------------------------------------------

/**
 * 外部可控或缺乏边界约束的表达式。
 * ⚠️ 必须引用 diff/情报中的具体函数或变量（例如 exif_format_get_size、变量 s/len 等），禁止使用“任意输入”之类泛化符号。
 */
predicate isSourceExpr(Expr expr) {
  // 例：补丁强调的函数调用（如 exif_format_get_size 返回值参与 s 计算）
  exists(FunctionCall fc |
    fc.getTarget().hasGlobalName("[补丁中的 source 函数，如 exif_format_get_size]") and
    inTarget(fc.getEnclosingFunction()) and
    expr = fc
  )
  or
  // 例：补丁涉及的关键变量（如 s、len）
  exists(VariableAccess va |
    va.getTarget().getName() = "[补丁里的变量名，如 s]" and
    (
      va.getTarget() instanceof Parameter and
      inTarget(va.getTarget().(Parameter).getFunction())
      or
      va.getTarget() instanceof LocalVariable and
      inTarget(va.getTarget().getFunction())
    ) and
    expr = va
  )
}

// --- 数据流配置 -------------------------------------------------------------

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(Expr e | isSourceExpr(e) and source.asExpr() = e)
  }

  predicate isSink(DataFlow::Node sink) {
    exists(VulnerableCall call |
      // ⚠️ 根据 diff/情报覆盖所有需要监控的参数，例如同时追踪缓冲区与长度参数
      sink.asExpr() = call.getDangerArg() or
      sink.asExpr() = call.getDataArg()
    )
  }

  /** 可选：字段别名 / 指针传递等特殊流转。 */
  predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
    exists(FieldAccess fa1, FieldAccess fa2 |
      n1.asExpr() = fa1 and
      n2.asExpr() = fa2 and
      fa1.getTarget() = fa2.getTarget()
    )
  }
}

module VulnFlow = TaintTracking::Global<VulnConfig>;
import VulnFlow::PathGraph

// --- 查询输出 ---------------------------------------------------------------

from VulnFlow::PathNode src, VulnFlow::PathNode snk, VulnerableCall call
where
  VulnFlow::flowPath(src, snk) and
  snk.getNode().asExpr() = call.getDangerArg()
select call,
  src, snk,
  "[一句话提醒：$@（Source 描述）→ " +
  call.getTarget().getName() +
  " 危险使用，示例修复 …]",
  src.getNode(), "[Source 标签]",
  snk.getNode(), "[Sink 标签]"
