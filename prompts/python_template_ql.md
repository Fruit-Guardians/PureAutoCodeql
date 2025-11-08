# Python CodeQL 规划 + 实现模板（Python new dataflow 专用）

> 目标：一次性给出可执行的 Python CodeQL 查询。必须先规划（Plan Summary）再输出代码，严格遵循下述骨架和 API。

---

## 1. 输出结构
1. `### Plan Summary`  
   - 列出 Sources / Sinks / Sanitizers / Helpers / Scope，每项需说明来源（Requirement、KB JSON 或参考案例）。  
   - 指明所用类型：`DataFlow::ParameterNode`, `DataFlow::CallCfgNode`, `DataFlow::AttrRead` 等。
2. `### CodeQL Query`  
   - 仅一个 ```ql 代码块，内容必须遵循第 3 节骨架，不得增删结构。  
   - 使用英文诊断信息，`select` 只能包含 4 个参数：`select sink.getNode(), source, sink, "message"`.

---

## 2. 常用类型/方法速查（必须使用）
| 目的 | 推荐写法 |
| --- | --- |
| 函数参数 | `DataFlow::ParameterNode pn | pn = source.(DataFlow::ParameterNode)` |
| 参数所属函数 | `pn.getEnclosingCallable()` 或 `pn.getFunction()` |
| 调用节点 | `DataFlow::CallCfgNode call` |
| 方法名/属性名 | `call.getFunction().(DataFlow::AttrRead).getAttributeName()` |
| 文件限定 | `n.getLocation().getFile().getBaseName() = "xxx.py"` |
| Flow 模块 | `module Flow = TaintTracking::Global<VulnConfig>;` |

禁止出现：`MethodCall`, `ParameterNode`（无命名空间）、`getFile()`（直接调用）、旧 API (`Call`, `MethodAccess`)。

---

## 3. 代码骨架（只能在标注区域填写逻辑）

```ql
/**
 * @kind path-problem
 * @name <填写名称>
 * @description <简要说明>
 * @id python/<project>-<identifier>
 * @tags security, taint, <更多标签>
 * @problem.severity <error|warning|recommendation>
 * @precision <high|medium|low>
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import Flow::PathGraph

// === Helper predicates（如需） ===
<HELPER_PREDICATES>

// === 配置 ===
module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(DataFlow::ParameterNode pn |
      pn = source.(DataFlow::ParameterNode) and
      <SOURCE_CONDITIONS>
    )
  }

  predicate isSink(DataFlow::Node sink) {
    exists(DataFlow::CallCfgNode call |
      sink = call and
      <SINK_CONDITIONS>
    )
  }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    none()
  }

  predicate isSanitizer(DataFlow::Node node) {
    none()
  }
}

module Flow = TaintTracking::Global<VulnConfig>;

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<诊断信息>"
```

> 仅可在 `<HELPER_PREDICATES>`, `<SOURCE_CONDITIONS>`, `<SINK_CONDITIONS>`, `<诊断信息>` 区域填写内容；其余结构保持不变（可增加真实需要的 helper，但必须放在 `<HELPER_PREDICATES>` 区域）。

---

## 4. 提示
- 在 Plan Summary 中若引用了知识库案例，请说明“改动点 vs 案例”。  
- 无净化器时直接写 `none()`，勿留下空 predicate。  
- 若需求提示的属性/函数不在 KB 中，Plan Summary 中应解释为何要扩展范围。
