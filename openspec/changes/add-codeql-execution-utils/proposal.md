## Why
当前系统中 LLM 生成 CodeQL 查询后，缺少执行这些查询并获取结果的工具函数。需要在 utils 模块中添加 CodeQL 执行相关的工具函数，以便 Agent 能够自动执行生成的查询并获取分析结果。

## What Changes
- 在 `utils/` 目录下新增 `codeql.py` 模块
- 添加执行 CodeQL 查询的核心函数
- 添加解析 CodeQL 查询结果的辅助函数
- CodeQL 数据库路径通过参数传递（由 `Analyze.py` 的 main 函数设置）
- 不涉及 CodeQL 数据库的创建，仅负责查询执行

## Impact
- Affected specs: 新增 `codeql-execution-utils` capability
- Affected code: 
  - 新增 `utils/codeql.py`
  - 更新 `utils/__init__.py` 导出新函数
  - 未来 Agent 可调用这些工具函数执行 CodeQL 查询
