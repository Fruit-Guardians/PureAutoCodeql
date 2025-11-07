## REMOVED Requirements

### Requirement: LangChain工具API端点

**Reason**: 删除直接调用LangChain工具的API端点，简化API架构。用户不应直接操作agent工具，而应通过分析任务接口获取实时输出。

**Migration**: 客户端应改用 `GET /api/analysis/{task_id}/stream` 端点来获取分析任务的实时输出，而不是直接调用LangChain工具。

## ADDED Requirements

### Requirement: 分析任务SSE流式输出

系统 SHALL 提供SSE（Server-Sent Events）端点，用于实时推送分析任务执行过程中agent的输出。

#### Scenario: 建立SSE连接

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/stream`
- **AND** task_id有效
- **THEN** 返回200状态码
- **AND** 响应头包含 `Content-Type: text/event-stream`
- **AND** 建立持久连接

#### Scenario: 实时推送agent输出

- **WHEN** SSE连接已建立
- **AND** 分析任务正在执行
- **THEN** 服务器实时推送agent的输出事件
- **AND** 每个事件包含事件类型（progress/output/error）和数据
- **AND** 事件按照时间顺序推送

#### Scenario: 任务完成后推送完成事件

- **WHEN** 分析任务执行完成
- **THEN** 推送 `completed` 事件
- **AND** 事件包含最终状态（success/failed）
- **AND** 关闭SSE连接

#### Scenario: 任务不存在

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/stream`
- **AND** task_id不存在
- **THEN** 返回404状态码
- **AND** 返回错误信息说明任务未找到

#### Scenario: 任务已结束时建立连接

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/stream`
- **AND** 任务已经完成（completed/failed/cancelled）
- **THEN** 返回410状态码
- **AND** 返回错误信息说明任务已结束，无法建立流式连接

#### Scenario: 多客户端同时订阅

- **WHEN** 多个客户端同时订阅同一任务的流式输出
- **THEN** 每个客户端独立接收事件流
- **AND** 不影响任务执行
- **AND** 客户端断开不影响其他订阅者

#### Scenario: 连接断开处理

- **WHEN** 客户端主动断开SSE连接
- **THEN** 服务器清理该连接的资源
- **AND** 任务继续执行不受影响
- **AND** 客户端可以重新建立连接

## MODIFIED Requirements

### Requirement: 漏洞分析异步任务API

系统 SHALL 提供异步任务API用于启动、查询和管理长时间运行的漏洞分析任务。

#### Scenario: 启动分析任务

- **WHEN** 客户端POST请求到 `/api/analysis/start`
- **AND** 请求体包含有效的case_id和配置参数
- **THEN** 返回202状态码
- **AND** 立即返回任务ID
- **AND** 后台开始执行分析任务
- **AND** 任务执行过程中的输出可通过SSE流式接口获取

#### Scenario: 查询任务状态

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/status`
- **AND** task_id有效
- **THEN** 返回200状态码
- **AND** 返回任务状态（pending/running/completed/failed）
- **AND** 返回任务进度信息（不包括进度条，只有运行到那个模块或者功能的信息）

#### Scenario: 获取分析结果

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/result`
- **AND** 任务已完成
- **THEN** 返回200状态码
- **AND** 返回完整的分析结果
- **AND** 包含CVE分析、Sink/Source分析、CodeQL查询结果

#### Scenario: 任务未完成时获取结果

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/result`
- **AND** 任务尚未完成
- **THEN** 返回409状态码
- **AND** 返回错误信息说明任务未完成

#### Scenario: 取消运行中的任务

- **WHEN** 客户端DELETE请求到 `/api/analysis/{task_id}`
- **AND** 任务正在运行
- **THEN** 返回200状态码
- **AND** 任务被标记为取消状态
- **AND** 后台停止任务执行

#### Scenario: 获取所有任务列表

- **WHEN** 客户端GET请求到 `/api/analysis/tasks`
- **THEN** 返回200状态码
- **AND** 返回所有任务的摘要信息
- **AND** 支持分页和过滤参数

