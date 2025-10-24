/**
 * @name Your Taint Tracking Query
 * @kind path-problem
 * @id java/h5-vsan-query
 * @problem.severity warning
 */

import semmle.code.java.dataflow.FlowSources
private import semmle.code.java.dataflow.TaintTracking
import BaseInjectionFlow::PathGraph

module BaseFlowConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) { 
    exists(Method md |
      source.asParameter() = md.getParameter(0) and md.getName() = "invokeServiceWithJson" 
    ) 
  }

  predicate isSink(DataFlow::Node sink) { 
    exists(MethodCall mc |
      sink.asExpr() = mc.getArgument(0)
    ) 
  }

  predicate isAdditionalFlowStep(DataFlow::Node node1, DataFlow::Node node2) {
    exists(MethodCall ma |
      ma.getMethod().hasQualifiedName("org.springframework.beans.factory", "BeanFactory", "getBean") and
      node1.asExpr() = ma.getArgument(0) and
      node2.asExpr() = ma
    )
  }
  
  // This predicate is generally not needed unless you are debugging incremental queries
  // predicate observeDiffInformedIncrementalMode() { any() }
}

module BaseInjectionFlow = TaintTracking::Global<BaseFlowConfig>;

from BaseInjectionFlow::PathNode source, BaseInjectionFlow::PathNode sink
where BaseInjectionFlow::flowPath(source, sink)
select sink.getNode(), source, sink, "h5", source.getNode(), "this user input"