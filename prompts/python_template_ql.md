# Python CodeQL 规划 + 实现模板（Python new dataflow 专用）

> 目标：一次性给出可执行的 Python CodeQL 查询。必须先规划（Plan Summary）再输出代码，严格遵循下述骨架和 API。
> 
> **重要**：本模板基于实际成功案例（CVE-2024-7099, CVE-2024-8412, CVE-2025-46725, CVE-2025-54802等）和KB库规范总结，必须严格遵循。

---

## 1. 输出结构

1. `### Plan Summary`  
   - 列出 Sources / Sinks / Sanitizers / Helpers / Scope，每项需说明来源（Requirement、KB JSON 或参考案例）。  
   - 指明所用类型：`DataFlow::ParameterNode`, `DataFlow::CallCfgNode`, `DataFlow::AttrRead` 等。
   - 说明参考的成功案例（如CVE-2024-7099）及改动点。
2. `### CodeQL Query`  
   - 仅一个 ```ql 代码块，内容必须遵循第 3 节骨架，不得增删结构。  
   - 使用英文诊断信息，`select` **必须包含 7 个参数**：`select sink.getNode(), src, sink, "message", src.getNode(), "source", sink.getNode(), "sink"`.

---

## 2. 常用类型/方法速查（基于成功案例和KB库）

| 目的 | 推荐写法（来自成功案例） | 说明 |
| --- | --- | --- |
| 函数参数 | `source instanceof DataFlow::ParameterNode` | 类型检查 |
| 参数所属函数（作用域） | `source.getScope() = f` | **必须使用**，避免类型不兼容 |
| 调用节点 | `DataFlow::CallCfgNode call` | 方法/函数调用 |
| 全局函数调用 | `calleeIsGlobalName(call, "open")` | 使用helper谓词 |
| 属性方法调用 | `call.getFunction() instanceof DataFlow::AttrRead and call.getFunction().(DataFlow::AttrRead).getAttributeName() = "eval"` | 先类型检查再访问 |
| 调用参数 | `call.getArg(0)` 或 `call.getKwarg("param")` | 位置参数/关键字参数 |
| 文件限定 | `n.getLocation().getFile().getBaseName() = "xxx.py"` | 文件路径检查 |
| 函数名限定 | `n.getEnclosingCallable().getScope().getName() = "function_name"` | 函数名检查 |
| 远程源 | `source instanceof RemoteFlowSource` | 用户输入（request.GET/POST等） |
| 获取AST节点 | `node.asCfgNode().getNode()` | 从DataFlow::Node获取AST |

**关键规范（基于成功案例和KB库）：**
- ✅ **作用域检查**：**必须**使用 `source.getScope() = f` 和 `call.getScope() = f`（参考CVE-2024-7099）
- ✅ **Sink定义**：**通常**使用 `sink = call.getArg(0)` 获取调用的第一个参数（SQL查询字符串、文件路径、URL等）
- ✅ **类型检查**：使用 `source instanceof DataFlow::ParameterNode` 或 `source instanceof RemoteFlowSource`
- ✅ **Source灵活性**：对于SSRF等漏洞，使用 `source.asCfgNode().isLoad() or source instanceof RemoteFlowSource` 捕获变量读取操作（CVE-2025-54381）
- ✅ **调用链追踪**：使用 `DataFlow::localFlow(clientCall, client)` 追踪复杂调用链（如 `httpx.AsyncClient().get()`）
- ✅ **范围限定谨慎**：避免过度限定文件/函数范围，除非明确需要（过度限定会导致漏报）
- ✅ **Select格式**：**必须**包含7个参数：`select sink.getNode(), src, sink, "message", src, "source", sink, "sink"` 或 `select sink.getNode(), src, sink, "message", src.getNode(), "source", sink.getNode(), "sink"`（两种格式都有效，推荐使用第一种）
- ✅ **Import顺序**：先导入标准库，`import Flow::PathGraph` 在module定义**之后**
- ✅ **注释风格**：使用 `/** ... */` 进行多行注释，`//` 进行单行注释
- ✅ **类型转换**：访问调用信息时先进行类型判定：`call.getFunction() instanceof DataFlow::AttrRead and call.getFunction().(DataFlow::AttrRead).getAttributeName()`
- ❌ **禁止**：`pn.getEnclosingCallable() = f` 这种直接比较（会导致类型不兼容错误）
- ❌ **禁止**：`MethodCall`, `ParameterNode`（无命名空间）、`getFile()`（直接调用）、旧 API
- ❌ **禁止**：直接对 AST 类型做 instance 判断，必须先 `asCfgNode().getNode()`
- ❌ **禁止**：在Source/Sink中过度使用文件/函数限定（会导致漏报），仅在必要时使用

**RemoteFlowSource 使用规范：**
- ✅ **正确用法**：`src instanceof RemoteFlowSource`
- ❌ **错误用法**：`exists(RemoteFlowSource rfs | src = rfs.getSource())`
- RemoteFlowSource 本身就是 DataFlow::Node，无需调用额外方法

**SSRF 等漏洞的关键模式（CVE-2025-54381 成功案例）：**
- ✅ **灵活的 Source**：`source.asCfgNode().isLoad() or source instanceof RemoteFlowSource`
  - `isLoad()` 捕获变量读取操作（如从 `request.body()` 解码后的 `url` 变量）
  - 不要过度限定文件和函数范围，除非明确需要
- ✅ **复杂调用链的 Sink 追踪**：
  ```ql
  exists(DataFlow::CallCfgNode call, DataFlow::Node client, DataFlow::CallCfgNode clientCall |
    // 检测 .get() 调用
    call.getFunction() instanceof DataFlow::AttrRead and
    call.getFunction().(DataFlow::AttrRead).getAttributeName() = "get" and
    client = call.getFunction().(DataFlow::AttrRead).getObject() and
    // 检测 AsyncClient() 实例化
    clientCall.getFunction() instanceof DataFlow::AttrRead and
    clientCall.getFunction().(DataFlow::AttrRead).getAttributeName() = "AsyncClient" and
    // 使用本地数据流追踪实例传播
    DataFlow::localFlow(clientCall, client) and
    sink = call.getArg(0)
  )
  ```
- ✅ **模块识别**：支持 `DataFlow::ModuleVariableNode` 和 `Name` 两种方式识别模块（如 `httpx`）
- ❌ **避免**：不要在 Source/Sink 中同时限定 `inFile()` 和 `inFunction()`，会导致漏报

---

## 3. 代码骨架（严格遵循成功案例结构）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称>
 * @description <详细描述>
 * @id python/<project>-<identifier>
 * @tags security, taint, <相关标签>
 * @problem.severity <error|warning|recommendation>
 * @precision <high|medium|low>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources  // 如果需要远程源（如request.GET/POST）

/** ---------- Helper predicates（如需） ---------- */
<HELPER_PREDICATES>

/** ---------- Config ---------- */
module VulnConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源 */
  predicate isSource(DataFlow::Node source) {
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点 */
  predicate isSink(DataFlow::Node sink) {
    <SINK_DEFINITION>
  }

  /** Additional flow steps: 额外的数据流步骤（如open返回值传播到write） */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    <ADDITIONAL_FLOW_STEPS>
  }

  /** Sanitizers: 净化器（如果不需要，写 none()） */
  predicate isSanitizer(DataFlow::Node node) {
    <SANITIZER_DEFINITION>
  }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<诊断信息>",
  src, "source", sink, "sink"
```

> **关键说明：**
> - `<HELPER_PREDICATES>` 区域：放置辅助谓词，如 `calleeIsGlobalName`, `calleeIsAttr`, `isAffectedFunction` 等
> - `<SOURCE_DEFINITION>` 区域：定义source，常见模式见第4节
> - `<SINK_DEFINITION>` 区域：定义sink，常见模式见第4节
> - `<ADDITIONAL_FLOW_STEPS>` 区域：如果需要额外流步骤（如open→write），否则写 `none()`
> - `<SANITIZER_DEFINITION>` 区域：如果不需要净化器，写 `none()`

---

## 4. Source/Sink 定义模式（基于成功案例）

### 4.1 Source 定义模式

#### 模式A：函数参数（参考CVE-2024-7099）
```ql
predicate isSource(DataFlow::Node source) {
  exists(Function f |
    isAffectedFunction(f) and
    source.getScope() = f and
    source instanceof DataFlow::ParameterNode
  )
}
```

#### 模式B：远程源（参考CVE-2024-8412, CVE-2025-46725）
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource
}
```

#### 模式C：远程源 + 作用域限定（参考CVE-2025-54802）
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource and
  inTargetFile(source) and
  inTargetFunction(source)
}
```

#### 模式D：混合源（参考CVE-2025-46725）
```ql
predicate isSource(DataFlow::Node source) {
  source instanceof RemoteFlowSource
  or
  source.asCfgNode().isLoad()
  or
  source instanceof DataFlow::ParameterNode
}
```

#### 模式E：字典get()调用作为源（参考CVE-2024-10940）
```ql
predicate isSource(DataFlow::Node source) {
  exists(DataFlow::CallCfgNode call, DataFlow::AttrRead attr |
    call.getFunction() = attr and
    attr.getAttributeName() = "get" and
    call.getArg(0).asExpr().(StringLiteral).getText() = "path" and
    (
      attr.getObject().asExpr().(Name).getId() = "kwargs" or
      attr.getObject().asExpr().(Name).getId() = "formatted"
    ) and
    source = call and
    inRelevantFiles(source) and
    inRelevantFunctions(source)
  )
}
```
**说明**：此模式用于检测字典 `.get()` 方法调用作为源，常见于模板格式化场景（如Langchain的 `ImagePromptTemplate.format()`）。

### 4.2 Sink 定义模式

#### 模式A：属性方法调用（参考CVE-2024-7099）
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

#### 模式B：全局函数调用（参考CVE-2025-54802）
```ql
predicate isSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    sink = call.getArg(0) and
    inTargetFile(sink) and
    inTargetFunction(sink)
  )
}
```

#### 模式C：通过Helper谓词（参考CVE-2024-8412）
```ql
predicate isRedirectTargetArg(DataFlow::Node n) {
  exists(DataFlow::CallCfgNode call | 
    isRedirectCall(call) and 
    n = call.getArg(0)
  )
}

predicate isSink(DataFlow::Node sink) {
  isRedirectTargetArg(sink)
}
```

#### 模式D：混合sink（参考CVE-2025-46725）
```ql
predicate isSink(DataFlow::Node sink) {
  // A) 全局eval/exec
  exists(DataFlow::CallCfgNode call |
    (calleeIsGlobalName(call, "eval") or calleeIsGlobalName(call, "exec")) and
    sink = call.getArg(0)
  )
  or
  // B) 属性调用
  exists(DataFlow::CallCfgNode call |
    (calleeIsAttr(call, "eval") or calleeIsAttr(call, "exec")) and
    attrReceiverIsLikelyEvalCarrier(call) and
    sink = call.getArg(0)
  )
}
```

#### 模式E：文件读取sink（参考CVE-2024-10940）
```ql
predicate isSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    call.getArg(1).asExpr().(StringLiteral).getText() = "rb" and
    sink = call.getArg(0) and
    inRelevantFiles(sink) and
    inRelevantFunctions(sink)
  )
}
```
**说明**：此模式用于检测 `open()` 文件读取操作，通过检查第二个参数（文件模式）来区分读取和写入操作。

### 4.3 isAdditionalFlowStep 模式

#### 模式A：open()返回值传播（参考CVE-2025-54802）
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // open(filename) 的 filename 参数 → open() 返回值
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    src = call.getArg(0) and
    dst = call and
    inTargetFile(src) and inTargetFunction(src) and
    inTargetFile(dst) and inTargetFunction(dst)
  )
}
```

#### 模式B：通过属性方法调用传播（参考CVE-2024-10940）
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // from path to image_to_data_url(path)
  exists(DataFlow::CallCfgNode call |
    call.getFunction() instanceof DataFlow::AttrRead and
    call.getFunction().(DataFlow::AttrRead).getAttributeName() = "image_to_data_url" and
    src = call.getArg(0) and
    dst = call
  )
  or
  // from image_to_data_url to encode_image
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "encode_image") and
    src = call.getArg(0) and
    dst = call
  )
}
```
**说明**：此模式用于跟踪通过方法调用链传播的污点，例如 `path → image_to_data_url(path) → encode_image(...)`。

### 4.4 isSanitizer 模式（参考CVE-2024-8412）

```ql
predicate isSanitizer(DataFlow::Node node) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsGlobalName(call, "check_redirect_url")
      or calleeIsGlobalName(call, "url_has_allowed_host_and_scheme")
      or calleeIsName(call, "is_safe_url")
    ) and
    node = call.getArg(0)  // 验证后的值被认为是净化的
  )
}
```

---

## 5. 常见Helper谓词模式（参考成功案例）

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

### 5.4 文件限定
```ql
predicate inTargetFile(DataFlow::Node n) {
  n.getLocation().getFile().getBaseName() = "target.py"
}
```

### 5.5 函数限定
```ql
predicate inTargetFunction(DataFlow::Node n) {
  n.getEnclosingCallable().getScope().getName() = "target_function"
}
```

### 5.6 接收者检查（参考CVE-2024-8412）
```ql
predicate receiverLooksLikeDjangoShortcuts(DataFlow::CallCfgNode call) {
  exists(DataFlow::Node recv |
    call.getFunction() instanceof DataFlow::AttrRead and
    recv = call.getFunction().(DataFlow::AttrRead).getObject() and
    recv instanceof DataFlow::ModuleVariableNode and
    recv.(DataFlow::ModuleVariableNode).getVariable().toString().matches("%django.shortcuts%")
  )
}
```

### 5.7 属性接收者检查（参考CVE-2025-46725）
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

### 5.8 字典get()调用检查（参考CVE-2024-10940）
```ql
predicate isDictGetCall(DataFlow::CallCfgNode call, string keyName) {
  exists(DataFlow::AttrRead attr |
    call.getFunction() = attr and
    attr.getAttributeName() = "get" and
    call.getArg(0).asExpr().(StringLiteral).getText() = keyName
  )
}
```

### 5.9 接收者名称检查（参考CVE-2024-10940）
```ql
predicate receiverIsName(DataFlow::CallCfgNode call, string name) {
  exists(DataFlow::AttrRead attr |
    call.getFunction() = attr and
    attr.getObject().asExpr().(Name).getId() = name
  )
}
```

### 5.10 open()文件模式检查（参考CVE-2024-10940）
```ql
predicate isOpenReadMode(DataFlow::CallCfgNode call, string mode) {
  calleeIsGlobalName(call, "open") and
  call.getArg(1).asExpr().(StringLiteral).getText() = mode
}
```

---

## 6. 成功案例参考

### CVE-2024-7099：SQL注入
- **Source**：受影响函数的参数节点（`DataFlow::ParameterNode`）
- **Sink**：`execute_query_` 方法调用的第一个参数
- **特点**：使用 `source.getScope() = f` 和 `call.getScope() = f` 限定作用域

### CVE-2024-8412：Django重定向
- **Source**：`RemoteFlowSource`（远程用户输入）
- **Sink**：重定向调用的第一个参数（通过helper谓词 `isRedirectTargetArg`）
- **特点**：包含 `isSanitizer` 检查URL验证函数

### CVE-2025-46725：eval注入
- **Source**：`RemoteFlowSource` + `ParameterNode` + `asCfgNode().isLoad()`
- **Sink**：全局 `eval/exec` 或属性调用 `pandas.eval` 等
- **特点**：混合source和sink模式

### CVE-2025-54802：文件写入
- **Source**：`RemoteFlowSource` + 文件/函数限定
- **Sink**：`open()` 的第一个参数或 `.write()` 的接收者
- **特点**：使用 `isAdditionalFlowStep` 连接 `open` 和 `write`

### CVE-2024-10940：Langchain文件读取
- **Source**：字典 `.get('path')` 调用（`kwargs.get('path')` 或 `formatted.get('path')`）在 `format()` 函数中
- **Sink**：`open(path, 'rb')` 的第一个参数在 `encode_image()` 函数中
- **特点**：
  - 使用文件级和函数级作用域限定（`image.py` 文件，`format` 和 `encode_image` 函数）
  - 使用 `isAdditionalFlowStep` 跟踪通过 `image_to_data_url()` 和 `encode_image()` 的流
  - Source 模式：通过 `call.getArg(0).asExpr().(StringLiteral).getText() = "path"` 匹配字典键
  - Sink 模式：通过 `call.getArg(1).asExpr().(StringLiteral).getText() = "rb"` 匹配文件模式

---

## 7. Python 知识库智能推荐

如果你正在生成 Python CodeQL 查询，以下是基于需求分析的智能推荐：

### 相关标签
[[RELEVANT_TAGS]]

### 知识库资源目录
[[KB_DIRECTORY_INDEX]]

### 结构化 KB JSON
`json
[[KB_STRUCTURED_CONTEXT]]
`

### 推荐使用的模块、辅助谓词和模板
[[KB_SUGGESTED_ITEMS]]

### 参考代码片段
[[KB_REFERENCE_SNIPPETS]]

**使用建议**：
- 优先使用推荐的 modules（import 语句）
- 参考推荐的 helpers 来实现辅助谓词
- 如果有匹配的 cases（成功案例），可以借鉴其结构
- 注意避免推荐的 errors 中列出的常见错误

---

## 8. 关键提示与最佳实践

### 8.1 作用域限定
- **必须**使用 `source.getScope() = f` 和 `call.getScope() = f` 来限定作用域
- **禁止**使用 `pn.getEnclosingCallable() = f`（会导致类型不兼容错误）

### 8.2 Sink 定义
- **通常**使用 `sink = call.getArg(0)` 获取调用的第一个参数
- 如果sink是方法调用的接收者，使用 `sink = call.getFunction().(DataFlow::AttrRead).getObject()`

### 8.3 类型检查顺序
- 先进行类型检查：`call.getFunction() instanceof DataFlow::AttrRead`
- 再进行类型转换和访问：`call.getFunction().(DataFlow::AttrRead).getAttributeName()`

### 8.4 Select 语句
- **必须**包含7个参数：`select sink.getNode(), src, sink, "message", src, "source", sink, "sink"`
- 注意：最后两个参数可以使用 `src, "source", sink, "sink"`（推荐）或 `src.getNode(), "source", sink.getNode(), "sink"`（两种格式都有效）

### 8.5 Import 顺序
1. `import python`
2. `import semmle.python.dataflow.new.DataFlow`
3. `import semmle.python.dataflow.new.TaintTracking`
4. `import semmle.python.dataflow.new.RemoteFlowSources`（如果需要）
5. 其他可选模块（如 `Regexp`, `TypeTracking` 等）
6. `import Flow::PathGraph`（在module定义之后）

### 8.6 注释规范
- 使用 `/** ... */` 进行多行注释（QLDoc风格）
- 使用 `//` 进行单行注释
- 为每个helper谓词添加注释说明用途

### 8.7 常见错误避免
- ❌ 不要使用 `MethodCall`（已弃用）
- ❌ 不要使用 `ParameterNode`（无命名空间，应使用 `DataFlow::ParameterNode`）
- ❌ 不要直接调用 `getFile()`（应使用 `getLocation().getFile()`）
- ❌ 不要使用旧API（如 `semmle.python.security.TaintTracking`）
- ❌ 不要直接对AST类型做instance判断，必须先 `asCfgNode().getNode()`

---

## 9. 验证清单

生成查询后，请检查：

- [ ] 使用了 `@problem.severity` 而不是 `@severity`
- [ ] select语句包含7个参数：`select sink.getNode(), src, sink, "message", src, "source", sink, "sink"`（或使用 `src.getNode()` 和 `sink.getNode()`）
- [ ] 使用了 `getScope()` 而不是 `getEnclosingCallable()` 进行作用域检查
- [ ] Sink 使用 `call.getArg(0)` 而不是 `call`（除非需求明确）
- [ ] `isSanitizer` 谓词存在（如果不需要，写 `none()`）
- [ ] `import Flow::PathGraph` 在module定义之后
- [ ] 所有类型检查都先进行 `instanceof` 判断
- [ ] 参考了KB库推荐的成功案例
