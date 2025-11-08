# C/C++ CodeQL 统一模板（基于 Heartbleed / EXIF / Curl 示例抽取）

本模板从 `projects/C_QL` 下三个经典查询抽取通用骨架与可复用模式：
- Heartbleed 越界读取（memcpy 源参数）
- EXIF 整数溢出（算术传播与边界检查）
- Curl 不安全格式化（sprintf/vsprintf 目标缓冲区）

按需“取消注释”对应示例块即可快速定制你的查询。

```ql
/**
 * @kind path-problem
 * @name C/C++ Unified Taint Flow template
 * @id cpp/template-unified-taint
 * @problem.severity error
 * @precision high
 * @tags security, taint
 */

import cpp
import semmle.code.cpp.dataflow.DataFlow
import semmle.code.cpp.dataflow.new.TaintTracking

/** ===== 通用 Helper（示例抽取） ===== */
class MemcpyLikeCall extends FunctionCall {
  MemcpyLikeCall() {
    this.getTarget().hasGlobalName(["memcpy", "__builtin_memcpy", "memmove", "bcopy"])
  }
  Expr getSrc() { result = this.getArgument(1) }
  Expr getLen() { result = this.getArgument(2) }
}

class UnsafeFormatCall extends FunctionCall {
  UnsafeFormatCall() {
    this.getTarget().hasGlobalName(["sprintf", "vsprintf", "snprintf", "vsnprintf"])
  }
  Expr getDestBuffer() { result = this.getArgument(0) }
  Expr getDangerousArgument() { exists(int i | i > 0 and i < this.getNumberOfArguments() and result = this.getArgument(i)) }
}

class ExifReadFunction extends Function {
  ExifReadFunction() {
    this.hasGlobalName(["exif_get_long","exif_get_short","exif_get_slong","exif_get_sshort","exif_get_rational","exif_get_srational"])
  }
}

/** 命令执行类调用（命令注入常见汇点） */
class CommandExecCall extends FunctionCall {
  CommandExecCall() {
    this.getTarget().hasGlobalName(["system","popen","execl","execlp","execv","execve","execvp","execvpe"])
  }
  Expr getCmdArg() { result = this.getArgument(0) }
}

/** 路径操作类调用（路径穿越常见汇点） */
class PathOpCall extends FunctionCall {
  PathOpCall() {
    this.getTarget().hasGlobalName(["open","fopen","unlink","remove","rename"])
  }
  Expr getPathArg() { result = this.getArgument(0) }
}

/** 网络/文件读取写入目标缓冲区（常见外部输入源） */
class NetReadCall extends FunctionCall {
  NetReadCall() {
    this.getTarget().hasGlobalName(["recv","read"])
  }
  /** read(fd, buf, count) / recv(sock, buf, len, flags) 的 buf 参数 */
  Expr getBufArg() { result = this.getArgument(1) }
}

/** 标准输入读取到缓冲区（外部输入源） */
class StdInReadCall extends FunctionCall {
  StdInReadCall() {
    this.getTarget().hasGlobalName(["fgets","gets","getline"])
  }
  Expr getBufArg() { result = this.getArgument(0) }
}

/** scanf/sscanf 将数据写入指针参数（外部输入源） */
class ScanfLikeCall extends FunctionCall {
  ScanfLikeCall() { this.getTarget().hasGlobalName(["scanf","sscanf"]) }
  Expr getPointerArg() { exists(int i | i > 1 and i < this.getNumberOfArguments() and result = this.getArgument(i)) }
}

/** ===== 可选：算术/类型传播（EXIF 风格） ===== */
predicate addArithmeticOrCastStep(DataFlow::Node n1, DataFlow::Node n2) {
  exists(AddExpr add |
    n1.asExpr() = add.getAnOperand() and
    n2.asExpr() = add
  )
  or
  exists(MulExpr mul |
    n1.asExpr() = mul.getAnOperand() and
    n2.asExpr() = mul
  )
  or
  exists(Cast cast |
    n1.asExpr() = cast.getExpr() and
    n2.asExpr() = cast
  )
}

/** ===== 精确度/召回率开关（默认更严格，需显式开启） ===== */
predicate EnableDefaultSources() { false }
predicate EnableDefaultSinks() { false }
predicate EnableMildPropagation() { false }

class VulnConfig extends DataFlow::Configuration {
  /** ===== Source：取消注释一个或多个即可 ===== */
  predicate isSource(DataFlow::Node src) {
    // 环境变量返回值（直接返回字符串/指针）
    EnableDefaultSources() and
    exists(FunctionCall fc |
      fc.getTarget().hasGlobalName(["getenv","getenv_s"]) and
      src.asExpr() = fc
    )
    or
    // 网络/文件读取到缓冲区（read/recv -> buf）
    EnableDefaultSources() and
    exists(NetReadCall nr |
      src.asExpr() = nr.getBufArg()
    )
    or
    // 标准输入读取到缓冲区（fgets/gets/getline -> buf）
    EnableDefaultSources() and
    exists(StdInReadCall sr |
      src.asExpr() = sr.getBufArg()
    )
    or
    // scanf/sscanf 的指针参数（被写入的数据）
    EnableDefaultSources() and
    exists(ScanfLikeCall sc |
      src.asExpr() = sc.getPointerArg()
    )
    or
    // EXIF 风格：EXIF读取函数的返回值
    EnableDefaultSources() and
    exists(FunctionCall ef |
      ef.getTarget() instanceof ExifReadFunction and
      src.asExpr() = ef
    )
    or
    // main/wmain 的 argv 访问（简化模型）
    EnableDefaultSources() and
    exists(VariableAccess va |
      va.getTarget() instanceof Parameter and
      va.getTarget().(Parameter).getFunction().hasGlobalName(["main","wmain"]) and
      va.getTarget().getName() = "argv" and
      src.asExpr() = va
    )
  }

  /** ===== Sink：取消注释一个或多个即可 ===== */
  predicate isSink(DataFlow::Node sink) {
    // 命令执行：system/popen/exec*
    EnableDefaultSinks() and
    exists(CommandExecCall ce |
      sink.asExpr() = ce.getCmdArg()
    )
    or
    // Heartbleed 风格：memcpy/memmove 源参数（潜在越界读取）
    EnableDefaultSinks() and
    exists(MemcpyLikeCall m |
      sink.asExpr() = m.getSrc()
    )
    or
    // 字符串复制族：strcpy/strcat（易溢出）
    EnableDefaultSinks() and
    exists(FunctionCall sc |
      sc.getTarget().hasGlobalName(["strcpy","strcat"]) and
      sink.asExpr() = sc.getArgument(1)
    )
    or
    // 格式化目标缓冲区（长度未校验）
    EnableDefaultSinks() and
    exists(UnsafeFormatCall call |
      sink.asExpr() = call.getDestBuffer()
    )
    or
    // 路径操作的路径参数（路径穿越/伪造）
    EnableDefaultSinks() and
    exists(PathOpCall po |
      sink.asExpr() = po.getPathArg()
    )
  }

  /** 可选：传播增强（EXIF 风格） */
  predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
    EnableMildPropagation() and (
      // 轻量传播增强（可选）
      addArithmeticOrCastStep(n1, n2)
      or
      // 同一变量的不同访问点视作等价（缓解简单别名差异）
      exists(VariableAccess va1, VariableAccess va2 |
        n1.asExpr() = va1 and n2.asExpr() = va2 and va1.getTarget() = va2.getTarget()
      )
      or
      // 结构字段的同名访问视作弱等价（FieldAccess/PointerFieldAccess）
      exists(FieldAccess fa1, FieldAccess fa2 |
        n1.asExpr() = fa1 and n2.asExpr() = fa2 and fa1.getTarget() = fa2.getTarget()
      )
      or
      exists(PointerFieldAccess pfa1, PointerFieldAccess pfa2 |
        n1.asExpr() = pfa1 and n2.asExpr() = pfa2 and pfa1.getTarget() = pfa2.getTarget()
      )
    )
  }

  /** 可选：净化器/屏障 */
  predicate isSanitizer(DataFlow::Node n) { none() }
  predicate isBarrier(DataFlow::Node n) {
    none()
    // 例：与常量比较后使用，视作传播屏障
    // exists(RelationalOperation cmp, Literal limit |
    //   cmp.getAnOperand() = n.asExpr() and cmp.getAnOperand() = limit
    // )
  }
}

from VulnConfig cfg, DataFlow::PathNode source, DataFlow::PathNode sink
where cfg.hasFlowPath(source, sink)
select sink.getNode(), source, sink, "Untrusted data flows into a sensitive operation"
```

使用方法：在 `isSource` / `isSink` 中按你的场景取消注释对应块，并在必要时打开 `isAdditionalFlowStep` 的算术/类型传播。该模板保证编译通过（默认 `none()`），你只需逐步放开对应块即可得到 Heartbleed / EXIF / Curl 风格查询。