## 问题与成因
- 抽象语法类型不匹配：`UnaryExpr/IndexExpr/CastExpr/BinaryExpr` 在新版 C/C++ 库中已更名为 `UnaryOperation/ArrayAccess/CastOperation/BinaryOperation` 或不建议直接引用
- 签名实现不一致：`module ... implements DataFlow::ConfigSig` 下声明了 `isSanitizer`，该签名不包含此成员，导致“应标记为 additional”与“未使用”告警
- AI易误填：过多AST类型占位与复杂步，导致生成时随意拼写类型名引发语法错误

## 修订目标
- 采用稳定的 TaintTracking 配置签名，支持 `isSanitizer` 与 `isAdditionalFlowStep`
- 删掉或改写易错的AST类型占位，保留少量通用别名/传播步
- 清晰、受控的占位位点，确保 AI 仅填字符串/索引，不触碰类型系统

## 具体修改
- 签名替换：将 `prompts/c_template_ql.md:98` 的 `module VulnConfig implements DataFlow::ConfigSig` 改为 `class VulnConfiguration extends TaintTracking::Configuration`
  - 添加构造与 `override`：`override predicate isSource/isSink/isAdditionalFlowStep`，并保留 `predicate isSanitizer`（无需 additional）
  - 使用：`module VulnFlow = TaintTracking::Global<VulnConfiguration>`
- 危险API与目标范围：保留函数名/正则占位
  - `prompts/c_template_ql.md:25–34` 强化 `inTarget` 限定但仅用 `Function/Call` API，不引入复杂AST
  - `prompts/c_template_ql.md:42–46` 用 `isDangerAPI`（函数名或正则），避免 AI 直接改类名
- 参数索引：在 `VulnerableCall` 保留 `getDangerArg/getDataArg` 索引占位（整数）
- 传播步瘦身：移除 `UnaryExpr/IndexExpr/CastExpr/BinaryExpr/ConditionalExpr` 等占位
  - 仅保留：字段别名（`FieldAccess` 同字段传递）、必要时加入新版稳定类型名：`UnaryOperation` 的 `*` 解引用、`ArrayAccess` 索引、`CastOperation` 转换（如必须）
  - 若保留上述类型，模板内固定类型名，不留可编辑占位，避免 AI 误拼
- 守卫/清洗器：将边界检查集中在 `isGuardExpr`，组合到 `isSanitizer`
  - 不再对二元比较的左右操作数暴露占位为“表达式对象”，只用字符串占位或安全函数名（例如 `min/sizeof/snprintf`）
  - 可选：若仍使用 `DataFlow::ConfigSig`，则将 `isSanitizer` 标记为 `additional predicate` 并在查询中引用，避免“未使用”警告

## 最小稳定骨架（示例片段）
- 签名与使用：
```
class VulnConfiguration extends TaintTracking::Configuration {
  VulnConfiguration() { this = "[CVE 描述]" }
  override predicate isSource(Node s) { exists(Expr e | isSourceExpr(e) and s.asExpr() = e) }
  override predicate isSink(Node k) { exists(VulnerableCall c | k.asExpr() = c.getDangerArg() or k.asExpr() = c.getDataArg()) }
  override predicate isAdditionalFlowStep(Node a, Node b) { exists(FieldAccess x, FieldAccess y | a.asExpr() = x and b.asExpr() = y and x.getTarget() = y.getTarget()) }
  predicate isSanitizer(Node g) { exists(Expr e | isGuardExpr(e) and g.asExpr() = e) }
}
module VulnFlow = TaintTracking::Global<VulnConfiguration>
```
- 传播步（如确需）：
```
override predicate isAdditionalFlowStep(Node a, Node b) {
  exists(FieldAccess x, FieldAccess y | a.asExpr() = x and b.asExpr() = y and x.getTarget() = y.getTarget())
  or exists(UnaryOperation d | d.getOperator() = "*" and a.asExpr() = d.getOperand() and b.asExpr() = d)
  or exists(ArrayAccess ia | a.asExpr() = ia.getArray() and b.asExpr() = ia)
  or exists(CastOperation ce | a.asExpr() = ce.getOperand() and b.asExpr() = ce)
}
```
- 守卫（简化占位）：
```
predicate isGuardExpr(Expr e) {
  exists(FunctionCall m | m.getTarget().hasGlobalName("[安全函数，如 snprintf/min]") and e = m)
  or exists(Expr x, Expr y | x.toString() = "[长度变量]" and y.toString() = "[容量变量]" and e.toString().regexpMatch(".*<=.*"))
}
```

## AI 填充约束
- 仅填函数名/文件名正则/参数索引/变量名字符串，不得改类型名
- `isDangerAPI`/`inTarget` 只添加 OR 条件，不新增谓词/类
- `isGuardExpr` 仅填常见安全调用或简单比较的字符串占位

## 验证与回退
- LSP 先行校验：若出现类型解析错误，立即切到仅字段别名版本（移除 `UnaryOperation/ArrayAccess/CastOperation`）
- 若 `isSanitizer` 仍告警，确认签名是否为 `TaintTracking::Configuration`，否则改为 `additional predicate isSanitizer(...)`

## 里程碑
- Phase A：替换签名与瘦身传播步，确保零语法错误
- Phase B：抽象守卫/来源占位，压住误报
- Phase C：按 CVE 家族增补 `isDangerAPI` 列表与目标范围正则