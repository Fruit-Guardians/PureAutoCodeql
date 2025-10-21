# 添加 LangChain 工具集成

## Why

当前系统已有 CodeQLGeneratorAgent 用于生成 CodeQL 语句，以及 utils/codeql.py 用于执行 CodeQL 查询，但缺少 LangChain 工具层的封装。为了使这些功能能够被 LangChain 框架的 agent 或 chain 调用，需要创建标准的 LangChain Tool 接口。

## What Changes

- 新增 `tools/` 目录用于存放 LangChain 工具
- 新增 `CodeQLGeneratorTool`：封装 CodeQLGeneratorAgent，将自然语言需求转换为 CodeQL 查询代码
- 新增 `CodeQLRunnerTool`：封装 utils/codeql.py 的执行逻辑，运行 CodeQL 查询并返回结果
- 可能需要在 utils/codeql.py 中添加辅助函数以更好地支持工具调用

## Impact

- 影响的能力：新增 `langchain-tools` 能力
- 影响的代码：
  - 新增 `tools/` 目录及相关文件
  - 可能修改 `utils/codeql.py` 以添加必要的辅助函数
  - 与现有的 `agents/codeql_generator_agent.py` 集成

