/**
 * @name Golden Java path traversal
 * @description Environment-controlled path reaches a file constructor.
 * @kind path-problem
 * @problem.severity warning
 * @security-severity 7.5
 * @precision high
 * @id pure-auto-codeql/golden-java-path-traversal
 * @tags security external/cwe/cwe-022
 */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

class EnvironmentSource extends DataFlow::ExprNode {
  EnvironmentSource() {
    exists(Method method |
      method = this.asExpr().(MethodCall).getMethod() and
      method.hasName("getenv") and
      method.getDeclaringType() instanceof TypeSystem
    )
  }
}

module Config implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) { source instanceof EnvironmentSource }

  predicate isSink(DataFlow::Node sink) {
    exists(ConstructorCall call |
      call.getConstructedType().hasQualifiedName("java.io", "File") and
      sink.asExpr() = call.getArgument(0)
    )
  }
}

module Flow = TaintTracking::Global<Config>;
import Flow::PathGraph

from Flow::PathNode source, Flow::PathNode sink
where Flow::flowPath(source, sink)
select sink.getNode(), source, sink, "Environment-controlled path reaches this file operation."
