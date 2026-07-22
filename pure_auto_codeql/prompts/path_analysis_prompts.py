"""
路径分析提示词模板

用于 PathAnalysisAgent 分析源点到汇点的路径，识别 isAdditionalFlowStep 点。
"""

from typing import Any, Dict, List


def build_path_analysis_prompt(
    language: str,
    path_data: Dict[str, Any],
    language_patterns: Dict[str, List[str]],
    source_root: str = ""
) -> str:
    """
    构建路径分析提示词。

    Args:
        language: 目标语言 (java, python, cpp)
        path_data: 路径数据，包含 source_function, sink_function, call_chain, transformations
        language_patterns: 特定语言的流步骤检测模式
        source_root: 源码根目录绝对路径

    Returns:
        str: 完整的提示词
    """

    source_func = path_data.get("source_function", {})
    sink_func = path_data.get("sink_function", {})
    call_chain = path_data.get("call_chain", [])
    transformations = path_data.get("transformations", [])

    # 格式化调用链
    call_chain_str = "\n".join([
        f"  {i+1}. {call.get('function', 'unknown')} at {call.get('file_path', 'unknown')}:{call.get('line_number', '?')}"
        for i, call in enumerate(call_chain)
    ])

    # 格式化转换点
    transformations_str = "\n".join([
        f"  - {t.get('type', 'unknown')}: {t.get('description', 'N/A')}"
        for t in transformations
    ]) if transformations else "  (无已知转换点)"

    # 格式化语言特定模式
    patterns_str = ""
    for step_type, patterns in language_patterns.items():
        patterns_str += f"\n  **{step_type}**: {', '.join(patterns)}"

    # 注册项目的指令
    register_instruction = ""
    if source_root:
        register_instruction = f"""
在开始分析之前，请务必使用 `register_project_tool` 注册项目（如果尚未注册），配置如下：
- `path`: "{source_root}" (必须使用此绝对路径)
- `name`: "CURRENT_PROJECT"
- `description`: "Current analysis target"

后续使用 Tree-sitter 工具时，`project` 参数请务必使用 "CURRENT_PROJECT"。
"""

    prompt = f"""# 路径分析任务

你是一个专业的代码安全分析专家，负责分析从源点到汇点的数据流路径，识别关键的 **isAdditionalFlowStep** 点。这些流步骤点是污点数据在传播过程中的关键转换点，对于生成准确的 CodeQL 查询至关重要。
{register_instruction}
**重要提示：CodeQL 的 `TaintTracking` 库已经默认包含了基础的污点传播规则（如变量赋值、标准算术运算、函数参数传递）。请不要重复定义这些默认支持的步骤，否则会导致查询逻辑冗余甚至断流。**

## 目标语言
{language.upper()}

## 路径信息

### 源点 (Source)
- **函数名**: {source_func.get('name', 'unknown')}
- **文件路径**: {source_func.get('file_path', 'unknown')}
- **行号**: {source_func.get('line_number', '?')}
- **描述**: {source_func.get('description', 'N/A')}

### 汇点 (Sink)
- **函数名**: {sink_func.get('name', 'unknown')}
- **文件路径**: {sink_func.get('file_path', 'unknown')}
- **行号**: {sink_func.get('line_number', '?')}
- **描述**: {sink_func.get('description', 'N/A')}

### 调用链 (Call Chain)
{call_chain_str}

### 已知转换点 (Transformations)
{transformations_str}

## 分析要求

请逐步追踪从源点到汇点的数据流路径，仅识别 CodeQL 可能无法自动推导的 **非标准** 传播步骤：

### 1. 复杂的数据结构操作 (Complex Data Operations)
- 自定义的容器操作（如从自定义 List/Map 中取值）
- 复杂的对象字段访问（如果 CodeQL 无法推导）
- **不要** 报告简单的变量赋值或 getter/setter 调用

### 2. 反序列化与解析 (Deserialization & Parsing)
- 将字节流转换为对象的点
- JSON/XML/Protocol Buffers 解析
- 自定义协议解码逻辑
- **这是一个重点关注区域**

### 3. 复杂的指针与内存操作 (Complex Pointer/Memory Ops) - C/C++ 特有
- 复杂的指针算术运算（非简单的数组索引）
- `memcpy`, `memmove` 等内存操作（如果 CodeQL 未覆盖）
- 自定义的内存拷贝循环

### 4. 隐式或特殊的类型转换 (Implicit/Special Type Conversion)
- 涉及数据截断或符号扩展的转换
- 字符串与字节数组之间的非标准转换
- **不要** 报告标准的显式类型转换（cast）

### 5. 算术运算 (Arithmetic)
- **仅报告** 可能导致溢出/下溢并因此产生安全问题的运算
- **不要** 报告标准的数据处理运算（如 `a + b`），CodeQL 会自动传播
- **不要** 报告指针解引用 (`*p`) 和取地址 (`&p`) 操作，CodeQL 已内置指针分析和别名分析

## {language.upper()} 特定模式
{patterns_str}

## 输出格式

请以 JSON 格式输出你识别的所有流步骤点，格式如下：

```json
{{
  "flow_steps": [
    {{
      "type": "custom_container|deserialization|complex_memory|special_conversion|overflow_arithmetic",
      "description": "流步骤的详细描述，包括具体的操作和上下文",
      "location": "文件路径:行号",
      "pattern": "用于 CodeQL 匹配的代码模式或表达式",
      "confidence": "high|medium|low",
      "context": {{
        "from_variable": "源变量名",
        "to_variable": "目标变量名",
        "operation": "具体操作",
        "additional_info": "其他相关信息"
      }}
    }}
  ]
}}
```

## 分析指南

1. **默认支持原则**：假设 CodeQL 能够处理所有的标准赋值、函数调用和基本算术运算。只有当你确信 CodeQL 会丢失污点时才报告。
2. **关注断点**：思考"污点在这里是否会因为复杂的逻辑而丢失？"。
3. **提供准确 Pattern**：生成的 pattern 必须能够准确匹配代码中的 AST 结构，但不要过于具体以至于破坏泛化性。
4. **评估置信度**：
   - **high**: 确信 CodeQL 无法自动传播，且必须手动定义。
   - **medium**: 怀疑 CodeQL 可能无法传播。
   - **low**: 不确定的流步骤，可能是误报。

## 注意事项

- 只输出 JSON 格式的结果，不要包含其他解释性文字
- 确保每个流步骤都有完整的字段信息
- pattern 字段应该是可以直接用于 CodeQL 查询的模式
- 如果没有识别到任何**非标准**流步骤，返回空数组：`{{"flow_steps": []}}`

开始分析！
"""

    return prompt


def build_batch_path_analysis_prompt(
    language: str,
    paths: List[Dict[str, Any]],
    language_patterns: Dict[str, List[str]],
    source_root: str = ""
) -> str:
    """
    构建批量路径分析提示词（用于一次性分析多条路径）。

    Args:
        language: 目标语言
        paths: 路径数据列表
        language_patterns: 特定语言的流步骤检测模式
        source_root: 源码根目录绝对路径

    Returns:
        str: 完整的提示词
    """

    # 格式化所有路径
    paths_str = ""
    for i, path_data in enumerate(paths):
        source_func = path_data.get("source_function", {})
        sink_func = path_data.get("sink_function", {})
        call_chain = path_data.get("call_chain", [])

        paths_str += f"\n### 路径 {i+1}\n"
        paths_str += f"- **源点**: {source_func.get('name', 'unknown')} at {source_func.get('file_path', 'unknown')}:{source_func.get('line_number', '?')}\n"
        paths_str += f"- **汇点**: {sink_func.get('name', 'unknown')} at {sink_func.get('file_path', 'unknown')}:{sink_func.get('line_number', '?')}\n"
        paths_str += f"- **调用链长度**: {len(call_chain)}\n"

    # 格式化语言特定模式
    patterns_str = ""
    for step_type, patterns in language_patterns.items():
        patterns_str += f"\n  **{step_type}**: {', '.join(patterns)}"

    # 注册项目的指令
    register_instruction = ""
    if source_root:
        register_instruction = f"""
在开始分析之前，请务必使用 `register_project_tool` 注册项目（如果尚未注册），配置如下：
- `path`: "{source_root}" (必须使用此绝对路径)
- `name`: "CURRENT_PROJECT"
- `description`: "Current analysis target"

后续使用 Tree-sitter 工具时，`project` 参数请务必使用 "CURRENT_PROJECT"。
"""

    prompt = f"""# 批量路径分析任务

你是一个专业的代码安全分析专家，负责分析多条从源点到汇点的数据流路径，识别关键的 **isAdditionalFlowStep** 点。
{register_instruction}
**重要提示：CodeQL 的 `TaintTracking` 库已经默认包含了基础的污点传播规则（如变量赋值、标准算术运算、函数参数传递）。请不要重复定义这些默认支持的步骤，否则会导致查询逻辑冗余甚至断流。**

## 目标语言
{language.upper()}

## 路径列表
{paths_str}

## 分析要求

对于每条路径，请识别以下类型的 **非标准 isAdditionalFlowStep** 点：

1. **复杂的数据结构操作** (custom_container)
   - 自定义的容器操作
   - 复杂的对象字段访问

2. **反序列化与解析** (deserialization)
   - 字节流转对象
   - 协议解析

3. **复杂的指针与内存操作** (complex_memory) - C/C++ 特有
   - 复杂的指针运算
   - 自定义的内存拷贝

4. **隐式或特殊的类型转换** (special_conversion)
   - 截断、扩展等非标准转换

5. **特定的算术运算** (overflow_arithmetic)
   - 仅限于可能导致安全问题的特殊运算（如溢出）

**不要** 报告以下内容：
- 简单的赋值 (`a = b`)
- 标准算术运算 (`a + b`)
- 函数传参和返回
- 显式类型转换 (`(int)a`)
- 指针解引用 (`*p`) 和取地址 (`&p`)

## {language.upper()} 特定模式
{patterns_str}

## 输出格式

请以 JSON 格式输出所有路径的流步骤分析结果：

```json
{{
  "paths": [
    {{
      "path_index": 0,
      "source_function": "源点函数名",
      "sink_function": "汇点函数名",
      "flow_steps": [
        {{
          "type": "custom_container|deserialization|complex_memory|special_conversion|overflow_arithmetic",
          "description": "流步骤的详细描述",
          "location": "文件路径:行号",
          "pattern": "用于 CodeQL 匹配的代码模式",
          "confidence": "high|medium|low",
          "context": {{
            "from_variable": "源变量名",
            "to_variable": "目标变量名",
            "operation": "具体操作"
          }}
        }}
      ]
    }}
  ]
}}
```

## 注意事项

- 只输出 JSON 格式的结果
- 确保 path_index 与输入路径列表的索引对应
- 如果某条路径没有识别到**非标准**流步骤，返回空数组
- 优先识别高置信度的流步骤

开始分析！
"""

    return prompt


# 用于测试的示例路径数据
EXAMPLE_PATH_DATA = {
    "source_function": {
        "name": "getParameter",
        "file_path": "src/main/java/com/example/Controller.java",
        "line_number": 42,
        "description": "HTTP request parameter input"
    },
    "sink_function": {
        "name": "executeQuery",
        "file_path": "src/main/java/com/example/Database.java",
        "line_number": 156,
        "description": "SQL query execution"
    },
    "call_chain": [
        {
            "function": "getParameter",
            "file_path": "src/main/java/com/example/Controller.java",
            "line_number": 42
        },
        {
            "function": "processInput",
            "file_path": "src/main/java/com/example/Service.java",
            "line_number": 78
        },
        {
            "function": "buildQuery",
            "file_path": "src/main/java/com/example/QueryBuilder.java",
            "line_number": 123
        },
        {
            "function": "executeQuery",
            "file_path": "src/main/java/com/example/Database.java",
            "line_number": 156
        }
    ],
    "transformations": [
        {
            "type": "assignment",
            "description": "Assign parameter to local variable"
        },
        {
            "type": "string_concatenation",
            "description": "Concatenate user input into SQL query"
        }
    ]
}
