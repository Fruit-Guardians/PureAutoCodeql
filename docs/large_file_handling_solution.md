# 大文件处理解决方案

## 问题背景

在分析大型源代码文件（如 `isis_tlvs.c`，43306 tokens）时，由于 Token 限制（10000 tokens），文件被截断导致关键代码丢失。

**典型场景**：
- 文件大小：43306 tokens
- 关键函数位置：中间区域（2757-3113行）
- 原有策略：前40% + 后60%，**中间关键代码丢失**

## 解决方案

采用**组合方案**：提示词改进 + 智能截断策略

### 1. 提示词改进

**文件**：`prompts/cpp_sink_prompt.py`

**改进内容**：
- 明确指导 Agent 对于大文件（>1000行）必须采用分段读取策略
- 步骤1：先使用 `ripgrep` 搜索关键函数名，获取行号
- 步骤2：根据行号使用 `head` 或 `tail` 参数分段读取
- 步骤3：避免一次性读取整个大文件

**关键指令**：
```
对于大文件（>1000行），必须采用分段读取策略！
步骤1：先使用ripgrep工具搜索关键函数名，获取函数所在的行号范围
步骤2：根据搜索结果，使用read_text_file工具的head或tail参数分段读取
```

### 2. 智能截断策略

**文件**：`services/llm_service.py` 中的 `_limit_tool_output_tokens` 函数

**改进内容**：
- 自动检测代码文件（通过函数定义、头文件等特征）
- 代码文件采用智能截断：**头部(15%) + 中间关键区域(70%) + 尾部(15%)**
- 非代码文件保持原有策略：前40% + 后60%

**截断比例对比**：

| 文件类型 | 头部 | 中间 | 尾部 |
|---------|------|------|------|
| 代码文件（大文件） | 15% | **70%** | 15% |
| 非代码文件 | 40% | - | 60% |

## 效果

### 之前的问题
```
文件: isis_tlvs.c (43306 tokens)
关键函数: unpack_tlv_router_cap (2757-3113行)
截断结果: 前40% + 后60% → 中间关键代码丢失 ❌
```

### 现在的改进
```
文件: isis_tlvs.c (43306 tokens)
关键函数: unpack_tlv_router_cap (2757-3113行)

Agent行为：
1. 使用ripgrep搜索 "unpack_tlv_router_cap" → 定位到2757行 ✅
2. 使用 head=3200 读取包含函数的区域 ✅
3. 即使被截断，智能截断保留70%中间区域 ✅
```

## 使用指南

### 对于 Agent 开发者

1. **大文件分析流程**：
   ```
   ripgrep搜索 → 获取行号 → 分段读取 → 分析
   ```

2. **提示词编写原则**：
   - 明确要求先搜索再读取
   - 禁止一次性读取大文件
   - 指导使用 head/tail 参数

### 对于系统维护者

1. **截断策略配置**：
   - 代码文件检测：通过正则表达式识别函数定义、头文件等
   - 截断比例：可在 `_limit_tool_output_tokens` 中调整

2. **扩展其他语言**：
   - 将相同的提示词改进应用到 `java_sink_prompt.py`、`python_sink_prompt.py`

## 技术细节

### 代码文件检测逻辑

```python
code_keywords = [
    r'\b(static\s+)?(int|void|char|struct|class|def|function)\s+\w+\s*\(',  # 函数定义
    r'#include\s*<',  # C/C++头文件
    r'^\s*//',  # 注释
    r'^\s*/\*',  # 多行注释
]
```

### 智能截断算法

```python
# 代码文件（大文件）
if is_code_file and len(tokens) > token_limit * 2:
    # 头部(15%) + 中间关键区域(70%) + 尾部(15%)
    first_token_count = int(token_limit * 0.15)
    middle_token_count = int(token_limit * 0.70)
    last_token_count = int(token_limit * 0.15)
    
    # 中间区域：从总长度的25%到75%之间
    middle_start = int(total_tokens * 0.25)
    middle_tokens = tokens[middle_start:middle_start + middle_token_count]
```

## 注意事项

1. **Token 限制**：当前限制为 10000 tokens，可根据需要调整
2. **分段读取**：`read_text_file` 不能同时使用 `head` 和 `tail`，需分两次读取
3. **搜索精度**：ripgrep 搜索时使用精确的函数名或关键词，避免误匹配

## 后续优化

- [ ] 扩展到 Java/Python 提示词
- [ ] 支持按函数边界智能截断（需要代码解析）
- [ ] 添加截断效果监控和统计

