# API重构：移除LangChain工具路由，实现SSE流式输出

## Why

当前API提供了独立的LangChain工具路由（使用LangServe），允许直接调用CodeQL生成工具。但实际使用场景中，用户更需要实时监控分析任务的agent执行进度，而不是直接操作agent工具。将API简化为只提供分析任务的流式输出，可以：

1. 简化API架构，专注于核心功能
2. 提供更好的用户体验（实时进度反馈）
3. 降低复杂度，移除不必要的直接agent操作接口

## What Changes

- **BREAKING**: 删除 `api/langchain_routes.py` 及其相关的LangServe集成
- **BREAKING**: 删除 `api/models.py` 中的 `CodeQLComposeRequest` 和 `CodeQLComposeResponse` 模型
- **BREAKING**: 移除 `api/server.py` 中对LangChain路由的引用
- 添加SSE（Server-Sent Events）流式输出端点 `GET /api/analysis/{task_id}/stream`
- 修改 `TaskManager` 以支持实时事件推送
- 在分析任务执行过程中收集agent输出并通过SSE推送

## Impact

- **Affected specs**: `http-api-server`（如果存在）或需要创建新的capability spec
- **Affected code**:
  - `api/langchain_routes.py` - 删除
  - `api/server.py` - 移除LangChain路由引用
  - `api/models.py` - 删除CodeQL工具相关模型
  - `api/analysis_routes.py` - 添加SSE流式输出端点
  - `api/task_manager.py` - 添加事件推送机制
- **Breaking Changes**:
  - `/langchain/codeql-compose/*` 端点将不再可用
  - 使用这些端点的客户端需要迁移到新的SSE流式接口
- **Migration**: 客户端应改用 `GET /api/analysis/{task_id}/stream` 来获取实时分析进度

