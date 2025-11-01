#### 编写约束

/**

* @kind path-problem
* @name `<NAME>`
* @description `<DESCRIPTION>`
* @id `<ID>`
* @tags `<TAG-LIST>`
* @severity `<SEVERITY>`
* @precision `<PRECISION>`
  */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking
import semmle.code.java.dataflow.FlowSources

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) {
    // TODO: 定义Java数据源，例如:
    // exists(RemoteFlowSource rfs | rfs.getSource() = src)
  }
  predicate isSink(DataFlow::Node sink) {
    // TODO: 定义Java sinks
    这里的例子：predicate isSink(DataFlow::Node sink) {
    exists(MethodCall mc |
      mc.getMethod().getDeclaringType().hasQualifiedName("java.sql", "Statement") and
      (
        mc.getMethod().hasName("execute") or
        mc.getMethod().hasName("executeQuery") or
        mc.getMethod().hasName("executeUpdate")
      ) and
      sink.asExpr() = mc
    )
  }
  }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // 可选: 定义额外的数据流步骤
  }
  predicate isSanitizer(DataFlow::Node node) {
    // 可选: 定义净化节点
  }
}

module Flow = TaintTracking::Global`<VulnConfig>`;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "`<diagnostic message>`",
  src, "source", sink, "sink"
