## Why

当前项目缺少HTTP API接口，无法通过网络调用实现CodeQL自动化分析功能。需要提供标准化的REST API接口，支持LangChain工具的远程调用和项目案例管理功能，使系统能够集成到更广泛的应用场景中。

## What Changes

- 添加基于FastAPI的HTTP API服务器
- 使用LangServe实现LangChain工具的API端点
- 实现项目案例列表和管理的REST API
- 实现漏洞分析任务的异步执行API
- 提供API文档和健康检查端点
- 解耦API层与业务逻辑层，保持清晰的目录结构

## Impact

- **Affected specs**: 新增 `http-api-server` capability
- **Affected code**: 
  - 新增 `api/` 目录及相关模块
  - 可能需要调整 `tools/` 中的工具以支持API调用
  - 需要更新 `pyproject.toml` 添加FastAPI和LangServe依赖
  - 需要更新 `README.md` 添加API使用说明
