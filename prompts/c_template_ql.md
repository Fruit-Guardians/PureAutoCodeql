# C/C++ CodeQL 规划 + 实现模板 (New DataFlow API)

> 目标：一次性给出可执行的 C/C++ CodeQL 查询。必须先规划（Plan Summary）再输出代码，严格遵循下述骨架和 API。
> 
> **指南索引**：
> - **核心规则与骨架**：见本文件。
> - **Source/Sink/FlowStep 定义**：请查阅 `prompts/c_patterns.md`。
> - **复杂案例参考**：请查阅 `prompts/c_cases.md`。

---

## CodeQL生成规则 (CRITICAL)

- **导入规范**
  - 必须：`import cpp`
  - 必须：`import semmle.code.cpp.dataflow.new.DataFlow`
  - 必须：`import semmle.code.cpp.dataflow.new.TaintTracking`
  - ❌ **严禁**使用旧模块 `semmle.code.cpp.dataflow.*` (非 new)
- **配置与模块**
  - 使用 `module VulnConfig implements DataFlow::ConfigSig`
  - 使用 `module VulnFlow = TaintTracking::Global<VulnConfig>;`
  - 必须在 module 定义之后 `import VulnFlow::PathGraph`
- **Select 语句**
  - 使用 7 参数格式：`select sink.getNode(), source, sink, "message", source.getNode(), "source", sink.getNode(), "sink"`
- **路径与节点**
  - 使用 `VulnFlow::PathNode`
  - 使用 `VulnFlow::flowPath(source, sink)`
- **类型要点**
  - 指针解引用：`PointerDereferenceExpr` (不要用 DerefExpr)
  - 数组：`ArrayExpr`
  - 地址取：`AddressOfExpr`
  - 字段：`FieldAccess`
- **空谓词返回**
  - 使用 `none()`，不要使用 `false`

## 1. 输出结构

1. `### Plan Summary`
   - 列出 Sources / Sinks / Sanitizers / Helpers / Scope。
   - 明确指出使用了 `c_patterns.md` 中的哪些模式（如 "使用 Additional Flow Step 模式 A"）。
   - **特别注意**：C/C++ 通常需要 `isAdditionalFlowStep` 来处理指针/算术传播。
2. `### CodeQL Query`
   - 仅一个 ```ql 代码块，内容必须遵循第 2 节骨架，不得增删结构。

---

## 2. 代码骨架（严格遵循此结构）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称>
 * @description <详细描述>
 * @id cpp/<project>-<identifier>
 * @tags security, taint, <相关标签>
 * @problem.severity <error|warning|recommendation>
 * @precision <high|medium|low>
 */

import cpp
import semmle.code.cpp.dataflow.new.DataFlow
import semmle.code.cpp.dataflow.new.TaintTracking

/** ---------- Helper predicates ---------- */
// 从 c_patterns.md 中复制所需 helper (如 inTargetFile, inTargetFunction)
<HELPER_PREDICATES>

/** ---------- Config ---------- */
module VulnConfig implements DataFlow::ConfigSig {
  /** Sources: 定义污染源 */
  predicate isSource(DataFlow::Node source) {
    // 从 c_patterns.md 中选择 Source 模式
    <SOURCE_DEFINITION>
  }

  /** Sinks: 定义汇聚点 */
  predicate isSink(DataFlow::Node sink) {
    // 从 c_patterns.md 中选择 Sink 模式
    <SINK_DEFINITION>
  }

  /** Additional flow steps: 额外的数据流步骤 (重要!) */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    // 强烈建议参考 c_patterns.md 中的模式 A (Comprehensive)，除非只需要非常简单的传播
    <ADDITIONAL_FLOW_STEPS>
  }

  /** Sanitizers: 净化器（如果不需要，写 none()） */
  predicate isSanitizer(DataFlow::Node node) {
    <SANITIZER_DEFINITION>
  }
}

// 定义全局污点追踪模块
module VulnFlow = TaintTracking::Global<VulnConfig>;
// 必须在 module 定义之后导入 PathGraph
import VulnFlow::PathGraph

from VulnFlow::PathNode source, VulnFlow::PathNode sink
where VulnFlow::flowPath(source, sink)
select sink.getNode(), source, sink,
  "<诊断信息>",
  source.getNode(), "source", sink.getNode(), "sink"
```

---

## 3. C/C++ 知识库智能推荐

如果你正在生成 C/C++ CodeQL 查询，以下是基于需求分析的智能推荐：

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
- 优先使用推荐的 modules
- **重点**：C/C++ 极其依赖 `isAdditionalFlowStep`，请务必查看 `c_patterns.md` 中的实现。
- 遇到指针运算、结构体解析等复杂逻辑，参考 `c_cases.md`。

---

## 4. 验证清单

- [ ] 使用了 `semmle.code.cpp.dataflow.new` 包
- [ ] `module ... implements DataFlow::ConfigSig`
- [ ] `isAdditionalFlowStep` 已实现（覆盖指针/算术/字段传播）
- [ ] select 语句包含 7 个参数
- [ ] 没有使用已弃用的 `DerefExpr` 或 `MethodAccess`
