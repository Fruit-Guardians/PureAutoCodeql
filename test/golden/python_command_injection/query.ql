/**
 * @name Golden Python command injection
 * @description Environment-controlled command reaches os.system.
 * @kind path-problem
 * @problem.severity warning
 * @security-severity 9.8
 * @precision high
 * @id pure-auto-codeql/golden-python-command-injection
 * @tags security external/cwe/cwe-078
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.ApiGraphs

module Config implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    source = API::moduleImport("os").getMember("getenv").getACall()
  }

  predicate isSink(DataFlow::Node sink) {
    exists(DataFlow::CallCfgNode call |
      call = API::moduleImport("os").getMember("system").getACall() and
      sink = call.getArg(0)
    )
  }
}

module Flow = TaintTracking::Global<Config>;
import Flow::PathGraph

from Flow::PathNode source, Flow::PathNode sink
where Flow::flowPath(source, sink)
select sink.getNode(), source, sink, "Environment-controlled command reaches this shell execution."
