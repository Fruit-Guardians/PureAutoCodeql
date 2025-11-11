## Why
当MCP工具返回大量数据时（如ripgrep搜索结果、大文件读取等），LLM可能因Token数量超限而无法有效处理或导致API调用失败。需要在工具输出返回给Agent之前进行Token数量限制，确保所有MCP工具的输出不超过8000 tokens。

## What Changes
- 在`MultiAgentAnalyzer.initialize`中为所有MCP工具添加Token限制包装器
- 使用tiktoken库计算工具输出的Token数量
- 当Token数超过8000时，智能截断内容（保留前40%和后60%）
- 在`pyproject.toml`中添加tiktoken依赖
- 适用于所有MCP工具（filesystem、ripgrep等）

## Impact
- Affected specs: source-analysis-agent
- Affected code:
  - `services/llm_service.py` (MultiAgentAnalyzer.initialize方法，添加工具包装逻辑)
  - `pyproject.toml` (添加tiktoken依赖)
