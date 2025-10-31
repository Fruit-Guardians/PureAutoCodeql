/**
 * @kind path-problem
 * @name Spring MVC Reflection Injection Vulnerability
 * @description Detects unsafe reflection method invocation in Spring MVC controllers where user-controlled input is used to dynamically invoke methods
 * @id java/spring-reflection-injection
 * @tags security, external/cwe/cwe-470
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
      m.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestMapping") and
      p = m.getAParameter() and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "PathVariable") and
      source.asParameter() = p
    )
    or
    // Source: Spring MVC @RequestParam parameters
    exists(Method m, Parameter p |
      m.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestMapping") and
      p = m.getAParameter() and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestParam") and
      source.asParameter() = p
    )
    or
    // Source: Spring MVC @RequestBody parameters
    exists(Method m, Parameter p |
      m.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestMapping") and
      p = m.getAParameter() and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestBody") and
      source.asParameter() = p
    )
  }

  override predicate isSink(DataFlow::Node sink) {
    // Sink: Method.invoke calls
    exists(MethodCall ma |
      ma.getMethod().hasName("invoke") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.lang.reflect", "Method") and
      sink.asExpr() = ma.getArgument(1)
    )
    or
    // Sink: Class.getMethod calls
    exists(MethodCall ma |
      ma.getMethod().hasName("getMethod") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.lang", "Class") and
      sink.asExpr() = ma.getArgument(0)
    )
  }
}

from VulnConfig config, DataFlow::PathNode source, DataFlow::PathNode sink
where config.hasFlowPath(source, sink)
select sink, source, sink,
  "User-controlled input from Spring MVC parameter flows to unsafe reflection method invocation",
  source, "source", sink, "sink"