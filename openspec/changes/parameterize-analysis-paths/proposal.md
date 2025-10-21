## Why
当前代码中存在多处硬编码路径和参数，降低了代码的灵活性和可维护性：

1. **硬编码的源码根目录**：
   - `java_source_analysis_agent.py` 第25行：`self.source_root = "h5-vsan-service.jar_Decompiler.com"`
   - `java_sink_path_agent.py` 第24行：`self.source_root = "h5-vsan-service.jar_Decompiler.com"`

2. **硬编码的分析参数**：
   - `Analyze.py` 第114-115行：`run_multi_agent_analysis` 函数的默认参数固定为 `CVE-2021-21985.json` 和 `CVE-2021-21985.diff`

3. **缺乏参数传递机制**：
   - `main()` 函数无法接收外部参数，只能使用默认值
   - 用户无法灵活指定不同的分析目标和配置

这些硬编码限制了工具的通用性，使其只能分析特定的 CVE-2021-21985 案例。

## What Changes

### 1. 参数化 Agent 构造函数
- 修改 `JavaSourceAnalysisAgent.__init__()` 方法，接收 `source_root` 参数
- 修改 `JavaPathAnalysisAgent.__init__()` 方法，接收 `source_root` 参数
- 保持向后兼容，提供默认值

### 2. 参数化主分析函数
- 修改 `run_multi_agent_analysis()` 函数，增加 `source_root` 参数
- 将 `source_root` 参数传递给各个 Agent
- 保持现有的 `json_path` 和 `diff_path` 参数

### 3. 简化 main() 函数参数设置
- 修改 `main()` 函数，直接在函数内部设置参数变量
- 方便调试时快速修改参数值，无需命令行参数解析
- 参数包括：
  - 源码根目录路径
  - CVE JSON 文件路径
  - Diff 文件路径

### 4. 简化的参数传递链路
```
main() [直接设置参数] -> run_multi_agent_analysis() -> Agent.__init__()
```

## Impact

### Affected Files
- `agents/java_source_analysis_agent.py`：修改构造函数签名
- `agents/java_sink_path_agent.py`：修改构造函数签名  
- `Analyze.py`：修改 `run_multi_agent_analysis()` 和 `main()` 函数

### Benefits
- **调试便利性**：可以直接在main函数中修改参数，快速测试不同配置
- **可维护性改善**：消除硬编码，集中参数管理
- **向后兼容**：现有调用方式仍然有效
- **简化实现**：避免复杂的命令行参数解析逻辑

### Risks
- 需要仔细处理参数传递，确保所有 Agent 都能正确接收参数
- 需要验证默认值的正确性，避免破坏现有功能

## Dependencies
- 无新的外部依赖
- 依赖现有的 Agent 架构和工具函数
- 需要保持与现有 `utils.java.find_path_from_java_file` 函数的兼容性

## Implementation Notes
- 优先保证向后兼容性
- 参数验证：检查路径是否存在，文件是否可读
- 错误处理：提供清晰的错误信息当参数无效时
- 调试友好：参数直接在main函数中设置，便于快速修改和测试