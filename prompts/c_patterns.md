# C/C++ CodeQL 模式库 (Patterns & Helpers)

> 本文件包含常用的 Source/Sink 定义模式、AdditionalFlowStep 模式和 Helper 谓词实现。
> 基于 semmle.code.cpp.dataflow.new.* API。

---

## 1. 常用类型/方法速查

| 目的 | 推荐写法 | 说明 |
| --- | --- | --- |
| 表达式 | `Expr` | 所有表达式的基类 |
| 函数调用 | `FunctionCall` | `call.getTarget()` 获取函数，`call.getArgument(i)` 获取参数 |
| 变量访问 | `VariableAccess` | `va.getTarget()` 获取变量 |
| 字段访问 | `FieldAccess` | `fa.getTarget()` 获取字段，`fa.getQualifier()` 获取对象 |
| 指针解引用 | `PointerDereferenceExpr` | `*p`，`e.getOperand()` 获取指针 |
| 取地址 | `AddressOfExpr` | `&x`，`e.getOperand()` 获取变量 |
| 数组访问 | `ArrayExpr` | `a[i]`，`e.getArrayBase()` 获取数组，`e.getArrayOffset()` 获取索引 |
| 赋值 | `AssignExpr` | `a = b`，`e.getLValue()`，`e.getRValue()` |
| 加法/减法 | `AddExpr`, `SubExpr` | `e.getLeftOperand()`, `e.getRightOperand()` |
| 转换 | `Cast` | `(int)x` |
| 宏调用 | `MacroInvocation` | 检查宏扩展 |
| 路径节点 | `VulnFlow::PathNode` | **必须使用**模块别名下的 PathNode |
| 节点转换 | `node.asExpr()`, `node.asParameter()` | DataFlow::Node 转 AST |

---

## 2. Source 定义模式

### 模式A：函数参数（标准）
适用于：库的公开入口函数，参数不可信。
```ql
predicate isSource(DataFlow::Node source) {
  exists(Function f, Parameter p |
    f.getName() = "target_function" and
    p = f.getParameter(0) and // 第一个参数
    source.asParameter() = p
  )
}
```

### 模式B：外部数据读取函数 (Source Function)
适用于：`getenv`, `read`, `recv` 等函数的返回值或缓冲区参数。
```ql
predicate isSource(DataFlow::Node source) {
  exists(FunctionCall call |
    call.getTarget().getName() in ["getenv", "read_from_net"] and
    (
      source.asExpr() = call or // 返回值作为源
      source.asExpr() = call.getArgument(1) // 输出参数作为源
    )
  )
}
```

### 模式C：结构体字段 (Struct Field)
适用于：从特定结构体字段读取的数据（如网络包解析）。
```ql
predicate isSource(DataFlow::Node source) {
  exists(FieldAccess fa |
    fa.getTarget().getName() = "data" and
    fa.getTarget().getDeclaringType().getName() = "Packet" and
    source.asExpr() = fa
  )
}
```

### 模式D：宏扩展 (Macro Expansion)
适用于：数据来自特定宏的扩展。
```ql
predicate isSource(DataFlow::Node source) {
  exists(MacroInvocation mi |
    mi.getMacro().getName() = "READ_DATA" and
    source.asExpr() = mi.getExpr()
  )
}
```

---

## 3. Sink 定义模式

### 模式A：危险函数参数 (Argument Sink)
适用于：`memcpy`, `strcpy`, `system` 等。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(FunctionCall call |
    call.getTarget().getName() = "memcpy" and
    sink.asExpr() = call.getArgument(2) // size 参数
  )
}
```

### 模式B：数组索引 (Array Index)
适用于：数组越界检测。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(ArrayExpr ae |
    sink.asExpr() = ae.getArrayOffset()
  )
}
```

### 模式C：指针算术 (Pointer Arithmetic)
适用于：指针加减法导致的越界。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(PointerArithmeticExpr pae |
    sink.asExpr() = pae.getRightOperand() // 偏移量
  )
}
```

### 模式D：格式化字符串 (Format String)
适用于：`printf`, `sprintf`, `syslog` 等。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(FunctionCall call |
    call.getTarget().getName().matches("%printf") and
    sink.asExpr() = call.getArgument(0) // format 参数
  )
}
```

### 模式E：内存分配大小 (Allocation Size)
适用于：`malloc`, `calloc` 导致的整数溢出或过大分配。
```ql
predicate isSink(DataFlow::Node sink) {
  exists(FunctionCall call |
    call.getTarget().getName() in ["malloc", "calloc", "realloc"] and
    sink.asExpr() = call.getArgument(0)
  )
}
```

---

## 4. Additional Flow Step 模式 (CRITICAL for C/C++)

C/C++ 的污点追踪通常需要手动处理指针、结构体和算术运算的传播。

### 模式A：通用污点传播 (Comprehensive)
适用于：大多数情况，涵盖赋值、指针、数组、算术运算等。
```ql
predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
  // 1. 赋值传播 (AssignExpr)
  exists(AssignExpr ae |
    n1.asExpr() = ae.getRValue() and
    n2.asExpr() = ae.getLValue()
  )
  or
  // 2. 字段访问传播 (FieldAccess): obj -> obj.field
  exists(FieldAccess fa |
    n1.asExpr() = fa.getQualifier() and
    n2.asExpr() = fa
  )
  or
  // 3. 指针解引用 (PointerDereferenceExpr): p -> *p
  exists(PointerDereferenceExpr pde |
    n1.asExpr() = pde.getOperand() and
    n2.asExpr() = pde
  )
  or
  // 4. 取地址 (AddressOfExpr): x -> &x
  exists(AddressOfExpr aoe |
    n1.asExpr() = aoe.getOperand() and
    n2.asExpr() = aoe
  )
  or
  // 5. 数组访问 (ArrayExpr): a -> a[i]
  exists(ArrayExpr ae |
    n1.asExpr() = ae.getArrayBase() and
    n2.asExpr() = ae
  )
  or
  // 6. 算术运算传播 (Arithmetic): x -> x + 1
  exists(Operation op |
    (
      op instanceof AddExpr or
      op instanceof SubExpr or
      op instanceof MulExpr or
      op instanceof DivExpr or
      op instanceof BitwiseAndExpr or
      op instanceof BitwiseOrExpr or
      op instanceof BitwiseXorExpr or
      op instanceof LShiftExpr or
      op instanceof RShiftExpr
    ) and
    n1.asExpr() = op.getAnOperand() and
    n2.asExpr() = op
  )
  or
  // 7. 类型转换 (Cast): x -> (int)x
  exists(Cast cast |
    n1.asExpr() = cast.getExpr() and
    n2.asExpr() = cast
  )
}
```

### 模式B：通过特定函数传播
适用于：`my_memcpy(dest, src, len)` 等自定义工具函数。
```ql
predicate isAdditionalFlowStep(DataFlow::Node n1, DataFlow::Node n2) {
  exists(FunctionCall call |
    call.getTarget().getName() = "my_memcpy" and
    n1.asExpr() = call.getArgument(1) and // src
    n2.asExpr() = call.getArgument(0)     // dest
  )
}
```

---

## 5. Helper 谓词模式

### 5.1 限制目标文件
```ql
predicate inTargetFile(Element e) {
  exists(string path | path = e.getFile().getRelativePath() |
    path.matches("%src/target.c") or
    path.matches("%include/target.h")
  )
}
```

### 5.2 限制目标函数
```ql
predicate inTargetFunction(Element e) {
  exists(Function f |
    f = e.getEnclosingFunction() and
    f.getName() in ["vuln_func", "parse_data"]
  )
}
```

### 5.3 检查边界检查 (Guard Check)
适用于：检测变量是否在使用前被检查过（如 `if (len > MAX) return;`）。
```ql
predicate isGuarded(VariableAccess va) {
  exists(RelationalOperation op |
    op.getAnOperand() = va and
    // 这里只是示例，实际需要配合 ControlFlow 或 Guard 库使用
    op.getEnclosingStmt().getParent*() instanceof IfStmt
  )
}
```

