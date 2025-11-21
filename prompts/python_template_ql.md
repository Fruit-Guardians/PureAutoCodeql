# Python CodeQL 规划 + 实现模板（Python new dataflow 专用）

> 目标：一次性给出可执行的 Python CodeQL 查询。必须先规划（Plan Summary）再输出代码，严格遵循下述骨架和 API。
> 
> **指南索引**：
> - **核心规则与骨架**：见本文件。
> - **Source/Sink 定义与代码积木**：请查阅 `prompts/python_patterns.md`。
> - **复杂案例参考**：请查阅 `prompts/python_cases.md`。

---

## CodeQL生成规则 (CRITICAL)

- **导入规范**
  - 必须：`import python`
  - 必须：`import semmle.python.dataflow.new.DataFlow`, `import semmle.python.dataflow.new.TaintTracking`
  - 推荐：`import semmle.python.ApiGraphs` (用于处理第三方库调用)
  - 需要远程源时：`import semmle.python.dataflow.new.RemoteFlowSources`
  - `import Flow::PathGraph` 放在 `module` 定义之后
- **配置与模块**
  - 使用 `module VulnConfig implements DataFlow::ConfigSig`
  - 使用 `module Flow = TaintTracking::Global<VulnConfig>;`
- **Select 语句**
  - 使用 7 参数格式，例如：
    `select sink.getNode(), src, sink, "message", src, "source", sink, "sink"`
- **类型与节点约定**
  - 常用：`DataFlow::CallCfgNode`, `DataFlow::ParameterNode`, `DataFlow::AttrRead`
  - 作用域检查使用 `source.getScope() = f`、`call.getScope() = f`
  - 远程源：`src instanceof RemoteFlowSource`
- **空谓词返回**
  - 使用 `none()`，不要使用 `false`
- **禁止事项**
  - 不要使用 `MethodCall`、裸 `ParameterNode`、旧的 `semmle.python.security.*`
  - 不要直接对 AST 做 instance 判断，应先 `asCfgNode().getNode()`
  - 不要使用 `getEnclosingCallable()` 替代 `getScope()`
  - 不要混用 `DataFlow::Node` 和 `API::Node`（API Graph 节点需调用 `.asSource()` 或 `.asSink()` 转换为数据流节点）。

## 框架与场景特化提示

### Flask / FastAPI / Web API
- **强烈推荐**使用 `API::moduleImport` 定义 Source，例如：
  ```ql
  source = API::moduleImport("flask").getMember("request").getMember("get_json").getReturn().asSource()
  ```
- 若需求强调某个 JSON/dict 字段（如 `pdf_path`），可在 `isSource` 或 `isAdditionalFlowStep` 中套用 `python_patterns.md` 的“字典 get() 模式”以确保命中。

### 文件与路径操作
- 典型 Sink：`os.path.exists`, `os.path.join`, `open`, `pathlib.Path(...).read_text()`。
- **强烈推荐**使用 `API::moduleImport` 定义 Sink，例如：
  ```ql
  exists(DataFlow::CallCfgNode call |
    call = API::moduleImport("os").getMember("path").getMember("exists").getACall() and
    sink = call.getArg(0)
  )
  ```
- 若 Sink 是接收者（如 `.write()`、`.review()` 需要对象），务必在 `isAdditionalFlowStep` 中把路径参数传递到该对象。
- 路径遍历场景通常没有 Sanitizer，若无净化逻辑 `isSanitizer` 直接返回 `none()`.

## 1. 输出结构

1. `### Plan Summary`  
   - 列出 Sources / Sinks / Sanitizers / Helpers / Scope，每项需说明来源（Requirement、KB JSON 或参考案例）。  
   - 指明所用类型：`DataFlow::ParameterNode`, `DataFlow::CallCfgNode`, `DataFlow::AttrRead` 等。
   - **重要**：明确指出使用了 `python_patterns.md` 中的哪些模式（如 "使用 Source 模式 D"）。
2. `### CodeQL Query`  
   - 仅一个 ```ql 代码块，内容必须遵循第 2 节骨架，不得增删结构。  
   - 使用英文诊断信息，`select` **必须包含 7 个参数**：`select sink.getNode(), src, sink, "message", src.getNode(), "source", sink.getNode(), "sink"`.

---

## 2. 代码骨架（严格遵循此结构）

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

/** ---------- Helper predicates ---------- */
// 从 python_patterns.md 中复制所需 helper
<HELPER_PREDICATES>

/** ---------- Config ---------- */
module VulnConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源 */
  predicate isSource(DataFlow::Node source) {
    // 从 python_patterns.md 中选择合适的 Source 模式
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点 */
  predicate isSink(DataFlow::Node sink) {
    // 从 python_patterns.md 中选择合适的 Sink 模式
    <SINK_DEFINITION>
  }

  /** Additional flow steps: 额外的数据流步骤 */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // 如果需要（如 open -> write），参考 python_patterns.md 模式
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

---

## 3. Python 知识库智能推荐

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
- 查阅 `prompts/python_patterns.md` 获取推荐的 helper 和 Source/Sink 模式代码。
- 如果遇到复杂逻辑，查阅 `prompts/python_cases.md` 中的类似案例。

---

## 4. 验证清单

生成查询后，请检查：

- [ ] 使用了 `@problem.severity` 而不是 `@severity`
- [ ] select语句包含7个参数：`select sink.getNode(), src, sink, "message", src, "source", sink, "sink"`
- [ ] 使用了 `getScope()` 而不是 `getEnclosingCallable()` 进行作用域检查
- [ ] Sink 使用 `call.getArg(0)` 而不是 `call`（除非是 `write` 的 receiver）
- [ ] `isSanitizer` 谓词存在（如果不需要，写 `none()`）
- [ ] `import Flow::PathGraph` 在module定义之后
- [ ] 所有类型检查都先进行 `instanceof` 判断
- [ ] 若涉及资源传播（如 open->write），检查是否正确实现了 `isAdditionalFlowStep`
