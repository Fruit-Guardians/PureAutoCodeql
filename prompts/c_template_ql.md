# C/C++ CodeQL 查询模板（通用数据流骨架）

本模板用于在 C/C++ 代码中基于污点传播定位“用户可控输入”到“敏感 API”的路径问题。保留可替换占位符与示例注释，便于快速按需定制。

---

## 一、固定骨架（严格遵守）

```ql
/**
 * @kind path-problem
 * @name <简明英文名称>
 * @description <详细描述>
 * @id cpp/<项目>-<漏洞类型>
 * @problem.severity <error|warning|recommendation>
 * @security-severity <0.0-10.0>
 * @precision <high|medium|low>
 * @tags security
 *       <更多标签（每行一个），例如 external/cwe/cwe-190>
 */

import cpp
import semmle.code.cpp.dataflow.new.DataFlow
import semmle.code.cpp.dataflow.new.TaintTracking

/** ========== Helper 谓词（可选）========== */
<HELPER-PREDICATES>

/** ========== 数据流配置 ========== */
module VulnConfig implements DataFlow::ConfigSig {
  /**
   * 定义 Source（用户可控输入）。
   * 典型来源：getenv/fgets/scanf/gets/read/recv 等；建议按需精确到参数位置或返回值。
   * 示例（按需取消注释并调整）：
   *
   *  exists(FunctionCall fc | fc.getTarget().hasName("getenv") and src.asExpr() = fc)
   *  or exists(FunctionCall fc | fc.getTarget().hasName("fgets") and src.asExpr() = fc.getArgument(0))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("scanf") and src.asExpr() = fc.getArgument(0))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("read") and src.asExpr() = fc.getArgument(1))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("recv") and src.asExpr() = fc.getArgument(1))
   */
  predicate isSource(DataFlow::Node src) {
    none()
  }

  /**
   * 定义 Sink（敏感使用点）。
   * 典型汇点：system/exec*/popen（命令执行）、strcpy/sprintf（危险字符串操作）、open/write（文件路径/写入）。
   * 示例（按需取消注释并调整）：
   *
   *  exists(FunctionCall fc | (fc.getTarget().hasName("system") or fc.getTarget().hasName("popen")) and sink.asExpr() = fc.getArgument(0))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("execv") and sink.asExpr() = fc.getArgument(1))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("execve") and sink.asExpr() = fc.getArgument(1))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("strcpy") and sink.asExpr() = fc.getArgument(1))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("sprintf") and sink.asExpr() = fc.getArgument(1))
   *  or exists(FunctionCall fc | fc.getTarget().hasName("open") and sink.asExpr() = fc.getArgument(0))
   */
  predicate isSink(DataFlow::Node sink) {
    none()
  }

  /**
   * 额外流步（可选）：用于指针别名、结构体字段传递、内存复制等场景增强污点传播。
   * 示例（按需取消注释并调整）：
   *
   *  // 结构体字段传递（示意）：
   *  // exists(Expr srcExpr, Expr dstExpr | src.asExpr() = srcExpr and dst.asExpr() = dstExpr and srcExpr.toString() = dstExpr.toString())
   *  // 内存复制类：memcpy/strncpy（根据具体模型设置参数位置）
   */
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
    none()
  }

  /**
   * 净化器（可选）：在此定义能有效移除污点的处理（如严格校验/归一化/白名单检查）。
   */
  predicate isSanitizer(DataFlow::Node node) {
    none()
  }

  /**
   * Barrier（可选）：若值在严格比较或白名单校验后使用，可作为传播屏障。
   */
  predicate isBarrier(DataFlow::Node node) {
    none()
  }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<诊断消息>",
  src, "source", sink, "sink"
```

---

## 二、占位符说明与填写建议
- `<HELPER-PREDICATES>`：放置复用谓词（如函数匹配、文件/路径过滤、类型/原型匹配）。可定义 `class`/`predicate`，例如 `UnsafeFormatCall`/`DangerousMemoryOperation`。
- `isSource`：按需求选择返回值或具体参数作为源；`getenv` 等返回值可用 `src.asExpr() = fc`。可结合文件/函数约束（`getRelativePath().regexpMatch(...)` / `getEnclosingFunction().hasName(...)`）。
- `isSink`：精确到具体参数位置（如 `system(cmd)` 的 `cmd` 是第 0 个参数；`strcpy(dst, src)` 的风险在第 2 参数；`open(path, ...)` 的路径在第 1 参数）。
- `isAdditionalFlowStep`：当默认传播不足时，加入别名复制、结构体字段等自定义步（见 Heartbleed/EXIF/Curl 示例）。
- `isSanitizer` / `isBarrier`：若存在明确净化/屏障逻辑（白名单、边界比较、严格长度检查），在此定义以减少误报。

## 三、常见场景映射（示例）
- 命令注入：`isSource(getenv/fgets/scanf/recv) → isSink(system/exec*/popen)`
- 路径遍历：`isSource(getenv/fgets/scanf) → isSink(open/fopen/remove/rename)`
- 缓冲区溢出：`isSource(getenv/fgets/scanf/recv) → isSink(strcpy/strcat/sprintf)`（按参数位置设置为源或汇）
- EXIF 整数溢出：`exif_get_* 返回值/变量 → memcpy/malloc size 参数`，并在 `isAdditionalFlowStep` 中添加 `Add/Mul/Cast` 传播，在 `isBarrier` 中定义常量比较屏障。
- Heartbleed 越界读取：`rrec.data → memcpy 源参数`，并加入结构体字段/数组地址传播与长度对比谓词（在 Helper 中定义）。

## 四、验证建议
- 先在目标文件或函数作用域上试跑，确保语法与数据流；再推广到完整数据库。
- 使用 `PathGraph` 输出 `src/sink`，便于复核路径与关键节点标签（select 中的 `$@` 可高亮参数）。
- 若传播不到位，优先补 `isAdditionalFlowStep`；若误报，补 `isSanitizer` 或 `isBarrier`；必要时加文件/函数约束精确到补丁范围。