# Python源码分析实战示例

## 场景：CodeQL查询返回空结果

### CodeQL查询结果（空）
```
CodeQL Results:
从source到sink没有找到数据流路径。

Source候选: projects/CVE-2023-1234/source_code/app.py:10 - input()
Sink候选: projects/CVE-2023-1234/source_code/app.py:25 - eval()
```

---

## 正确的LLM分析流程

### 步骤1：读取源文件

**Action**: 使用server-filesystem读取 `projects/CVE-2023-1234/source_code/app.py`

**源文件内容**:
```python
# app.py
import json

def get_user_input():
    """Source点：用户输入"""
    user_data = input("Enter JSON data: ")  # 第10行
    return user_data

def parse_data(json_string):
    """中间转换：JSON解析"""
    parsed = json.loads(json_string)  # 第15行
    return parsed

def execute_command(data_dict):
    """Sink点：危险执行"""
    if 'command' in data_dict:
        eval(data_dict['command'])  # 第25行

def main():
    raw_input = get_user_input()     # 第10行的返回值
    parsed_input = parse_data(raw_input)  # 第15行 json.loads
    execute_command(parsed_input)    # 第25行 eval
```

### 步骤2：分析数据流路径

**LLM思考过程**:
```
1. Source: get_user_input() 返回 user_data (str类型)
   ↓
2. 传递给: parse_data(json_string)
   ↓
3. json.loads(json_string) → 返回 dict 类型 ← 【断流点！】
   ↓
4. 传递给: execute_command(data_dict)
   ↓
5. Sink: eval(data_dict['command'])

问题：CodeQL无法识别 json.loads 的数据流传递！
```

### 步骤3：识别断流点

**断流点分析**:
```json
{
  "breakpoints": [
    {
      "id": "breakpoint_1",
      "file_path": "app.py",
      "line_number": "15",
      "breakpoint_type": "函数调用 - JSON解析",
      "breakpoint_reason": "json.loads()将字符串转换为字典，CodeQL未建模此数据流",
      "source_context": "parsed = json.loads(json_string)",
      "data_flow_before": "json_string (str) 来自 get_user_input()",
      "data_flow_after": "parsed (dict) 传递给 execute_command()",
      "connection_required": true,
      "importance": "high",
      "python_specific_info": {
        "is_decorator": false,
        "is_magic_method": false,
        "is_dynamic_attr": false,
        "involved_types": ["str", "dict"],
        "library_function": "json.loads"
      }
    }
  ],
  "analysis_summary": {
    "total_breakpoints": "1",
    "main_flow_path": "input() → get_user_input() → parse_data() → json.loads() → execute_command() → eval()",
    "connection_complexity": "低",
    "recommended_approach": "为json.loads添加isAdditionalFlowStep条件",
    "selection_reason": "json.loads是唯一的数据转换点，连接此处即可完成数据流",
    "python_challenges": "标准库函数json.loads未被CodeQL默认建模"
  }
}
```

### 步骤4：生成修复条件

**isAdditionalFlowStep条件**:
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // 连接json.loads的输入和输出
  exists(Call call |
    call.getFunc().(Attribute).getName() = "loads" and
    call.getFunc().(Attribute).getObject().(Name).getId() = "json" and
    src.asExpr() = call.getArg(0) and  // json_string参数
    dst.asExpr() = call                 // 返回值parsed
  )
}
```

---

## 关键要点

### ✅ 正确做法
1. **主动读取源文件** - 不要只依赖CodeQL结果
2. **完整理解数据流** - 从Source→中间→Sink的完整路径
3. **识别具体函数/方法** - 如`json.loads`、`str.decode`等
4. **基于源码生成精确的修复条件** - 不要猜测

### ❌ 错误做法
1. 不读源文件，只依赖CodeQL结果的抽象信息
2. 只看断流点周围几行代码，不看完整函数定义
3. 猜测数据流路径，而不是从源码中确认
4. 生成过于宽泛的条件（如匹配所有函数调用）

---

## 其他常见断流模式

### 模式1：装饰器
```python
@app.route('/api')
def handler():
    data = request.get_json()  # Source
    return process(data)       # Sink

# 断流点：@app.route装饰器改变了函数调用路径
```

### 模式2：动态属性访问
```python
def process(obj, attr_name):
    value = getattr(obj, attr_name)  # 断流点
    return execute(value)

# 断流点：getattr动态获取属性
```

### 模式3：列表推导
```python
def transform(items):
    results = [process(x) for x in items]  # 断流点
    return results

# 断流点：列表推导中的元素传递
```

---

## 实战建议

1. **优先读取Source和Sink所在的文件** - 这是最重要的
2. **查找中间的所有函数调用** - 每个函数都可能是断流点
3. **关注标准库和第三方库函数** - 如json、pickle、requests等
4. **验证数据类型的变化** - str→dict、bytes→str等转换
5. **记录完整的调用链** - 便于生成准确的修复条件

**记住**：源码是真相的唯一来源！

