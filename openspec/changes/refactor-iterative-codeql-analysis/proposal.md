## Why
当前的Java Source Analysis Agent已经集成了CodeQL工具，但需要优化其使用方式。Agent应该能够自主地使用codeql_generator和codeql_runner工具来创建和执行QL查询，而不是依赖外部调用。

## What Changes
- 优化Java Source Analysis Agent的CodeQL工具集成
- 让Agent自主调用codeql_generator创建查询
- 让Agent自主调用codeql_runner执行查询
- 简化分析流程，提高工具使用的自主性
- 保持现有的输出格式和接口不变

## Impact
- 受影响规范：source-analysis-agent
- 受影响代码：agents/java_source_analysis_agent.py
- 改进工具集成：让Agent更好地自主使用CodeQL工具进行分析
