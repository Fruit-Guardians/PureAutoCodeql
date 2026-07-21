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

注意Sink点无论如何只有一个，不要使用or连接多个sink点

`hasQualifiedName`方法，该方法只存在于`RefType`类中

## CodeQL生成规则 (CRITICAL)

- **导入规范**
  - 必须：`import java`
  - 必须：`import semmle.code.java.dataflow.DataFlow`, `import semmle.code.java.dataflow.TaintTracking`
  - 视需要：`import semmle.code.java.dataflow.FlowSources`
- **配置与模块**
  - 使用 `module VulnConfig implements DataFlow::ConfigSig`
  - 使用 `module Flow = TaintTracking::Global<VulnConfig>;` 并 `import Flow::PathGraph`
- **类型与节点约定**
  - 方法调用使用 `MethodCall`（不要使用 `MethodAccess`/不存在的类型）
  - Sink 通常为参数：`sink.asExpr() = mc.getAnArgument()`，不要将整个调用作为 sink
- **Select 语句**
  - 使用 7 参数格式，例如：
    `select sink.getNode(), src, sink, "message", src, "source", sink, "sink"`
- **空谓词返回**
  - 使用 `none()`，不要使用 `false`
- **禁止事项**
  - ❌ 不使用 `java.lang` 等内部包限定作为约束条件
  - ❌ 不发明不存在类型（如 `DataFlow::CallNode`）

- **sink点定义原则（重要）**：
  - **必须优先定义Sink点分析报告中的sink点**
  - **禁止直接使用底层调用作为限制条件**：
    - ❌ 禁止：`Runtime.exec()`, `java.lang.reflect.Method.invoke()`, `java.lang` 包下的函数等
    - ❌ 禁止：使用 `mc.getMethod().getDeclaringType().hasQualifiedName("java.lang.reflect", "Method")` 作为限制条件
    - ✅ 要求：只限制到 Sink 点报告给出的具体函数调用
  
  - **🔴 致命错误：区分 MethodCall 和 ConstructorCall**：
    - **MethodCall**：仅代表普通方法调用（如 `obj.getName()`）
    - **ConstructorCall**：代表构造函数调用（如 `new File(...)`）
    - ❌ **错误**：使用 `MethodCall` 捕获构造函数
      ```ql
      exists(MethodCall mc |
        mc.getMethod().hasName("File")  // 错误：构造函数不是 MethodCall
      )
      ```
    - ✅ **正确**：使用 `ConstructorCall` 捕获构造函数
      ```ql
      exists(ConstructorCall cc |
        cc.getConstructedType().hasQualifiedName("java.io", "File") and
        sink.asExpr() = cc.getArgument(0)
      )
      ```
  
  - **🔴 关键错误：避免将中间节点定义为 Sink（短路效应）**：
    - **问题**：如果将污点传播路径中的中间步骤（如 `entry.getName()`）定义为 Sink，会导致分析在中间节点就停止，无法追踪到真正的危险点
    - ❌ **错误示例**：
      ```ql
      // 错误：将 getName() 定义为 Sink
      exists(MethodCall mc |
        mc.getMethod().hasName("getName") and
        sink.asExpr() = mc  // 这会导致分析在此停止
      )
      ```
    - ✅ **正确做法**：
      - 只将真正的危险操作定义为 Sink（如 `new File(userInput)`）
      - 中间步骤（如 `getName()`）应该在 `isAdditionalFlowStep` 中处理污点传播
      - 让污点追踪引擎自然地追踪数据流到最终的危险点
  
  - **三种方法精确定位 sink 点**（使用 AND 组合确保精确性）：
    1. **被调用的方法名**：`mc.getEnclosingCallable().hasName("invokeService")` - 用于锁定 sink 点所在的上层方法
    2. **当前方法调用名**：`mc.getMethod().hasName("invoke")` 或构造函数：`cc.getConstructedType().hasQualifiedName("java.io", "File")` - 用于锁定具体的危险方法调用
    3. **Java 文件路径匹配**：`mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java")` - 用于锁定具体文件
  
  - **🔴 关键：Sink 节点的正确定义方式**：
    - **对于方法调用（MethodCall）：必须将方法的参数作为 sink**
      - ✅ **正确**：`sink.asExpr() = mc.getAnArgument()` - 污点数据流向方法参数
      - ❌ **错误**：`sink.asExpr() = mc` - 污点数据流向整个方法调用
      - **原因**：污点数据通常流向危险方法的参数（如 `invoke(method, obj, args)`）
    
    - **对于构造函数调用（ConstructorCall）：将构造函数的参数作为 sink**
      - ✅ **正确**：`sink.asExpr() = cc.getArgument(0)` - 污点数据流向构造函数参数
      - ❌ **错误**：`sink.asExpr() = cc` - 污点数据流向整个构造函数调用
    
    - **适用场景**：
      - 反射调用（MethodCall）：`Method.invoke()`, `Constructor.newInstance()`
      - 命令执行（MethodCall）：`Runtime.exec()`, `ProcessBuilder.command()`
      - SQL注入（MethodCall）：`Statement.execute()`, `PreparedStatement.executeQuery()`
      - 文件操作（ConstructorCall）：`new FileInputStream()`, `new FileOutputStream()`, `new File()`
      - 等所有接受用户输入作为参数的危险方法或构造函数
  
  - **示例对比**：
    ```ql
    // ❌ 错误示例1 - 使用 MethodCall 捕获构造函数
    exists(MethodCall mc |
      mc.getMethod().hasName("File") and  // 错误：构造函数不是 MethodCall
      sink.asExpr() = mc.getAnArgument()
    )
    
    // ✅ 正确示例1 - 使用 ConstructorCall 捕获构造函数
    exists(ConstructorCall cc |
      cc.getEnclosingCallable().hasName("uploadFile") and
      cc.getConstructedType().hasQualifiedName("java.io", "File") and
      sink.asExpr() = cc.getArgument(1)  // 正确：构造函数参数作为sink
    )
    
    // ❌ 错误示例2 - 将中间节点定义为 Sink
    exists(MethodCall mc |
      mc.getMethod().hasName("getName") and
      sink.asExpr() = mc  // 错误：导致分析在中间步骤停止
    )
    
    // ✅ 正确示例2 - 只定义真正的危险点为 Sink
    exists(MethodCall mc |
      mc.getEnclosingCallable().hasName("invokeService") and
      mc.getMethod().hasName("invoke") and
      sink.asExpr() = mc.getAnArgument()  // 正确：方法参数作为sink
    )
    ```
  
  - **注意**：
    - 不允许使用内部包（如 java.lang）作为限制条件
    - 如果代码段有多次调用同名方法，可以添加包名或其他条件进一步限制
    - Sink 点报告中的 sink 点是必需的，必须精确匹配报告中的调用位置
    - **对于 MethodCall，使用 `mc.getAnArgument()` 作为 sink 节点**
    - **对于 ConstructorCall，使用 `cc.getArgument(N)` 作为 sink 节点**
    - **绝不将中间传播步骤（如 getter 方法）定义为 Sink**
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
    // ✅ 已验证的 Source 点参考查询（如果有）：
    // [[SOURCE_VERIFICATION_QUERY]]
    // 
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
    // ✅ 已验证的 Sink 点参考查询（如果有）：
    // [[SINK_VERIFICATION_QUERY]]
    // 
    // TODO: 定义Java sinks
    // 使用三种方法精确定位sink点（根据报告中的具体调用位置）：
    // 1. mc/cc.getEnclosingCallable().hasName("xxx") - 被调用的方法名
    // 2. mc.getMethod().hasName("xxx") 或 cc.getConstructedType().hasQualifiedName("pkg", "Class") - 当前调用
    // 3. mc/cc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%XxxController.java") - 文件路径
    // 🔴 重要：区分 MethodCall 和 ConstructorCall
    // 🔴 关键：不要将中间传播步骤（如 getName()）定义为 Sink
    
    // 示例1：普通方法调用 (MethodCall)
    exists(MethodCall mc |
      mc.getEnclosingCallable().hasName("invokeService") and
      mc.getMethod().hasName("invoke") and
      mc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%ProxygenController.java") and
      sink.asExpr() = mc.getAnArgument()  // 方法参数作为sink
    )
    or
    // 示例2：构造函数调用 (ConstructorCall)
    exists(ConstructorCall cc |
      cc.getEnclosingCallable().hasName("uploadFile") and
      cc.getConstructedType().hasQualifiedName("java.io", "File") and
      cc.getEnclosingCallable().getDeclaringType().getFile().getAbsolutePath().matches("%FileController.java") and
      sink.asExpr() = cc.getArgument(1)  // 构造函数参数作为sink
    )
    or
    // 示例3：另一个构造函数 (ConstructorCall)
    exists(ConstructorCall cc |
      cc.getEnclosingCallable().hasName("writeFile") and
      cc.getConstructedType().hasQualifiedName("java.io", "FileOutputStream") and
      sink.asExpr() = cc.getArgument(0)  // 构造函数参数作为sink
    )
  }

  predicate isAdditionalFlowStep(DataFlow::Node node1, DataFlow::Node node2) {
    // 示例1：方法调用的污点传播（从参数到返回值）
    exists(MethodCall mc , Expr arg|
      mc.getMethod().getDeclaringType().hasQualifiedName("com.example.xxx", "xxx") and
      (
        mc.getMethod().hasName("xxx") or
        mc.getMethod().hasName("xxx")
      ) and
      arg = mc.getAnArgument() and
      node1.asExpr() = arg and
      node2.asExpr() = mc
    )
    or
    // 示例2：构造函数的污点传播（从参数到对象）
    exists(ConstructorCall c |
      c.getCallee().hasQualifiedName("org.apache.commons.compress.archivers.tar","TarArchiveInputStream","TarArchiveInputStream") and
      node1.asExpr() = c.getArgument(0) and
      node2.asExpr() = c
    )
    or
    // 示例3：Getter 方法的污点传播（从对象到返回值）
    // 注意：这里是传播步骤，不是 Sink 定义
    exists(MethodCall mc |
      mc.getMethod().hasName("getName") and
      mc.getMethod().getDeclaringType().hasQualifiedName("org.apache.commons.compress.archivers.tar", "TarArchiveEntry") and
      node1.asExpr() = mc.getQualifier() and  // 源头是 entry 对象
      node2.asExpr() = mc                      // 目标是 getName() 的返回值
    )
  }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<diagnostic message>",
  src, "source", sink, "sink"