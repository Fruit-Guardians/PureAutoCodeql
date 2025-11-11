# Implementation Tasks

## 1. 修改工作区管理策略
- [x] 1.1 修改 `utils/codeql.py` 中的 `create_temporary_qlpack` 函数，接受可选的 `task_id` 参数
- [x] 1.2 当提供 `task_id` 时，使用固定路径 `temp/codeql_temp/<task_id>` 而非时间戳路径
- [x] 1.3 如果目标目录已存在，清理或复用该目录（根据策略决定）
- [x] 1.4 确保 `qlpack.yml` 和 `codeql-pack.lock.yml` 在固定目录中正确生成

## 2. 实现持久化保存机制
- [x] 2.1 在 `tools/codeql_compose.py` 中添加首次生成后的保存逻辑
- [x] 2.2 创建 `temp/ql_queries/<task_id>` 目录结构
- [x] 2.3 将生成的 .ql 文件和相关元数据保存到持久化目录
- [x] 2.4 记录任务 ID 与文件路径的映射关系（可选：用于后续查询）

## 3. 创建错误修复专用 Prompt
- [x] 3.1 创建 `prompts/codeql_fix_inplace.md` 文件
- [x] 3.2 在 prompt 中指导 LLM 使用 `@modelcontextprotocol/server-filesystem` 工具
- [x] 3.3 明确说明使用正则替换（regex replace）功能修改现有文件
- [x] 3.4 在 prompt 中包含占位符 `[[QL_FILE_PATH]]`，用于注入当前 .ql 文件的完整路径
- [x] 3.5 包含 `[[ERROR_LOG]]`、`[[CURR_QL_CONTENT]]` 等现有占位符

## 4. 重构 Agent 架构（已修改设计）
- [x] 4.1 修改 `agents/codeql_gen_agents/codeql_error_agent.py`，删除 `fix_mode` 参数和两种模式
- [x] 4.2 创建新的 `agents/codeql_gen_agents/codeql_fix_inplace_agent.py` 专门用于原地修复
- [x] 4.3 `CodeQLErrorAgent` 只负责错误分析，使用 `codeql_erroranalyze.md`
- [x] 4.4 `CodeQLFixInplaceAgent` 负责原地修复，使用 `codeql_fix_inplace.md`
- [x] 4.5 更新 `agents/codeql_gen_agents/__init__.py` 导出新的 Agent

## 5. 集成到 CodeQLComposeTool（已简化为始终原地修改）
- [x] 5.1 修改 `tools/codeql_compose.py` 的迭代修复逻辑
- [x] 5.2 首轮使用 `CodeQLGenAgent` 生成查询，立即保存到 `query_file` 和持久化目录
- [x] 5.3 后续所有轮次都使用原地修复：`CodeQLErrorAgent` 分析 → `CodeQLFixInplaceAgent` 修改文件
- [x] 5.4 每轮开始前从 `query_file` 读取最新内容（由 FixInplaceAgent 修改）
- [x] 5.5 传递 .ql 文件的绝对路径给 FixInplaceAgent
- [x] 5.6 移除 `has_first_success` 逻辑，简化为统一的修复流程

## 6. 测试与验证
- [ ] 6.1 测试单任务多轮修复流程，验证文件路径保持不变
- [ ] 6.2 测试首次生成后持久化目录是否正确创建
- [ ] 6.3 测试 Error Agent 是否正确调用 MCP 文件系统工具
- [ ] 6.4 验证多个并发任务不会互相干扰（不同 task_id）
- [ ] 6.5 验证旧的时间戳模式仍能正常工作（向后兼容，如果需要）

## 7. 文档与代码风格
- [ ] 7.1 为新增函数添加 docstring
- [ ] 7.2 更新相关函数的 docstring，说明新参数
- [ ] 7.3 确保代码符合项目约定：简洁、高效、注释少

