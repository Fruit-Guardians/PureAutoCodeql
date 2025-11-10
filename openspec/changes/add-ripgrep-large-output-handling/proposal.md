## Why
当ripgrep MCP工具返回超过2000行的搜索结果时，LLM可能无法有效处理大量数据，需要在LLM服务的工具输出格式化函数中添加智能截断和反馈机制。

## What Changes
- 在`_format_tool_output`函数中添加ripgrep工具识别和输出大小检测
- 实现超过2000行结果的智能截断机制，保留前1000行和后1000行
- 为LLM提供截断统计信息和建议，不改变终端显示逻辑

## Impact
- Affected specs: source-analysis-agent
- Affected code: services/llm_service.py (_format_tool_output函数仅)
