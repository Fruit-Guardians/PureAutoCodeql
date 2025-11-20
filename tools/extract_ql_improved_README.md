# extract_ql_improved.py 使用指南

## 概述

`extract_ql_improved.py` 是对原始 `extract_ql.py` 的改进版本，解决了原始版本中提取自定义谓词和导入语句失败的问题。

## 主要改进

1. **更强大的谓词提取**：
   - 支持带有修饰符的谓词（如 `private`、`cached`、`override`）
   - 支持跨多行的参数列表
   - 支持嵌套的大括号
   - 更精确的变量替换

2. **导入语句提取**：
   - 自动提取所有导入语句
   - 支持自定义导入语句

3. **多语言支持**：
   - 支持 Java、C++ 和 Python CodeQL 查询
   - 根据语言自动选择适当的导入语句

4. **错误处理和日志记录**：
   - 详细的错误信息
   - 警告日志记录

5. **更灵活的 API**：
   - 提供多个级别的函数，从简单的谓词提取到完整的断点查询生成

## 使用方法

### 基本用法

```python
from extract_ql_improved import extract_and_generate_breakpoint

# CodeQL 查询代码
codeql_code = """
import java
import semmle.code.java.dataflow.DataFlow

predicate isSource(DataFlow::Node source) {
  exists(Method m |
    m.hasName("getInput") and
    source.asParameter() = m.getAParameter()
  )
}

predicate isSink(DataFlow::Node sink) {
  exists(Method m |
    m.hasName("executeQuery") and
    sink.asExpr() = m.getAnArgument()
  )
}

predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(Method m |
    m.hasName("process") and
    src.asExpr() = m.getArgument(0) and
    dst.asExpr() = m.getResult()
  )
}
"""

# 提取谓词并生成断点查询
predicates, breakpoint_query = extract_and_generate_breakpoint(codeql_code, "java")

print("提取的谓词:", predicates)
print("生成的断点查询:", breakpoint_query)
```

### 高级用法

```python
from extract_ql_improved import extract_ql_predicate, Get_Breakpoint

# 只提取谓词
predicates = extract_ql_predicate(codeql_code)

# 手动生成断点查询
breakpoint_query = Get_Breakpoint(predicates, "java")
```

### 单独提取导入语句

```python
from extract_ql_improved import extract_imports

imports = extract_imports(codeql_code)
print("导入语句:", imports)
```

## API 参考

### `extract_ql_predicate(code: str) -> Dict[str, str]`

从CodeQL代码中提取指定的谓词定义。

**参数:**
- `code`: CodeQL代码字符串

**返回值:**
- 包含提取的谓词的字典，可能包含以下键：
  - `isSource`: isSource谓词体
  - `isSink`: isSink谓词体
  - `isAdditionalFlowStep`: isAdditionalFlowStep谓词体
  - `imports`: 导入语句

### `Get_Breakpoint(predicate: Dict[str, str], language: str = "java") -> str`

组装断点查询语句。

**参数:**
- `predicate`: 包含谓词定义的字典
- `language`: 编程语言，默认为"java"

**返回值:**
- 组装好的断点查询语句

### `extract_and_generate_breakpoint(code: str, language: str = "java") -> Tuple[Dict[str, str], str]`

从CodeQL代码中提取谓词并生成断点查询。

**参数:**
- `code`: CodeQL代码字符串
- `language`: 编程语言，默认为"java"

**返回值:**
- 元组：(提取的谓词字典, 生成的断点查询)

## 与原始版本的区别

| 功能 | 原始版本 | 改进版本 |
|------|---------|---------|
| 谓词提取 | 基本正则表达式 | 更强大的解析算法 |
| 导入语句提取 | 不支持 | 支持 |
| 多语言支持 | 仅Java | Java、C++、Python |
| 错误处理 | 基本处理 | 详细错误信息和日志 |
| 变量替换 | 简单替换 | 更精确的替换 |
| API设计 | 单一函数 | 多个级别的函数 |

## 常见问题

### Q: 为什么有时候仍然提取不到谓词？

A: 可能的原因：
1. 谓词名称不是 `isSource`、`isSink` 或 `isAdditionalFlowStep`
2. 谓词定义的语法不正确
3. 大括号不匹配

### Q: 如何处理自定义的谓词名称？

A: 可以使用 `extract_predicate_with_name` 函数提取自定义名称的谓词：

```python
from extract_ql_improved import extract_predicate_with_name

# 提取名为 "myCustomSource" 的谓词
source_body = extract_predicate_with_name(codeql_code, "myCustomSource")
```

### Q: 如何处理复杂的嵌套结构？

A: 改进版本使用大括号计数算法处理嵌套结构，可以处理大多数情况。但如果嵌套结构非常复杂，可能需要手动调整代码。

## 示例

查看 `extract_ql_example.py` 文件获取更多使用示例。