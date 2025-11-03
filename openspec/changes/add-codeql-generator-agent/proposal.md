# Add CodeQL Generator Agent

## Why
当前系统缺少一个能够根据用户需求自动生成 CodeQL 查询代码的 Agent。用户需要手动编写 CodeQL 查询来分析代码中的 source 点、特定函数调用等，这个过程耗时且需要专业知识。通过创建一个专门的 CodeQL 代码生成 Agent，可以降低使用门槛，提高分析效率。

## What Changes
- 新增 `codeql_generator_agent.py` 到 `agents/` 目录
- Agent 接收自然语言描述的 CodeQL 查询需求作为输入
- Agent 输出格式化的 CodeQL 代码，用 `<codeql></codeql>` 标签包裹
- 支持多种常见查询场景：查询 source 点、查询特定函数、查询数据流路径等
- 遵循项目代码风格：简洁高效，仅在函数上添加注释

## Impact
- 影响的 specs: 新增 `codeql-generator-agent` capability
- 影响的代码: 
  - 新增 `agents/codeql_generator_agent.py`
  - 可能需要更新 `agents/__init__.py` 以导出新 agent
  - 可能需要新增主入口文件或更新现有入口以调用该 agent

