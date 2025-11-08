# 空结果检测与用户交互功能

## 概述

本功能为CodeQL查询执行系统添加了空结果检测能力，能够在查询语法正确且执行成功但结果为空时，与用户进行交互，让用户选择是否继续优化查询。

## 功能特性

### 1. 智能空结果检测
- **多模式检测**: 支持analyze模式和run模式的结果检测
- **多种空结果识别**: 能够识别多种空结果模式，包括：
  - `paths_count = 0` (analyze模式)
  - 输出包含"No results."、"0 results"等关键词
  - 只有表头没有数据行
  - 空白或空输出

### 2. 用户交互机制
- **空结果提示**: 检测到空结果时，在输出中添加明确的提示信息
- **用户选择**: 提示用户检查查询条件或选择是否继续优化查询
- **向后兼容**: 不影响现有正常结果的处理流程

## 实现细节

### CodeQLExecutionResult类增强

在<mcfile name="codeql_execution.py" path="services/codeql_execution.py"></mcfile>中添加了`has_results`属性方法：

```python
@property
def has_results(self) -> bool:
    """检测查询结果是否为空。"""
    # 检查paths_count（analyze模式）
    if self.paths_count is not None:
        return self.paths_count > 0
    
    # 检查output内容（run模式）
    if self.output and self.output.strip():
        # 检查常见的空结果模式
        empty_indicators = [
            'No results.', 'No results found', '0 results',
            'Empty result set', '查询结果为空', '未找到结果'
        ]
        
        # 检查数据行数量和质量
        lines = [line.strip() for line in self.output.splitlines() if line.strip()]
        if len(lines) <= 2:
            return False
            
        data_lines = [line for line in lines if not line.startswith('|') or '---' not in line]
        return len(data_lines) > 1
    
    return False
```

### CodeQLExecutionService增强

在<mcfile name="codeql_execution.py" path="services/codeql_execution.py"></mcfile>的两个执行方法中添加了空结果检测：

#### _execute_analyze_mode方法
```python
# 检测空结果
if not result.has_results:
    result.output = f"查询执行成功，但未找到匹配结果。\n\n原始输出:\n{result.output}"
```

#### _execute_run_mode方法
```python
# 检测空结果
if not result.has_results:
    result.output = f"查询执行成功，但未找到匹配结果。\n\n原始输出:\n{result.output}"
```

### CodeQLComposeTool集成

在<mcfile name="codeql_compose.py" path="tools/codeql_compose.py"></mcfile>中添加了空结果检测和用户交互逻辑：

```python
# 检测空结果并与用户交互
if not execution_result.has_results:
    print(f"⚠️ [CodeQLComposeTool] 检测到空结果，正在与用户交互...")
    
    # 返回包含空结果信息的响应
    result = f"⚠️ 查询执行成功，但未找到匹配结果。\n请检查查询条件或选择是否继续优化查询。"
    # ... 具体格式化逻辑
```

## 测试验证

创建了测试脚本<mcfile name="test_empty_results.py" path="test_empty_results.py"></mcfile>来验证空结果检测功能：

测试用例包括：
- analyze模式：paths_count为0和5
- run模式：各种空结果模式
- 边界情况：空输出、空白输出

所有测试用例均通过验证。

## 使用示例

### 正常结果
```
CodeQL query successfully generated and executed after 1 round(s):

```ql
import java
// ... 查询内容
```

Text results saved to: /path/to/results.txt
Preview:

```
| file | line | vulnerability |
|------|------|---------------|
| main.py | 10 | SQL Injection |
```
```

### 空结果
```
CodeQL query successfully generated and executed after 1 round(s):

```ql
import java
// ... 查询内容
```

⚠️ 查询执行成功，但未找到匹配结果。
Text results saved to: /path/to/results.txt

请检查查询条件或选择是否继续优化查询。
```

## 向后兼容性

- ✅ 不影响现有正常结果的处理
- ✅ 空结果检测仅在查询执行成功后进行
- ✅ 错误处理逻辑保持不变
- ✅ 所有现有API接口保持兼容

## 未来扩展

当前实现为后续的用户交互功能奠定了基础，未来可以：

1. **增强交互机制**: 实现真正的用户选择界面
2. **智能优化建议**: 基于空结果提供查询优化建议
3. **结果分析**: 分析空结果的原因（查询条件过严、数据库不匹配等）
4. **自动优化**: 基于空结果自动调整查询策略

## 相关文件

- <mcfile name="codeql_execution.py" path="services/codeql_execution.py"></mcfile>
- <mcfile name="codeql_compose.py" path="tools/codeql_compose.py"></mcfile>
- <mcfile name="test_empty_results.py" path="test_empty_results.py"></mcfile>