/**
 * @name SQL Injection Detection
 * @description Detects potential SQL injection vulnerabilities by tracking data flow 
 * from user input sources to SQL execution sinks
 * @kind path-problem
 * @problem.severity warning
 * @id java/sql-injection
 */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

/**
 * Source definition: User input methods that could contain malicious SQL
 */
class SqlInjectionSource extends DataFlow::Node {
  SqlInjectionSource() {
    // Servlet user input sources
    exists(MethodAccess ma |
      ma.getMethod().getName() = "getParameter" and
      ma.getMethod().getDeclaringType().hasQualifiedName("javax.servlet.http", "HttpServletRequest") and
      this.asExpr() = ma
    )
    or
    exists(MethodAccess ma |
      ma.getMethod().getName() = "getQueryString" and
      ma.getMethod().getDeclaringType().hasQualifiedName("javax.servlet.http", "HttpServletRequest") and
      this.asExpr() = ma
    )
    or
    exists(MethodAccess ma |
      ma.getMethod().getName() = "getHeader" and
      ma.getMethod().getDeclaringType().hasQualifiedName("javax.servlet.http", "HttpServletRequest") and
      this.asExpr() = ma
    )
    // Add other user input sources as needed
  }
}

/**
 * Sink definition: SQL execution methods that could be vulnerable to injection
 */
class SqlInjectionSink extends DataFlow::Node {
  SqlInjectionSink() {
    // Statement execution methods
    exists(MethodAccess ma |
      (ma.getMethod().getName() = "execute" or
       ma.getMethod().getName() = "executeQuery" or
       ma.getMethod().getName() = "executeUpdate") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.sql", "Statement") and
      this.asExpr() = ma
    )
    or
    // PreparedStatement execution methods
    exists(MethodAccess ma |
      (ma.getMethod().getName() = "execute" or
       ma.getMethod().getName() = "executeQuery" or
       ma.getMethod().getName() = "executeUpdate") and
      ma.getMethod().getDeclaringType().hasQualifiedName("java.sql", "PreparedStatement") and
      this.asExpr() = ma
    )
  }
}

/**
 * Data flow configuration for SQL injection detection
 */
class SqlInjectionConfiguration extends DataFlow::Configuration {
  SqlInjectionConfiguration() { this = "SqlInjectionConfiguration" }
  
  override predicate isSource(DataFlow::Node source) {
    source instanceof SqlInjectionSource
  }
  
  override predicate isSink(DataFlow::Node sink) {
    sink instanceof SqlInjectionSink
  }
  
  // Additional sanitization and taint tracking rules can be added here
  override predicate isSanitizer(DataFlow::Node node) {
    // Add sanitization methods if needed
    none()
  }
}

from SqlInjectionConfiguration config, DataFlow::PathNode source, DataFlow::PathNode sink
where config.hasFlowPath(source, sink)
select sink, 
  "Potential SQL injection vulnerability: user input flows to SQL execution",
  source, sink,
  "User input from $@ flows to SQL execution at $@",
  source.getNode(), source.getNode().toString(),
  sink.getNode(), sink.getNode().toString()