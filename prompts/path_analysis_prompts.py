"""
路径分析提示词模板

用于 PathAnalysisAgent 分析源点到汇点的路径，识别 isAdditionalFlowStep 点。
"""

import json
from typing import Dict, Any, List


def build_path_analysis_prompt(
    language: str,
    path_data: Dict[str, Any],
    language_patterns: Dict[str, List[str]]
) -> str:
    """
    构建路径分析提示词。
    
    Args:
        language: 目标语言 (java, python, cpp)
        path_data: 路径数据，包含 source_function, sink_function, call_chain, transformations
        language_patterns: 特定语言的流步骤检测模式
        
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
    
    prompt = f"""# 路径分析任务

你是一个专业的代码安全分析专家，负责分析从源点到汇点的数据流路径，识别关键的 **isAdditionalFlowStep** 点。这些流步骤点是污点数据在传播过程中的关键转换点，对于生成准确的 CodeQL 查询至关重要。

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

请逐步追踪从源点到汇点的数据流路径，识别以下类型的 **isAdditionalFlowStep** 点：

### 1. 赋值操作 (Assignment)
- 变量赋值：污点数据从一个变量传递到另一个变量
- 对象字段赋值：污点数据存储到对象字段
- 数组/列表赋值：污点数据存储到数组或列表元素
- 方法链：通过方法调用传递污点数据

### 2. 反序列化操作 (Deserialization)
- 对象反序列化：将字节流转换为对象
- JSON/XML 解析：将文本格式转换为结构化数据
- 二进制解析：解析二进制协议或文件格式
- 自定义反序列化：使用自定义解析逻辑

### 3. 算术运算 (Arithmetic)
- 加法、减法、乘法、除法
- 位运算：AND, OR, XOR, 移位
- 可能导致溢出或下溢的运算
- 涉及污点数据的数学计算

### 4. 偏移操作 (Offset)
- 指针算术：指针加减操作
- 数组索引：使用污点数据作为索引
- 缓冲区偏移：计算缓冲区内的偏移量
- 内存访问：基于污点数据的内存读写

### 5. 类型转换 (Type Conversion)
- 显式类型转换：cast 操作
- 隐式类型转换：自动类型提升或降级
- 包装/拆箱：基本类型与包装类型之间的转换
- 字符串转换：数值与字符串之间的转换

## {language.upper()} 特定模式
{patterns_str}

## 输出格式

请以 JSON 格式输出你识别的所有流步骤点，格式如下：

```json
{{
  "flow_steps": [
    {{
      "type": "assignment|deserialization|arithmetic|offset|type_conversion",
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

1. **逐步追踪调用链**：按照调用链顺序，分析每个函数调用中的数据流转
2. **关注数据转换**：重点识别污点数据发生形式变化的位置
3. **考虑语言特性**：根据目标语言的特性，识别特定的流步骤模式
4. **评估置信度**：
   - **high**: 明确的流步骤，有清晰的代码证据
   - **medium**: 可能的流步骤，需要进一步验证
   - **low**: 不确定的流步骤，可能是误报
5. **提供足够上下文**：确保 pattern 字段包含足够的信息，以便生成 CodeQL 谓词

## 注意事项

- 只输出 JSON 格式的结果，不要包含其他解释性文字
- 确保每个流步骤都有完整的字段信息
- pattern 字段应该是可以直接用于 CodeQL 查询的模式
- 如果没有识别到任何流步骤，返回空数组：`{{"flow_steps": []}}`
- 优先识别高置信度的流步骤，避免过多的误报

开始分析！
"""
    
    return prompt


def build_batch_path_analysis_prompt(
    language: str,
    paths: List[Dict[str, Any]],
    language_patterns: Dict[str, List[str]]
) -> str:
    """
    构建批量路径分析提示词（用于一次性分析多条路径）。
    
    Args:
        language: 目标语言
        paths: 路径数据列表
        language_patterns: 特定语言的流步骤检测模式
        
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
    
    prompt = f"""# 批量路径分析任务

你是一个专业的代码安全分析专家，负责分析多条从源点到汇点的数据流路径，识别关键的 **isAdditionalFlowStep** 点。

## 目标语言
{language.upper()}

## 路径列表
{paths_str}

## 分析要求

对于每条路径，请识别以下类型的 **isAdditionalFlowStep** 点：

1. **赋值操作** (assignment)
2. **反序列化操作** (deserialization)
3. **算术运算** (arithmetic)
4. **偏移操作** (offset)
5. **类型转换** (type_conversion)

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
          "type": "assignment|deserialization|arithmetic|offset|type_conversion",
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
- 如果某条路径没有识别到流步骤，返回空数组
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
