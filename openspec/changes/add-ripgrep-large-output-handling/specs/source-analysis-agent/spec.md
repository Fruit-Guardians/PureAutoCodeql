## ADDED Requirements
### Requirement: Ripgrep大结果LLM反馈处理
系统 SHALL 在LLM服务的`_format_tool_output`函数中提供ripgrep工具输出超过2000行时的智能处理机制，仅影响LLM接收的反馈信息。

#### Scenario: 检测大结果
- **WHEN** ripgrep工具返回搜索结果给LLM
- **THEN** 系统在`_format_tool_output`函数中检测输出行数是否超过2000行

#### Scenario: 智能截断LLM反馈
- **WHEN** 检测到结果超过2000行
- **THEN** 在LLM反馈中保留前1000行和后1000行，中间用省略标记
- **AND** 为LLM提供截断统计信息"搜索结果共X行，已截断显示前1000行和后1000行"
- **AND** 建议LLM使用更精确的搜索参数来减少结果数量

#### Scenario: 保持小结果正常LLM处理
- **WHEN** ripgrep结果少于2000行
- **THEN** 按现有逻辑正常处理，不进行截断
- **AND** 终端输出显示保持不变

#### Scenario: 终端输出不受影响
- **WHEN** ripgrep工具执行任何搜索
- **THEN** `_print_detailed_tool_output`函数保持现有行为不变
- **AND** 终端用户看到的搜索结果完整显示，不受截断影响