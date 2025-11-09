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
 * 例如 memcpy 第三参、vsprintf 目标缓冲等。
 */
class VulnerableCall extends FunctionCall {
  VulnerableCall() {
    this.getTarget().hasGlobalName("[危险 API 名]") and
    inTarget(this.getEnclosingFunction())
  }

  /** 数据源参数（可选） */
  Expr getDataArg() { result = this.getArgument([索引]) }

  /** 长度或危险参数 */
  Expr getDangerArg() { result = this.getArgument([索引]) }
}

// --- Source 定义 -------------------------------------------------------------

/**
 * 外部可控或缺乏边界约束的表达式。
 * 参考补丁中的“产生未校验长度/数据”的函数或字段。
 */
predicate isSourceExpr(Expr expr) {
  // 示例：函数调用返回值
  exists(FunctionCall fc |
    fc.getTarget().hasGlobalName("[source-API]") and
    inTarget(fc.getEnclosingFunction()) and
    expr = fc
  )
  or
  // 示例：关键局部变量或参数
  exists(VariableAccess va |
    va.getTarget() instanceof Parameter and
    va.getTarget().getName() = "[变量名]" and
    inTarget(va.getTarget().(Parameter).getFunction()) and
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
      sink.asExpr() = call.getDangerArg()
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
