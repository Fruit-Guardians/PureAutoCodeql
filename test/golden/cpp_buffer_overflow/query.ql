/**
 * @name Golden C buffer overflow
 * @description Environment-controlled data indexes a fixed-size buffer.
 * @kind path-problem
 * @problem.severity warning
 * @security-severity 9.8
 * @precision high
 * @id pure-auto-codeql/golden-cpp-buffer-overflow
 * @tags security external/cwe/cwe-120
 */

import cpp
import semmle.code.cpp.dataflow.new.DataFlow
import semmle.code.cpp.dataflow.new.TaintTracking

module Config implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    source.asExpr().(FunctionCall).getTarget().hasGlobalName("atoi")
  }

  predicate isSink(DataFlow::Node sink) {
    exists(ArrayExpr access |
      sink.asExpr() = access.getArrayOffset()
    )
  }
}

module Flow = TaintTracking::Global<Config>;
import Flow::PathGraph

from Flow::PathNode source, Flow::PathNode sink
where Flow::flowPath(source, sink)
select sink.getNode(), source, sink, "Environment-controlled index may access outside the buffer."
