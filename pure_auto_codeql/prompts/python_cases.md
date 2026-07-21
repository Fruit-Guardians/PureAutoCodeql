# Python CodeQL 成功案例参考 (Success Cases)

> 本文件包含经过验证的 CVE 成功案例分析。当遇到复杂的污点追踪逻辑（如资源传播、字典读取、库内部闭环）时，请参考对应的案例模式。

---

## 1. 成功案例列表

### CVE-2024-7099：SQL注入
- **场景**：Web 应用中的 SQL 注入。
- **Source**：`DataFlow::ParameterNode` + 严格作用域限定。
- **Sink**：`execute_query_` 方法调用的第一个参数。
- **核心点**：严格的作用域检查 `source.getScope() = f`，防止误报。

### CVE-2025-54802：任意文件写入 (pyLoad)
- **场景**：路径遍历导致任意文件写入，涉及文件句柄传播。
- **Source**：`RemoteFlowSource` + 文件/函数限定。
- **Sink**：`open()` 的第一个参数（路径） 或 `fp.write()` 的接收者（`fp`）。
- **核心点**：使用 `isAdditionalFlowStep` 将 `open` 的参数流转给 `open` 的返回值，实现路径参数到文件对象的污染传播。这是检测 `f = open(tainted); f.write(...)` 的关键。

### CVE-2025-46725：Eval 注入 (Generic)
- **场景**：不安全的 `eval()` 使用，涉及多种调用方式（全局函数、属性方法）。
- **Source**：混合源 (`RemoteFlowSource` or `isLoad()` or `ParameterNode`)，用于最大化覆盖率。
- **Sink**：全局 `eval`/`exec` 和属性 `pandas.eval`, `DataFrame.eval`。
- **核心点**：使用 `attrReceiverIsLikelyEvalCarrier` 识别模块变量（ModuleVariableNode），覆盖 `pd.eval`, `pandas.eval` 等多种调用方式。

### CVE-2024-10940：任意文件读取 (Langchain)
- **场景**：从复杂的字典结构中读取路径参数，导致文件读取。
- **Source**：`kwargs.get('path')` - 字典特定 Key 读取。
- **Sink**：`open(..., 'rb')` - 带模式检查的 Open 调用。
- **核心点**：复杂的 `isAdditionalFlowStep` 贯穿多个辅助函数 (`image_to_data_url` -> `encode_image`)。使用 `call.getArg(0).asExpr().(StringLiteral).getText()` 精确匹配字典 Key。

### CVE-2022-22817：库内部任意代码执行 (PIL/ImageMath)
- **场景**：库函数参数直接传递给内部的 `eval`，完全在库内部发生（Intra-library）。
- **Source**：`ImageMath.eval(expression)` 的 `expression` 参数。
- **Sink**：同文件内的 `builtins.eval()` 或全局 `eval()`。
- **核心点**：
  - **Intra-library 分析**：源和汇聚点都在库文件内部，不依赖 RemoteFlowSource。
  - **正则匹配函数名**：使用 `getQualifiedName().regexpMatch(...)` 灵活匹配函数路径（如 `PIL.ImageMath.eval`）。
  - **严格文件限定**：使用 `regexpMatch` 匹配文件路径结尾。

