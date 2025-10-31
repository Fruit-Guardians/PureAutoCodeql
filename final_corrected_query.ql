/**
 * @kind path-problem
 * @name Unsafe Reflection in vSphere Client Virtual SAN Health Check Plugin
 * @description Detects unsafe reflection usage where user-controlled input flows to Method.invoke calls, leading to remote code execution
 * @id java/unsafe-reflection-vsan
 * @tags security, external/cwe/cwe-470, external/cwe/cwe-918
 * @severity high
 * @precision high
 */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking
import DataFlow::PathGraph

class VulnConfig extends TaintTracking::Configuration {
  VulnConfig() { this = "VulnConfig" }

  override predicate isSource(DataFlow::Node source) {
    // Source: Spring MVC @PathVariable parameters
    exists(Method m, Parameter p |
      (m.hasName("invokeServiceWithJson") or m.hasName("invokeServiceWithMultipartFormData")) and
      m.getAParameter() = p and
      source.asParameter() = p and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "PathVariable")
    )
    or
    // Source: @RequestBody parameters
    exists(Method m, Parameter p |
      m.hasName("invokeServiceWithJson") and
      m.getParameter(2) = p and
      source.asParameter() = p and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestBody")
    )
    or
    // Source: @RequestParam parameters
    exists(Method m, Parameter p |
      m.hasName("invokeServiceWithMultipartFormData") and
      (m.getParameter(3) = p or m.getParameter(2) = p) and
      source.asParameter() = p and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestParam")
    )
  }

  override predicate isSink(DataFlow::Node sink) {
    // Sink: Method.invoke calls
    exists(MethodAccess ma |
      ma.getMethod().hasName("invoke") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.lang.reflect", "Method") and
      sink.asExpr() = ma.getArgument(0)
    )
    or
    // Sink: Class.forName calls
    exists(MethodAccess ma |
      ma.getMethod().hasName("forName") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.lang", "Class") and
      sink.asExpr() = ma.getArgument(0)
    )
  }
}

from VulnConfig config, DataFlow::PathNode source, DataFlow::PathNode sink
where config.hasFlowPath(source, sink)
select sink, source, sink, 
  "Unsafe reflection: User-controlled input flows to dangerous reflection API",
  source, "source", sink, "sink"