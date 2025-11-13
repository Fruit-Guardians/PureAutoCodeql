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

- **sink点定义原则（重要）**：
  - **必须优先定义Sink点分析报告中的sink点**
  - **禁止直接使用底层调用作为限制条件**：
    - ❌ 禁止：`Runtime.exec()`, `java.lang.reflect.Method.invoke()`, `java.lang` 包下的函数等
    - ❌ 禁止：使用 `mc.getMethod().getDeclaringType().hasQualifiedName("java.lang.reflect", "Method")` 作为限制条件
    - ✅ 要求：只限制到 Sink 点报告给出的具体函数调用
  - **三种方法精确定位 sink 点**（使用 AND 组合确保精确性）：
    1. **被调用的方法名**：`mc.getEnclosingCallable().hasName("invokeService")` - 用于锁定 sink 点所在的上层方法
    2. **当前方法调用名**：`mc.getMethod().hasName("invoke")` - 用于锁定具体的危险方法调用
    3. **Java 文件路径匹配**：`mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java")` - 用于锁定具体文件
  
  - **🔴 关键：Sink 节点的正确定义方式**：
    - **对于反射调用、命令执行等漏洞，必须将方法的参数作为 sink，而不是方法调用本身**
    - ✅ **正确**：`sink.asExpr() = mc.getAnArgument()` - 污点数据流向方法参数
    - ❌ **错误**：`sink.asExpr() = mc` - 污点数据流向整个方法调用
    - **原因**：在数据流分析中，污点数据通常流向危险方法的参数（如 `invoke(method, obj, args)`），而不是方法调用表达式本身
    - **适用场景**：
      - 反射调用：`Method.invoke()`, `Constructor.newInstance()`
      - 命令执行：`Runtime.exec()`, `ProcessBuilder.command()`
      - SQL注入：`Statement.execute()`, `PreparedStatement.executeQuery()`
      - 文件操作：`FileInputStream()`, `FileOutputStream()`
      - 等所有接受用户输入作为参数的危险方法
  
  - **示例对比**：
    ```ql
    // ❌ 错误示例 - 无法检测到数据流
    exists(MethodCall mc |
      mc.getEnclosingCallable().hasName("invokeService") and
      mc.getMethod().hasName("invoke") and
      mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java") and
      sink.asExpr() = mc  // 错误：将整个方法调用作为sink
    )
    
    // ✅ 正确示例 - 能够检测到数据流
    exists(MethodCall mc |
      mc.getEnclosingCallable().hasName("invokeService") and
      mc.getMethod().hasName("invoke") and
      mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java") and
      sink.asExpr() = mc.getAnArgument()  // 正确：将方法参数作为sink
    )
    ```
  
  - **注意**：
    - 不允许使用内部包（如 java.lang）作为限制条件
    - 如果代码段有多次调用同名方法，可以添加包名或其他条件进一步限制
    - Sink 点报告中的 sink 点是必需的，必须精确匹配报告中的调用位置
    - **默认情况下，对于所有危险方法调用，都应该使用 `mc.getAnArgument()` 作为 sink 节点**
- 确保查询逻辑严谨，避免误报

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


若目标Source分析为Spring等框架，则在source判断中加入如下内容，用来确保所有用户输入都作为source点
class EndpointMethod extends Callable{
    EndpointMethod(){
        this.getAnAnnotation().getType() instanceof RouteAnnotation
    }
}

class GateWaySource extends RemoteFlowSource{
    GateWaySource(){
        any(EndpointMethod m).getAParameter() = this.asParameter()
    }

    override string getSourceType(){
        result = "gateway route source"
    }
}

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
    // 使用三种方法精确定位sink点（根据报告中的具体调用位置）：
    // 1. mc.getEnclosingCallable().hasName("xxx") - 被调用的方法名
    // 2. mc.getMethod().hasName("xxx") - 当前方法调用名
    // 3. mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%XxxController.java") - 文件路径
    // 🔴 重要：使用 mc.getAnArgument() 将方法参数作为sink，而不是整个方法调用
    
    exists(MethodCall mc |
      // 方法1: 用被调用的方法去锁定这个sink点
      mc.getEnclosingCallable().hasName("invokeService") and
      // 方法2: 用当前方法调用名去锁定
      mc.getMethod().hasName("invoke") and
      // 方法3: 用Java文件路径去锁定
      mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java") and
      // 🔴 关键：将方法参数作为sink，而不是方法调用本身
      sink.asExpr() = mc.getAnArgument()
    )
    // 如果有多个sink点，使用 or 连接
    or
    exists(MethodCall mc |
      mc.getEnclosingCallable().hasName("anotherMethod") and
      mc.getMethod().hasName("dangerousCall") and
      mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%AnotherController.java") and
      // 同样使用 mc.getAnArgument()
      sink.asExpr() = mc.getAnArgument()
    )
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

//predicate isAdditionalFlowStep(DataFlow::Node node1, DataFlow::Node node2) {
    exists(MethodCall ma |
      ma.getMethod().hasQualifiedName("org.springframework.beans.factory", "BeanFactory", "getBean") and
      node1.asExpr() = ma.getArgument(0) and
      node2.asExpr() = ma
    )
  //}

module Flow = TaintTracking::Global`<VulnConfig>`;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "`<diagnostic message>`",
  src, "source", sink, "sink"
