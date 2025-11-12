/**
 * @name [漏洞名称 / CVE]
 * @description [用 2~3 句话描述漏洞成因、补丁逻辑与本查询展示的路径语义]
 * @kind path-problem
 * @id cpp/[唯一 ID]
 * @problem.severity [error|warning|recommendation]
 * @security-severity [0.0-10.0]
 * @precision [high|medium|low]
 * @tags security
 *       vulnerability
 *       external/cwe/cwe-[CWE-ID]
 *       external/cve/cve-[CVE-ID]
 */

import cpp
import semmle.code.cpp.dataflow.new.TaintTracking
import semmle.code.cpp.dataflow.new.DataFlow

/**
 * ⚠️ 重要：C/C++ CodeQL 最新API规范（必须严格遵守）
 * 
 * 1. 导入规范：
 *    - ✅ 必须使用: import semmle.code.cpp.dataflow.new.TaintTracking
 *    - ✅ 必须使用: import semmle.code.cpp.dataflow.new.DataFlow
 *    - ❌ 禁止使用: import semmle.code.cpp.dataflow.DataFlow (已弃用)
 *    - ❌ 禁止使用: import semmle.code.cpp.dataflow.TaintTracking (已弃用)
 * 
 * 2. 配置模块规范：
 *    - ✅ 必须使用: module VulnConfig implements DataFlow::ConfigSig
 *    - ❌ 禁止使用: class VulnConfig extends DataFlow::Configuration (旧API)
 *    - ❌ 禁止使用: class VulnConfig extends TaintTracking::Configuration (不存在)
 * 
 * 3. 全局流模块规范：
 *    - ✅ 必须使用: module VulnFlow = TaintTracking::Global<VulnConfig>;
 *    - ✅ 必须使用: import VulnFlow::PathGraph
 *    - ❌ 禁止使用: DataFlow::PathGraph (不存在)
 * 
 * 4. 路径节点规范：
 *    - ✅ 必须使用: VulnFlow::PathNode (从模块别名获取)
 *    - ❌ 禁止使用: DataFlow::PathNode (已弃用)
 *    - ❌ 禁止使用: TaintTracking::PathNode (不存在)
 * 
 * 5. 流路径谓词规范：
 *    - ✅ 必须使用: VulnFlow::flowPath(src, snk)
 *    - ❌ 禁止使用: cfg.hasFlowPath(source, sink) (旧API)
 *    - ❌ 禁止使用: DataFlow::hasFlowPath(source, sink) (不存在)
 * 
 * 6. 指针解引用类型规范：
 *    - ✅ 必须使用: PointerDereferenceExpr (正确的AST类型)
 *    - ❌ 禁止使用: DerefExpr (不存在)
 *    - ❌ 禁止使用: PointerDereference (不存在)
 * 
 * 7. 数组和地址操作类型规范：
 *    - ✅ 数组访问: 使用 ArrayExpr，getArrayBase() 获取数组基址
 *    - ✅ 地址取操作: 使用 AddressOfExpr，getOperand() 获取操作数
 *    - ⚠️ 注意: 这些类型在 isAdditionalFlowStep 中用于特殊数据流传播
 * 
 * 8. DataFlow::Node 转换方法规范：
 *    - ✅ source.asExpr() = e: 将 DataFlow::Node 转换为 Expr
 *    - ✅ sink.getNode(): 从 PathNode 获取底层 Node
 *    - ✅ src.getNode().asExpr(): 从 PathNode 获取 Node 再转换为 Expr
 *    - ⚠️ 注意: 类型转换失败时谓词不匹配，不会报错
 * 
 * 9. 查询结构规范：
 *    - from 子句: from VulnFlow::PathNode src, VulnFlow::PathNode snk, [其他类]
 *    - where 子句: where VulnFlow::flowPath(src, snk) and [其他条件]
 *    - select 子句: select [元素], src, snk, "[消息]", src.getNode(), "[source标签]", snk.getNode(), "[sink标签]"
 *    - ⚠️ 注意: select 语句必须包含 7 个参数（元素、src、snk、消息、src节点、source标签、snk节点、sink标签）
 *    - ⚠️ 注意: 第一个参数通常是 sink 相关的元素（如 VulnerableCall），用于显示问题位置
 * 
 * 10. 模块别名命名规范：
 *     - ✅ 可以使用任意名称: module Flow = TaintTracking::Global<VulnConfig>;
 *     - ✅ 推荐使用有意义的名称: VulnFlow, TaintFlow, DataFlow 等
 *     - ⚠️ 注意: 别名名称必须与 import 语句中的名称一致
 * 
 * 11. 谓词返回值规范：
 *     - ✅ 使用 none() 表示谓词不匹配任何内容（用于可选谓词）
 *     - ❌ 禁止使用 false 作为表达式返回值
 *     - ⚠️ 注意: isAdditionalFlowStep 和 isSanitizer 如果不需要，应使用 none()
 *     - ⚠️ 重要: isSanitizer 必须标记为 additional，因为它不是 DataFlow::ConfigSig 接口的必需成员
 * 
 * 12. 常用 AST 类型和方法规范：
 *     - FunctionCall: getTarget() 获取函数, getArgument(n) 获取第n个参数, getEnclosingFunction() 获取包含函数
 *     - VariableAccess: getTarget() 获取变量, getName() 获取变量名
 *     - FieldAccess: getTarget() 获取字段, getQualifier() 获取限定对象
 *     - Parameter: getFunction() 获取所属函数, getName() 获取参数名
 *     - LocalVariable: getFunction() 获取所属函数, getName() 获取变量名
 *     - ⚠️ 注意: 当通过 VariableAccess.getTarget() 获取变量时，需要先进行类型转换才能调用类型特定方法
 *     - ⚠️ 例如: va.getTarget().(LocalVariable).getFunction() 而不是 va.getTarget().getFunction()
 *     - Function: hasGlobalName(name) 检查全局名称, getFile() 获取文件
 *     - File: getRelativePath() 获取相对路径
 *     - ⚠️ 注意: 参数索引从 0 开始，getArgument(0) 是第一个参数
 */

// --- 目标范围（可选） --------------------------------------------------------

/**
 * 限制到受影响的函数或文件，减少误报。
 * 可根据补丁信息添加多个 OR 条件。
 */
predicate inTarget(Function f) {
  f.hasGlobalName("[受影响函数名]") or
  f.getFile().getRelativePath().regexpMatch(".*[受影响文件]$")
}

// --- 危险调用建模 ------------------------------------------------------------

/**
 * 用类来封装危险调用，便于访问关键参数。
 * ⚠️ 请使用 diff/案例情报中真实出现的危险 API 与参数索引（例如补丁强调的 memcpy 第三个参数 s）。
 */
class VulnerableCall extends FunctionCall {
  VulnerableCall() {
    this.getTarget().hasGlobalName("[补丁中的危险 API，例如 memcpy]") and
    inTarget(this.getEnclosingFunction())
  }

  /** 如需追踪源参数，可按 diff 指定的参数索引返回；无需求可移除本方法。 */
  Expr getDataArg() { result = this.getArgument([根据 diff 指定的索引]) }

  /** 长度或危险参数，必须对应补丁指出的实参（如 memcpy 的第 2/3 参数）。 */
  Expr getDangerArg() { result = this.getArgument([根据 diff 指定的索引]) }
}

// --- Source 定义 -------------------------------------------------------------

/**
 * 外部可控或缺乏边界约束的表达式。
 * ⚠️ 必须引用 diff/情报中的具体函数或变量（例如 exif_format_get_size、变量 s/len 等），禁止使用“任意输入”之类泛化符号。
 */
predicate isSourceExpr(Expr expr) {
  // 例：补丁强调的函数调用（如 exif_format_get_size 返回值参与 s 计算）
  exists(FunctionCall fc |
    fc.getTarget().hasGlobalName("[补丁中的 source 函数，如 exif_format_get_size]") and
    inTarget(fc.getEnclosingFunction()) and
    expr = fc
  )
  or
  // 例：补丁涉及的关键变量（如 s、len）
  exists(VariableAccess va |
    va.getTarget().getName() = "[补丁里的变量名，如 s]" and
    (
      va.getTarget() instanceof Parameter and
      inTarget(va.getTarget().(Parameter).getFunction())
      or
      va.getTarget() instanceof LocalVariable and
      inTarget(va.getTarget().(LocalVariable).getFunction())
    ) and
    expr = va
  )
}

// --- 数据流配置 -------------------------------------------------------------

module VulnConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node source) {
    exists(Expr e | isSourceExpr(e) and source.asExpr() = e)
  }

  predicate isSink(DataFlow::Node sink) {
    exists(VulnerableCall call |
      // ⚠️ 根据 diff/情报覆盖所有需要监控的参数，例如同时追踪缓冲区与长度参数
      sink.asExpr() = call.getDangerArg() or
      sink.asExpr() = call.getDataArg()
    )
  }

  /** 可选：字段别名 / 指针传递等特殊流转。 */
  predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
    // 字段访问传播：相同字段的访问传播 (例如: obj.field1 -> obj.field1)
    exists(FieldAccess fa1, FieldAccess fa2 |
      n1.asExpr() = fa1 and
      n2.asExpr() = fa2 and
      fa1.getTarget() = fa2.getTarget()
    )
    or
    // 指针解引用传播：相同操作数的解引用传播 (例如: *p -> *p)
    // ⚠️ 注意：必须使用 PointerDereferenceExpr 类型
    exists(PointerDereferenceExpr pde1, PointerDereferenceExpr pde2 |
      n1.asExpr() = pde1 and
      n2.asExpr() = pde2 and
      pde1.getOperand() = pde2.getOperand()
    )
    or
    // 数组元素访问传播：数组到其元素的传播 (例如: arr -> arr[i])
    // ⚠️ 注意：使用 ArrayExpr 类型，getArrayBase() 获取数组基址
    exists(ArrayExpr ae |
      n1.asExpr() = ae.getArrayBase() and
      n2.asExpr() = ae
    )
    or
    // 地址取操作传播：变量到其地址的传播 (例如: x -> &x)
    // ⚠️ 注意：使用 AddressOfExpr 类型，getOperand() 获取操作数
    exists(AddressOfExpr aoe |
      n1.asExpr() = aoe.getOperand() and
      n2.asExpr() = aoe
    )
  }
  
  /** 可选：净化器（如果不需要，可以省略或使用 none()） */
  additional predicate isSanitizer(DataFlow::Node node) {
    none()  // 如果不需要净化器，使用 none()
  }
}

module VulnFlow = TaintTracking::Global<VulnConfig>;
import VulnFlow::PathGraph

// --- 查询输出 ---------------------------------------------------------------

from VulnFlow::PathNode src, VulnFlow::PathNode snk, VulnerableCall call
where
  VulnFlow::flowPath(src, snk) and
  snk.getNode().asExpr() = call.getDangerArg()
select call,
  src, snk,
  "[一句话提醒：$@（Source 描述）→ " +
  call.getTarget().getName() +
  " 危险使用，示例修复 …]",
  src.getNode(), "[Source 标签]",
  snk.getNode(), "[Sink 标签]"
