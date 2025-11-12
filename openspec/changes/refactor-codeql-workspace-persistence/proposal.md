# 重构 CodeQL 工作区持久化策略

## Why
当前系统每次生成 CodeQL 查询时都会在 `temp/codeql_temp` 下创建基于时间戳的新文件夹，导致：
1. 临时文件夹快速堆积，占用磁盘空间
2. 错误修复流程中无法直接修改已生成的 .ql 文件，只能重新生成
3. 调试和追踪变得困难，因为每次迭代都在不同的文件夹中

需要重构为：每个任务使用单一持久化工作区，支持文件原地修改，提升错误修复效率。

## What Changes
- 修改 `create_temporary_qlpack` 函数，每个任务使用固定的工作区路径（`temp/codeql_temp/<task_id>`），而非基于时间戳的随机路径
- 首轮生成 .ql 文件后，立即创建持久化的保存目录（`temp/ql_queries/<task_id>`）并保存文件
- 创建新的 prompt 文件 `prompts/codeql_fix_inplace.md`，用于错误修复阶段
- 拆分 Agent 架构：`CodeQLErrorAgent`（错误分析）和 `CodeQLFixInplaceAgent`（原地修复）
- `CodeQLFixInplaceAgent` 使用 MCP 文件系统工具的 editfile 功能修改现有 .ql 文件
- 在 prompt 中明确指出当前 .ql 文件的完整路径，以便工具调用

## Impact
- 影响的 specs: 
  - 新增或更新 `codeql-execution` capability（文件管理策略）
  - 更新 `codeql-generation-agent` capability（错误修复 prompt）
- 影响的代码:
  - `utils/codeql.py`：修改 `create_temporary_qlpack` 和 `execute_codeql_query` 函数
  - `agents/codeql_gen_agents/codeql_error_agent.py`：支持新的错误修复 prompt
  - `prompts/codeql_erroranalyze.md`：更新为使用文件系统工具的 prompt（或创建新文件）
  - `tools/codeql_compose.py`：调整工作区和持久化目录的管理逻辑

