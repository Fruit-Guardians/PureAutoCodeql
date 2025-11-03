/**
 * @kind path-problem
 * @name <NAME>
 * @description <DESCRIPTION>
 * @id <ID>
 * @tags <TAG-LIST>
 * @severity <SEVERITY>
 * @precision <PRECISION>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources

<HELPER-PREDICATES>

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { /* TODO: sources */ }
  predicate isSink(DataFlow::Node sink)   { /* TODO: sinks */ }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { /* optional */ }
  predicate isSanitizer(DataFlow::Node node) { /* optional */ }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<diagnostic message>",
  src, "source", sink, "sink"
