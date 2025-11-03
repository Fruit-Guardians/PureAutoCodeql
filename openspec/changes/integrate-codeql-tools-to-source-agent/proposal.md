## Why
当前的Java Source Analysis Agent使用硬编码的prompt进行源码分析，缺乏CodeQL查询的自动生成和执行能力。需要集成现有的CodeQL工具来提供更精确和自动化的源码分析功能。

## What Changes
- 将CodeQL Generator Tool集成到Java Source Analysis Agent中
- 将CodeQL Runner Tool集成到Java Source Analysis Agent中
- 删除现有的硬编码prompt，改为使用CodeQL工具进行动态分析
- 保持Agent的接口不变，确保向后兼容

## Impact
- Affected specs: java-source-analysis-agent
- Affected code: agents/java_source_analysis_agent.py, tools/codeql_generator_tool.py, tools/codeql_runner_tool.py
