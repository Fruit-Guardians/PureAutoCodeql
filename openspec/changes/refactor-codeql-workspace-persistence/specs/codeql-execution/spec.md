# codeql-execution Specification

## ADDED Requirements

### Requirement: 任务级工作区管理
系统 SHALL 为每个 CodeQL 生成任务分配一个固定的工作区目录，路径为 `temp/codeql_temp/<task_id>`，在整个任务生命周期内保持不变。

#### Scenario: 创建任务工作区
- **WHEN** 系统启动一个新的 CodeQL 生成任务，并提供 `task_id` 参数
- **THEN** 在 `temp/codeql_temp/<task_id>` 创建工作区目录，包含 `qlpack.yml`、`codeql-pack.lock.yml` 和初始 .ql 文件

#### Scenario: 复用现有工作区
- **WHEN** 同一 `task_id` 的任务多次调用 `create_temporary_qlpack`
- **THEN** 使用相同的工作区目录，而不是创建新的时间戳目录

#### Scenario: 防止任务冲突
- **WHEN** 多个并发任务使用不同的 `task_id`
- **THEN** 每个任务使用独立的工作区目录，互不干扰

### Requirement: 首轮生成后持久化保存
系统 SHALL 在 CodeQL 查询首轮生成后，立即将生成的文件保存到持久化目录 `temp/ql_queries/<task_id>`。

#### Scenario: 首轮生成触发保存
- **WHEN** CodeQL 查询首轮生成完成（无论是否成功）
- **THEN** 创建 `temp/ql_queries/<task_id>` 目录，并保存 .ql 文件及相关元数据文件

#### Scenario: 后续轮次保存新版本
- **WHEN** 任务在后续轮次进行错误修复
- **THEN** 在持久化目录中保存新版本的查询文件（如 `query_round2.ql`、`query_round3.ql`）

#### Scenario: 保存文件结构完整性
- **WHEN** 保存到持久化目录
- **THEN** 包含 .ql 文件和元数据 JSON 文件（包含轮次、时间戳等信息）

### Requirement: 向后兼容时间戳模式
系统 SHALL 在未提供 `task_id` 时，保持原有的基于时间戳的临时目录创建行为。

#### Scenario: 未指定 task_id
- **WHEN** 调用 `create_temporary_qlpack` 时未提供 `task_id` 参数
- **THEN** 使用时间戳格式（如 `20251111_123456_789012`）创建临时目录

#### Scenario: 旧代码路径正常工作
- **WHEN** 现有代码未更新为传递 `task_id`
- **THEN** 系统仍然能够正常生成和执行 CodeQL 查询，不会因缺少参数而失败

