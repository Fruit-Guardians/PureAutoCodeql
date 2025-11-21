# C/C++ CodeQL 成功案例参考 (Success Cases)

> 本文件包含经过验证的 C/C++ CVE 成功案例分析。

---

## 1. 成功案例列表

### CVE-2014-0160: Heartbleed (OpenSSL)
- **场景**：TLS Heartbeat 扩展中的缓冲区过读（Buffer Over-read）。
- **Source**：TLS 记录结构体中的数据字段 `rrec.data`。
- **Sink**：`memcpy` 的源参数（读取越界）。
- **核心点**：
  - 使用 `FieldAccess` 定位 `rrec.data` 和 `rrec.length`。
  - 检查长度变量是否与记录的实际长度进行了比较 (`isLengthValidated`)。
  - 这是一个典型的 **Taint Tracking** 问题，污点是“未经验证的长度”。

### CVE-2022-26125: 整数下溢 (FRR)
- **场景**：ISIS 协议解析中的整数下溢导致缓冲区溢出。
- **Source**：从流中读取的 `stream_getc` 返回值。
- **Sink**：减法操作的减数。
- **核心点**：
  - 追踪两个流：一个是长度变量的赋值，一个是该变量的使用。
  - 漏洞发生在 `subtlv_len - 2` 操作中，当 `subtlv_len < 2` 时发生下溢。
  - 使用 `AssignExpr` 和 `SubExpr` 进行精细的 AST 匹配。

### CVE-2020-12762: 整数溢出 (json-c)
- **场景**：数组操作中的整数溢出导致越界写入。
- **Source**：`json_object_array_add` 的参数。
- **Sink**：`array_list_add` 调用的数组参数。
- **核心点**：
  - 检测代码中是否**缺少**特定的边界检查（如 `INT_MAX`）。
  - 使用 `not exists(...)` 模式来查找缺失的安全检查。
  - 使用 `isAdditionalFlowStep` 处理结构体字段的隐式传播 (`jso -> jso->o.c_array`)。

### CVE-2000-0973: 格式化字符串/缓冲区溢出 (Curl)
- **场景**：`vsprintf`/`sprintf` 缺乏边界检查。
- **Source**：多种来源，包括函数参数、结构体字段、环境变量等。
- **Sink**：格式化函数的参数。
- **核心点**：
  - **全面的 AdditionalFlowStep**：实现了极其实用的数据流传播规则，涵盖了赋值、字段访问、指针解引用、取地址、数组访问等。这是 C/C++ 污点分析成功的关键。
  - 使用 `matches("%...")` 进行模糊文件路径匹配。

---

## 2. 关键经验总结

1.  **AdditionalFlowStep 是关键**：C/C++ 不像 Java/Python 那样自动处理所有的数据流传播。特别是涉及指针运算、结构体字段访问、数组操作时，往往需要显式定义 `isAdditionalFlowStep`。参考 CVE-2000-0973 的实现。
2.  **类型转换 (Cast)**：在 C/C++ 中，类型转换非常常见，必须在 `isAdditionalFlowStep` 中处理 `Cast` 表达式。
3.  **宏 (Macro)**：如果漏洞涉及宏，使用 `MacroInvocation`。
4.  **AST 类型准确性**：区分 `PointerDereferenceExpr` (*p), `AddressOfExpr` (&p), `FieldAccess` (p->f), `ArrayExpr` (p[i]) 至关重要。

