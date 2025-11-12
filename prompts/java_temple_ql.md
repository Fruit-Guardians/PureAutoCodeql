#### Java特殊注意事项
🛡️ 防错检查清单
类型存在性检查：确认使用的DataFlow子类型在Java库中存在
AST节点转换：使用asExpr()、asParameter()正确转换节点类型
方法调用处理：直接使用MethodCall而不是分离Method和CallNode
导入语句：确保正确导入semmle.code.java.dataflow.DataFlow
💡 记忆要点
Java CodeQL中没有DataFlow::CallNode类型
方法调用统一使用MethodCall AST类
DataFlow节点主要用于isSource、isSink、isAdditionalFlowStep参数
使用mc.getMethod()访问方法调用对应的方法


#### 编写约束

/**

* @kind path-problem
* @name `<NAME>`
* @description `<DESCRIPTION>`
* @id `<ID>`
* @tags `<TAG-LIST>`
* @severity `<SEVERITY>`
* @precision `<PRECISION>`
  */

import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking
import semmle.code.java.dataflow.FlowSources

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) {
    // TODO: 定义Java数据源，例如:
    // exists(RemoteFlowSource rfs | rfs.getSource() = src)exists(Method m, Parameter p |
      m.getDeclaringType().hasQualifiedName("com.example.xxx", "xxx") and
      (
        m.hasName("xxx") or
        m.hasName("xxx")
      ) and
      p = m.getAParameter() and
      src.asParameter() = p
    )
    or
    // Also consider Spring request body and request parameters as sources
    exists(Method m, Parameter p |
      m.getDeclaringType().hasQualifiedName("com.example.xxx", "xxx") and
      p.getAnAnnotation().getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestBody") and
      src.asParameter() = p
    )
    or
  }
  predicate isSink(DataFlow::Node sink) {
    // TODO: 定义Java sinks
    这里的例子：predicate isSink(DataFlow::Node sink) {
    exists(MethodCall mc |
      mc.getMethod().getDeclaringType().hasQualifiedName("java.sql", "Statement") and
      (
        mc.getMethod().hasName("execute") or
        mc.getMethod().hasName("executeQuery") or
        mc.getMethod().hasName("executeUpdate")
      ) and
      sink.asExpr() = mc
    )
  }
  }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // Handle data flow through ProxygenSerializer deserialization
    exists(MethodCall mc |
      mc.getMethod().getDeclaringType().hasQualifiedName("com.example.xxx", "xxx") and
      (
        mc.getMethod().hasName("xxx") or
        mc.getMethod().hasName("xxx")
      ) and
      dst.asExpr() = mc
    )
  }
}

module Flow = TaintTracking::Global`<VulnConfig>`;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "`<diagnostic message>`",
  src, "source", sink, "sink"
