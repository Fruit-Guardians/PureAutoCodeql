CodeQL query successfully generated and executed after 1 round(s):

```ql
/**
 * @kind path-problem
 * @name Path Traversal in pyLoad CNL Blueprint
 * @description Detects path traversal vulnerabilities in pyLoad's CNL Blueprint that allow arbitrary file writes
 * @id python/pyload-path-traversal
 * @tags security, taint, external/cwe/cwe-022
 * @problem.severity high
 * @precision high
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources

/** ========== Helper Predicates ========== */
predicate calleeIsGlobalName(DataFlow::CallCfgNode call, string nm) {
  call.getFunction().asCfgNode().getNode() instanceof Name and
  call.getFunction().asCfgNode().getNode().(Name).getId() = nm
}

predicate calleeIsAttr(DataFlow::CallCfgNode call, string attr) {
  call.getFunction() instanceof DataFlow::AttrRead and
  call.getFunction().(DataFlow::AttrRead).getAttributeName() = attr
}

predicate inTargetFile(DataFlow::Node n, string filename) {
  n.getLocation().getFile().getBaseName() = filename
}

predicate inTargetFunction(DataFlow::Node n, string funcName) {
  n.getEnclosingCallable().getScope().getName() = funcName
}

/** ========== Data Flow Configuration ========== */
module PathTraversalConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) {
    // Source: HTTP request parameters in CNL Blueprint functions
    src instanceof RemoteFlowSource and
    inTargetFile(src, "cnl_blueprint.py") and
    (
      inTargetFunction(src, "addcrypted") or
      inTargetFunction(src, "add") or
      inTargetFunction(src, "addcrypted2") or
      inTargetFunction(src, "flashgot")
    )
  }

  predicate isSink(DataFlow::Node sink) {
    // Sink: file write operations (open() function calls)
    exists(DataFlow::CallCfgNode call |
      calleeIsGlobalName(call, "open") and
      sink = call.getArg(0) and
      inTargetFile(sink, "cnl_blueprint.py")
    )
  }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // Propagate taint through string operations used in path construction
    exists(DataFlow::CallCfgNode call |
      calleeIsAttr(call, "replace") and
      src = call.getFunction().(DataFlow::AttrRead).getObject() and
      dst = call
    )
    or
    exists(DataFlow::CallCfgNode call |
      calleeIsGlobalName(call, "os.path.join") and
      src = call.getArg(1) and  // The filename argument
      dst = call
    )
    or
    exists(DataFlow::CallCfgNode call |
      calleeIsGlobalName(call, "os.path.normpath") and
      src = call.getArg(0) and
      dst = call
    )
  }

  predicate isSanitizer(DataFlow::Node node) {
    // No effective sanitizers in the vulnerable code path
    none()
  }
}

module Flow = TaintTracking::Global<PathTraversalConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "Path traversal vulnerability: user-controlled input flows to file write operation without proper path validation",
  src, "source", sink, "sink"
```

SARIF output saved to: output\result_20251106_204604.sarif