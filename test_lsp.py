from utils.codeql import create_temporary_qlpack
from services.lsp_service import CodeQLLSPService
import time

wrong_ql = '''/**
 * @kind path-problem
 * @name Python Security Vulnerability Detection
 * @description Detects potential security vulnerabilities where user input flows to dangerous sinks
 * @id python/security-vulnerability-detection
 * @tags security, taint, vulnerability
 * @problem.severity warning
 * @precision medium
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

/** ========== Common Sink Definitions ========== */
predicate isEvalSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsGlobalName(call, "eval") or
      calleeIsGlobalName(call, "exec") or
      calleeIsAttr(call, "eval") or
      calleeIsAttr(call, "exec")
    ) and
    sink = call.getArg(0)
  )
}

predicate isFileOperationSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    sink = call.getArg(0)
  )
  or
  exists(DataFlow::CallCfgNode call |
    calleeIsAttr(call, "write") and
    sink = call.getFunction().(DataFlow::AttrRead).getObject()
  )
}

predicate isSQLSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsAttr(call, "execute") or
      calleeIsAttr(call, "executemany") or
      calleeIsAttr(call, "execute_query_")
    ) and
    sink = call.getArg(0)
  )
}

predicate isRedirectSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsGlobalName(call, "redirect") or
      calleeIsAttr(call, "redirect")
    ) and
    sink = call.getArg(0)
  )
}

/** ========== Vulnerability Configuration ========== */
module SecurityConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) {
    src instanceof RemoteFlowSource
  }

  predicate isSink(DataFlow::Node sink) {
    isEvalSink(sink) or
    isFileOperationSink(sink) or
    isSQLSink(sink) or
    isRedirectSink(sink)
  }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    none()
  }

  predicate isSanitizer(DataFlow::Node node) {
    none()
  }
}

module Flow = TaintTracking::Global<SecurityConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "Potential security vulnerability: User input flows to dangerous sink",
  src, "source", sink, "sink"'''
try:
    query_file = create_temporary_qlpack("", language="python")
    pack_root = query_file.parent
    lsp_service = CodeQLLSPService(pack_root, query_file)
    lsp_service.start()
    res = lsp_service.check_syntax(wrong_ql)
    print(res)
    time.sleep(300)
except Exception as e:
    print(f"LSP服务器启动失败: {e}")
finally:
    print("LSP服务器停止")
    lsp_service.stop()
