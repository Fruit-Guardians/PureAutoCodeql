### Python CodeQL 知识库索引

请使用 MCP `filesystem`（根目录 `projects/`）按需读取以下文件，获取模块、辅助谓词、案例和错误修复提示。

- 可读取的路径列表：  
[[KB_DIRECTORY_INDEX]]
- 自动匹配到的标签：[[RELEVANT_TAGS]]
- 推荐优先查看的条目：  
[[KB_SUGGESTED_ITEMS]]

### 查询骨架（必须遵循）

1. **务必先通过 MCP 读取** `projects/python_kb/templates/path_problem_skeleton.ql`，在本地复制骨架。  
2. 将以下骨架粘贴到最终输出中，并替换占位符 `<NAME>`、`<DESCRIPTION>` 等，同时填充 `module VulnConfig` 中的 Source/Sink/FlowStep/Sanitizer 逻辑。  
3. 只允许在骨架内增删谓词或 import，不得改变 `module VulnConfig implements DataFlow::ConfigSig`、`module Flow = TaintTracking::Global<VulnConfig>;`、`import Flow::PathGraph` 以及 `select` 的结构。

```ql
/**
 * @kind path-problem
 * @name <NAME>
 * @description <DESCRIPTION>
 * @id <ID>
 * @tags <TAG-LIST>
 * @severity <SEVERITY>
 * @precision <PRECISION>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources

<HELPER-PREDICATES>

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { /* TODO: sources */ }
  predicate isSink(DataFlow::Node sink)   { /* TODO: sinks */ }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { /* optional */ }
  predicate isSanitizer(DataFlow::Node node) { /* optional */ }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<diagnostic message>",
  src, "source", sink, "sink"
```

### 编写要点

- 仅使用 **`semmle.python.dataflow.new.*`** 模块族（`DataFlow`、`TaintTracking`、`RemoteFlowSources` 等），不要再引用 `semmle.python.security.*`。  
- 所有 Source/Sink 逻辑都在 `VulnConfig` 内实现；若需额外 helper，请在 `<HELPER-PREDICATES>` 位置声明，并从 `projects/python_kb/knowledge_base/helpers.json` 中挑选适配的谓词。  
- 如果需要模板/案例灵感，优先读取 `projects/python_kb/knowledge_base/templates.json`、`cases.json` 中的推荐条目。  
- 在生成最终查询前，再次检查 `select` 只包含四个元素（`sink.getNode(), source, sink, "message"`），并确保必要的 `flowPath`、`PathNode` 用法完整。
