"""
CodeQL generation prompt strategies with different strictness levels.

This module provides prompt variants for retry mechanism when dataflow queries return empty results.
Each retry level progressively relaxes source/sink constraints to increase the chance of finding paths.
"""


def get_codeql_generation_prompt_suffix(retry_count: int = 0) -> str:
    """
    根据重试次数返回不同严格程度的CodeQL生成提示词后缀。
    
    Args:
        retry_count: 当前重试次数 (0=首次生成, 1-3=重试)
    
    Returns:
        提示词后缀字符串，用于指导生成更宽松的source/sink定义
    """
    if retry_count == 0:
        # 标准prompt - 首次生成
        return """
## 生成要求
- 生成精确的数据流查询，准确定义source点和sink点
- source点应该是明确的用户输入来源
- **sink点定义原则（重要）**：
  - **必须优先定义Sink点分析报告中的sink点**
  - **注意**：
    - 如果代码段有多次调用同名方法，可以添加包名或其他条件进一步限制
    - Sink 点报告中的 sink 点是必需的，必须精确匹配报告中的调用位置
  - 对于其他场景：sink点应该是明确的危险操作点
- 确保查询逻辑严谨，避免误报
"""
    
    elif retry_count == 1:
        # 第一次重试 - 适度放宽
        return """
## 生成要求（放宽版本 - 重试1/3）
⚠️ 前一次查询未找到数据流路径，请适度放宽条件：

- **放宽source点定义**：
  - 除了直接的用户输入（如HTTP参数、文件读取），也考虑间接的数据来源
  - 包含可能被用户控制的配置项、环境变量等
  - 考虑通过多层传递的用户数据

- **放宽sink点定义**：
  - **必须包含Sink点分析报告中的sink点**
  - **三种方法精确定位 sink 点**（可以适当放宽某些条件）：
    1. `mc.getEnclosingCallable().hasName("xxx")` - 被调用的方法名（必需）
    2. `mc.getMethod().hasName("xxx")` - 当前方法调用名（必需）
    3. 文件路径匹配（可以放宽为目录级别匹配，如 `%/controller/%` 而不是具体文件名）
  - 可以考虑多个相关的调用点，使用 `or` 连接
  - 除了明显的危险操作，也包含潜在的风险点
  - 考虑间接调用的危险函数
  - 包含可能导致安全问题的辅助函数
  - **注意**：仍然禁止使用底层调用（如 `java.lang` 包）作为限制条件

- 保持数据流分析的基本逻辑，但降低匹配的严格程度
"""
    
    elif retry_count == 2:
        # 第二次重试 - 进一步放宽
        return """
## 生成要求（进一步放宽 - 重试2/3）
⚠️ 前两次查询均未找到路径，请进一步放宽条件：

- **更宽泛的source点**：
  - 包含所有可能的外部输入源
  - 考虑反射、动态加载等间接输入方式
  - 包含数据库查询结果、缓存数据等
  - 放宽对输入验证的要求

- **更宽泛的sink点**：
  - **必须包含Sink点分析报告中的sink点**
  - **进一步放宽定位条件**：
    - 方法名匹配可以使用模糊匹配（如包含特定关键词）
    - 文件路径可以只匹配目录或模块名
    - 可以考虑同一类型的多个方法调用
  - 使用 `or` 连接多个相关的调用点
  - 包含所有可能产生副作用的操作
  - 考虑间接的、多层调用的危险点
  - 包含日志记录、序列化等可能的风险点
  - 降低对sink点类型的限制

- 适当放宽数据流的污点传播规则
- 考虑更多的中间节点和传播路径
"""
    
    elif retry_count >= 3:
        # 第三次重试 - 最宽松策略
        return """
## 生成要求（最宽松策略 - 重试3/3）
⚠️ 这是最后一次重试，请使用最宽松的策略：

- **最大范围的source点**：
  - 包含任何可能的外部数据来源
  - 不限制输入的类型和来源
  - 包含所有参数、返回值、字段访问等
  - 考虑任何可能被外部影响的数据

- **最大范围的sink点**：
  - **必须包含Sink点分析报告中的sink点作为核心条件**
  - **最宽松的定位策略**：
    - 可以只使用方法名或文件路径中的一个条件
    - 可以使用非常宽泛的模式匹配
    - 考虑所有相关的方法调用和操作
  - 使用 `or` 连接所有可能相关的调用点
  - 包含所有可能产生影响的操作
  - 不限制操作的类型和危险程度
  - 包含所有方法调用、字段赋值等
  - 考虑任何可能产生副作用的代码
  - **注意**：在此阶段可以适当考虑底层 API，但仍需以报告中的 sink 点为主

- **最宽松的数据流规则**：
  - 放宽污点传播的限制
  - 包含更多的隐式数据流
  - 考虑控制流依赖
  - 降低路径可达性的要求

- 优先找到任何可能的数据流路径，即使可能包含误报
"""
    
    else:
        # 默认返回标准prompt
        return get_codeql_generation_prompt_suffix(0)


def get_retry_strategy_description(retry_count: int) -> str:
    """
    获取当前重试策略的简短描述。
    
    Args:
        retry_count: 当前重试次数
    
    Returns:
        策略描述字符串
    """
    strategies = {
        0: "标准策略",
        1: "放宽策略（重试1/3）",
        2: "进一步放宽（重试2/3）",
        3: "最宽松策略（重试3/3）",
    }
    return strategies.get(retry_count, strategies[0])
