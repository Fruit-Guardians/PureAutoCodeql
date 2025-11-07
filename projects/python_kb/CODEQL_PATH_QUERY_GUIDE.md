# Python Path Query 生成指令（面向模型）

本说明直接约束你的生成行为。除非特别说明，否则必须严格按照以下规则写出符合 CodeQL 最新语法的 Python `path-problem` 查询。

## 1. 固定骨架（禁止改动）

在输出中原样保留以下结构，仅替换 `<PLACEHOLDER>` 内容，并在 `module VulnConfig` 内填充逻辑。除可选 import 外任何结构调整都被视为错误。

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
  predicate isSource(DataFlow::Node src) { /* 填写 source 条件 */ }
  predicate isSink(DataFlow::Node sink)   { /* 填写 sink 条件 */ }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { /* 可选 */ }
  predicate isSanitizer(DataFlow::Node node) { /* 可选 */ }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<diagnostic message>",
  src, "source", sink, "sink"
```

- `import` 顺序保持一致；只在需要时添加额外模块（如 `semmle.python.dataflow.new.Regexp`）。
- 所有辅助谓词应放在 `<HELPER-PREDICATES>` 区域，命名清晰，例如 `predicate calleeIsAttr(...)`。

## 2. 语法与类型约束

在任何谓词中遵循以下规则：

- 所有 `isSource`、`isSink`、`isAdditionalFlowStep`、`isSanitizer` 形参类型固定为 `DataFlow::Node`。
- 组合逻辑必须使用 `and`、`or`、`not`，返回布尔表达式。
- 访问调用信息时先进行类型判定：
  ```ql
  call.getFunction() instanceof DataFlow::AttrRead and
  call.getFunction().(DataFlow::AttrRead).getAttributeName() = "redirect"
  ```
- 远程用户输入写法固定为 `src instanceof RemoteFlowSource`。若需要自定义来源，需额外继承 `RemoteFlowSource::Range`（在单独 helper 文件内实现）。
- 判断文件或函数时使用 `node.getLocation().getFile().getBaseName()`、`node.getScope().getName()` 等方法，不得直接对 AST 类型做 instance 判断。
- 区分位置/关键字参数：`call.getArg(0)`、`call.getKwarg("param")`。
- 禁止引用旧库（例如 `semmle.python.security.TaintTracking`）以及任何 `internal/` 模块。

- 若 Source 来自函数参数，必须通过 `DataFlow::ParameterNode` 建模：
  ```ql
  exists(Function f |
    /* 你的函数过滤条件 */ and
    source instanceof DataFlow::ParameterNode and
    source.getScope() = f
  )
  ```
  禁止实例化 `Function::Parameter` 或调用其 `getFunction()`、`getCfgNode()`、`getAParameter()` 等旧 API。
- 判断调用所在函数时使用 `call.getScope()`：
  ```ql
  exists(DataFlow::CallCfgNode call, Function f |
    call.getScope() = f and
    f.getName() = "target"
  )
  ```
  不要将 `call.getEnclosingCallable()` 直接与 `Function` 比较。
- `isSanitizer` 仅在需要时声明，并写成 `additional predicate isSanitizer(...) { ... }`；若无净化逻辑可以直接删除该谓词。

## 3. 常用类型与成员速查

- `DataFlow::CallCfgNode`
  - `call.getFunction()` → `DataFlow::Node`
  - `call.getArg(int)`, `call.getKwarg(string)`
  - `call.asCfgNode().getLocation()` 获取调用位置
- `DataFlow::AttrRead`
  - `call.getFunction() instanceof DataFlow::AttrRead`
  - `call.getFunction().(DataFlow::AttrRead).getAttributeName()`
  - `call.getFunction().(DataFlow::AttrRead).getObject()`
- `DataFlow::ModuleVariableNode`
  - `node.(DataFlow::ModuleVariableNode).getVariable().getId()`
- `DataFlow::Node`
  - `node.asCfgNode().getNode()` 获取 AST
  - `node.getLocation().getFile().getRelativePath()` / `.getBaseName()`
  - `node.getEnclosingCallable().getScope().getName()`
- `DataFlow::ParameterNode`
  - `node instanceof DataFlow::ParameterNode`
  - `node.getScope()` 返回其定义的 `Function`
- `Flow::PathNode`
  - `Flow::flowPath(src, sink)`
  - `src.getNode()`、`sink.getNode()`

## 4. 行为准则

Do：
- 使用 `exists(... | ...)` 限定局部变量范围。
- 把常用条件提炼成单独谓词（例如 `predicate isRedirectCall(DataFlow::CallCfgNode call)`）。
- 需要限制作用域时，创建谓词 `predicate inTargetFile(DataFlow::Node n)` 等并复用。
- 若流程需要额外步骤（例如 `open()` 返回值传入 `.write()`），通过 `isAdditionalFlowStep` 明确建模。

Don’t：
- 不得调整 `select` 参数顺序或省略任意 path node。
- 不得直接对 `DataFlow::Node` 做 AST 类断言，必须先 `asCfgNode().getNode()`。
- 避免硬编码 import 缺失、类型不匹配、旧 API。
- 不得实例化 `Function::Parameter`、调用 `getFunction()` / `getCfgNode()` / `getAParameter()`。
- 不要声明普通的 `predicate isSanitizer(...)`；如无净化逻辑请删除，或使用 `additional predicate`。

## 5. 自检流程

生成查询后必须执行：
1. `codeql query compile <path/to/query.ql>`。
2. 如编译失败，记录错误消息与对应谓词片段，再根据错误类型进行最小修复。

## 6. 公共模块引用清单

生成时默认 import 以下模块，必要时追加：

- `semmle.python.dataflow.new.DataFlow`
- `semmle.python.dataflow.new.TaintTracking`
- `semmle.python.dataflow.new.RemoteFlowSources`
- 可选：`semmle.python.dataflow.new.Regexp`、`semmle.python.dataflow.new.TypeTracking`、`semmle.python.dataflow.new.FlowSummary`、`semmle.python.dataflow.new.BarrierGuards`、`semmle.python.dataflow.new.SensitiveDataSources`

## 7. 成功模式索引

在需要具体场景参考时，可比照以下查询：

- `CVE-2024-8412/CVE-2024-8412.ql`：多种 Django 重定向 sink，`RemoteFlowSource` 作为来源。  
- `CVE-2025-46725/CVE-2025-46725.ql`：混合 `eval`/`exec` sink，展示属性接收者判定。  
- `CVE-2025-54802/CVE-2025-54802.ql`：`isAdditionalFlowStep` 连接 `open()` 与 `.write()`。  
- `CVE-2024-10940/CVE-2024-10940.ql`：限制文件与函数范围同时追踪字面量。

## 8. `semmle/python/dataflow/new` 模块速查

| 模块文件 | Import 路径 | 公开类型 / 谓词 | 典型用途 |
| --- | --- | --- | --- |
| `DataFlow.qll` | `import semmle.python.dataflow.new.DataFlow` | `DataFlow::ConfigSig`, `DataFlow::Node`, `DataFlow::CallCfgNode`, `DataFlow::localFlow` | 全部路径分析的基础 |
| `TaintTracking.qll` | `import semmle.python.dataflow.new.TaintTracking` | `TaintTracking::Global<Config>`, `TaintTracking::Configuration`, `TaintTracking::localTaint` | 全局/局部污点追踪 |
| `RemoteFlowSources.qll` | `import semmle.python.dataflow.new.RemoteFlowSources` | `RemoteFlowSource`, `RemoteFlowSource::Range` | 远程/用户输入 Source |
| `BarrierGuards.qll` | `import semmle.python.dataflow.new.BarrierGuards` | `ConstCompareBarrier` | 常见条件屏障建模 |
| `FlowSummary.qll` | `import semmle.python.dataflow.new.FlowSummary` | `SummarizedCallable`, `propagatesFlow(...)` | 自定义库函数数据流摘要 |
| `Regexp.qll` | `import semmle.python.dataflow.new.Regexp` | `RegExpPatternSource`, `getAParse()`, `getRegExpTerm()` | 字符串到正则模式的流 |
| `SensitiveDataSources.qll` | `import semmle.python.dataflow.new.SensitiveDataSources` | `SensitiveDataSource`, `SensitiveDataSource::Range`, `SensitiveDataClassification` | 敏感数据来源建模 |
| `TypeTracking.qll` | `import semmle.python.dataflow.new.TypeTracking` | `TypeTracker`, `TypeTracker::step`, `startInAttr` | 追踪对象类型或 API 能力 |

### 8.1 示例片段

按需引用以下模板，确保语法与类型正确：

- **Flow summary**
  ```ql
  import semmle.python.dataflow.new.FlowSummary

  class RequestsGetSummary extends FlowSummary::SummarizedCallable {
    RequestsGetSummary() { this.hasQualifiedName("requests", "get") }

    override predicate propagatesFlow(string input, string output, boolean preserves, string model) {
      input = "argument[0]" and output = "return" and preserves = true
    }
  }
  ```
- **Type tracking**
  ```ql
  import semmle.python.dataflow.new.TypeTracking

  predicate isFastApiRequest(DataFlow::Node n) {
    exists(TypeTracker tt |
      tt.start() and n = tt.getNode() and
      tt.smallstep("argument[0]", "return") and
      tt.getAttr() = "request"
    )
  }
  ```
- **Regular expression sources**
  ```ql
  import semmle.python.dataflow.new.Regexp

  predicate isRegExpPattern(DataFlow::Node n) {
    n instanceof RegExpPatternSource
    or exists(RegExpPatternSource src |
      src.getAParse() = n.asCfgNode()
    )
  }
  ```
- **Barrier guard**
  ```ql
  import semmle.python.dataflow.new.BarrierGuards

  predicate isSanitizer(DataFlow::Node n) {
    n = ConstCompareBarrier()
  }
  ```
- **敏感数据来源**
  ```ql
  import semmle.python.dataflow.new.SensitiveDataSources

  predicate isSource(DataFlow::Node src) {
    src instanceof SensitiveDataSource
    or exists(SensitiveDataSource::Range r |
      src = r and r.getClassification() = SensitiveDataClassification::password()
    )
  }
  ```

按照上述指令生成查询，即可最大程度避免语法错误并快速定位问题源。必要时结合 7 节示例对照实操细节。*** End Patch
