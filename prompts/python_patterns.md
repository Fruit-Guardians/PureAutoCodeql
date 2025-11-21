# Python CodeQL 模式库 (Patterns & Helpers)

> 本文件包含常用的 Source/Sink 定义模式和 Helper 谓词实现（代码积木）。在规划（Plan）阶段确定类型后，请直接从此处复制对应的代码块。

---

## 1. 常用类型/方法速查

| 目的 | 推荐写法 | 说明 |
| --- | --- | --- |
| 函数参数 | `source instanceof DataFlow::ParameterNode` | 类型检查 |
| 参数所属函数（作用域） | `source.getScope() = f` | **必须使用**，避免类型不兼容 |
| 调用节点 | `DataFlow::CallCfgNode call` | 方法/函数调用 |
| 全局函数调用 | `calleeIsGlobalName(call, "open")` | 使用helper谓词 |
| 属性方法调用 | `call.getFunction() instanceof DataFlow::AttrRead` | 先类型检查再访问 |
| 调用参数 | `call.getArg(0)` 或 `call.getKwarg("param")` | 位置参数/关键字参数 |
| 文件限定 | `n.getLocation().getFile().getBaseName() = "xxx.py"` | 文件路径检查 |
| 函数名限定 | `n.getEnclosingCallable().getScope().getName() = "function_name"` | 函数名检查 |
| 远程源 | `source instanceof RemoteFlowSource` | 用户输入（request.GET/POST等） |
| 获取AST节点 | `node.asCfgNode().getNode()` | 从DataFlow::Node获取AST |
| 模块变量识别 | `node instanceof DataFlow::ModuleVariableNode` | 识别导入的模块变量（如 `pd`, `django`） |

---

## 2. Source 定义模式

### 模式A：函数参数（标准）
适用于：明确知道漏洞位于特定函数的参数中。
```ql
predicate isSource(DataFlow::Node source) {
  exists(Function f |
    isAffectedFunction(f) and
    source.getScope() = f and
    source instanceof DataFlow::ParameterNode
  )
}
```

### 模式B：远程源（标准）
适用于：Web 应用入口，自动识别 request.GET/POST 等。
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource
}
```

### 模式C：远程源 + 作用域限定
适用于：仅关注特定文件/函数内的远程输入。
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource and
  inTargetFile(source) and
  inTargetFunction(source)
}
```

### 模式D：混合宽泛源 (High Coverage)
适用于：当不确定Source是参数还是直接读取变量时，使用此模式可大幅提高覆盖率。
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource
  or
  source.asCfgNode().isLoad()  // 捕获变量读取
  or
  source instanceof DataFlow::ParameterNode
}
```

### 模式E：字典特定Key读取
适用于：从字典/kwargs中获取特定Key（如 `kwargs.get('path')`）。
```ql
predicate isSource(DataFlow::Node source) {
  exists(DataFlow::CallCfgNode call, DataFlow::AttrRead attr |
    call.getFunction() = attr and
    attr.getAttributeName() = "get" and
    call.getArg(0).asExpr().(StringLiteral).getText() = "path" and // 匹配Key
    (
      attr.getObject().asExpr().(Name).getId() = "kwargs" or  // 匹配字典变量名
      attr.getObject().asExpr().(Name).getId() = "formatted"
    ) and
    source = call and
    inRelevantFiles(source)
  )
}
```

### 模式F：库内部特定函数参数 (Intra-library)
适用于：分析库自身代码，源是库公开函数的参数，且需要严格限定文件和函数名。
```ql
predicate isSource(DataFlow::Node source) {
  exists (DataFlow::ParameterNode p, string qn |
    p = source and
    inTargetFile(p) and
    // 参数名检查
    p.getParameter().getName() = "expression" and
    // 函数限定名正则匹配 (如 PIL.ImageMath.eval)
    source.getEnclosingCallable().getQualifiedName() = qn and
    qn.regexpMatch("(^|.*\\.)eval$")
  )
}
```

---

## 3. Sink 定义模式

### 模式A：属性方法调用
适用于：`obj.method(sink)` 形式。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call, Function f |
    isAffectedFunction(f) and
    call.getScope() = f and
    call.getFunction() instanceof DataFlow::AttrRead and
    call.getFunction().(DataFlow::AttrRead).getAttributeName() = "execute_query_" and
    sink = call.getArg(0)
  )
}
```

### 模式B：全局函数调用 + 文件模式检查
适用于：`open(sink, "rb")` 等带常数参数检查的全局调用。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    call.getArg(1).asExpr().(StringLiteral).getText() = "rb" and // 检查模式，区分读/写
    sink = call.getArg(0) and
    inTargetFile(sink)
  )
}
```

### 模式C：通过 Helper 谓词
适用于：逻辑较复杂，封装在 helper 中。
```ql
predicate isSink(DataFlow::Node sink) {
  isRedirectTargetArg(sink)
}
```

### 模式D：混合Sink（全局 + 属性 + 特定模块）
适用于：同时检测 `eval()` 和 `pandas.eval()`, `PIL.ImageMath.eval()` 等。
```ql
predicate isSink(DataFlow::Node sink) {
  // A) 全局eval/exec
  exists(DataFlow::CallCfgNode call |
    (calleeIsGlobalName(call, "eval") or calleeIsGlobalName(call, "exec")) and
    sink = call.getArg(0)
  )
  or
  // B) 属性调用 (pandas.eval, df.eval, etc.)
  exists(DataFlow::CallCfgNode call |
    (calleeIsAttr(call, "eval") or calleeIsAttr(call, "exec")) and
    attrReceiverIsLikelyEvalCarrier(call) and // 检查接收者是否为 pandas, pd, builtins 等
    sink = call.getArg(0)
  )
}
```

### 模式E：接收者作为Sink (Receiver Sink)
适用于：检测 `.write()` 方法的调用者（通常需要配合 isAdditionalFlowStep 追踪 open 返回值）。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    call.getFunction() instanceof DataFlow::AttrRead and
    call.getFunction().(DataFlow::AttrRead).getAttributeName() = "write" and
    sink = call.getFunction().(DataFlow::AttrRead).getObject() and // receiver is sink
    inTargetFile(sink)
  )
}
```

---

## 4. Additional Flow Step 模式

### 模式A：资源创建 -> 资源使用 (Open -> Write)
关键：将 `open(path)` 的路径参数传播到 `open` 的返回值。
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // open(filename) 的 filename 参数 → open() 返回值
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    src = call.getArg(0) and
    dst = call and
    inTargetFile(src)
  )
}
```

### 模式B：自定义调用链传播
适用于：`path -> helper(path) -> sink`。
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // path -> image_to_data_url(path)
  exists(DataFlow::CallCfgNode call |
    call.getFunction().(DataFlow::AttrRead).getAttributeName() = "image_to_data_url" and
    src = call.getArg(0) and
    dst = call
  )
}
```

---

## 5. 常见 Helper 谓词模式

### 5.1 全局函数调用检查
```ql
predicate calleeIsGlobalName(DataFlow::CallCfgNode call, string nm) {
  call.getFunction().asCfgNode().getNode() instanceof Name and
  call.getFunction().asCfgNode().getNode().(Name).getId() = nm
}
```

### 5.2 属性方法调用检查
```ql
predicate calleeIsAttr(DataFlow::CallCfgNode call, string attr) {
  call.getFunction() instanceof DataFlow::AttrRead and
  call.getFunction().(DataFlow::AttrRead).getAttributeName() = attr
}
```

### 5.3 受影响函数检查
```ql
predicate isAffectedFunction(Function f) {
  f.getName() in ["function1", "function2", "function3"]
}
```

### 5.4 文件限定（严格）
```ql
predicate inTargetFile(DataFlow::Node n) {
  // 简单文件名匹配
  n.getLocation().getFile().getBaseName() = "target.py"
  // 或者正则路径匹配
  // n.getLocation().getFile().getRelativePath().regexpMatch(".*/PIL/ImageMath\\.py$")
}
```

### 5.5 函数限定（严格）
```ql
predicate inTargetFunction(DataFlow::Node n) {
  n.getEnclosingCallable().getScope().getName() = "target_function"
}
```

### 5.6 接收者模块检查
```ql
predicate attrReceiverIsLikelyEvalCarrier(DataFlow::CallCfgNode call) {
  exists(DataFlow::Node recv |
    recv = call.getFunction().(DataFlow::AttrRead).getObject() and
    (
      (recv instanceof DataFlow::ModuleVariableNode and
       recv.(DataFlow::ModuleVariableNode).getVariable().getId() in ["pandas","pd","builtins","PIL"])
      or
      (recv.asCfgNode().getNode() instanceof Name and
       recv.asCfgNode().getNode().(Name).getId() in ["ImageMath","DataFrame","pandas","pd","builtins","PIL"])
    )
  )
}
```

### 5.7 字典get()调用检查
```ql
predicate isDictGetCall(DataFlow::CallCfgNode call, string keyName) {
  exists(DataFlow::AttrRead attr |
    call.getFunction() = attr and
    attr.getAttributeName() = "get" and
    call.getArg(0).asExpr().(StringLiteral).getText() = keyName
  )
}
```

