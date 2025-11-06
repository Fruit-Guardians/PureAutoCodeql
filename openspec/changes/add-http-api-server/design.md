## Context

PureAutoCodeQL 是一个基于多智能体架构的自动化漏洞分析工具，当前仅支持命令行和Python API调用。为了支持更广泛的集成场景（如Web界面、CI/CD流程、远程调用等），需要提供HTTP API接口。

**约束条件**:
- 必须保持与现有架构的解耦，不影响核心分析逻辑
- 需要支持异步任务执行，因为漏洞分析是长时间运行的操作
- LangChain工具需要通过LangServe标准化暴露
- 项目使用Python 3.13，需要确保依赖兼容性

**利益相关者**:
- 开发者：需要清晰的API接口和文档
- 运维人员：需要易于部署和监控的服务
- 集成方：需要标准化的REST API

## Goals / Non-Goals

**Goals**:
- 提供标准化的REST API接口
- 使用LangServe实现LangChain工具的API化
- 实现项目案例的CRUD操作
- 支持异步任务执行和状态查询
- 提供完整的API文档（OpenAPI/Swagger）
- 保持API层与业务逻辑层的解耦

**Non-Goals**:
- 不实现用户认证系统（可在后续版本添加）
- 不实现数据持久化存储（使用内存存储任务状态）
- 不实现分布式部署（单机部署）
- 不修改现有的核心分析逻辑

## Decisions

### 1. Web框架选择：FastAPI

**决策**: 使用 FastAPI 作为 Web 框架

**理由**:
- 原生支持异步操作（async/await）
- 自动生成OpenAPI文档
- 基于Pydantic的数据验证
- 高性能（基于Starlette和Pydantic）
- 与LangServe完美集成

**备选方案**:
- Flask: 生态成熟但缺少原生异步支持
- Django: 过于重量级，不适合API服务

### 2. LangChain工具API化：LangServe

**决策**: 使用 LangServe 将 LangChain 工具暴露为 API

**理由**:
- LangChain官方推荐的API化方案
- 自动处理流式响应和异步调用
- 标准化的工具调用协议
- 与FastAPI无缝集成

**备选方案**:
- 手动实现工具API: 工作量大且不标准化

### 3. 目录结构设计

**决策**: 采用以下目录结构

```
api/
├── __init__.py
├── server.py              # FastAPI应用主入口
├── config.py              # API配置
├── models.py              # Pydantic数据模型
├── dependencies.py        # 依赖注入
├── middleware.py          # 中间件
├── routes/
│   ├── __init__.py
│   ├── langchain.py       # LangServe路由
│   ├── projects.py        # 项目管理路由
│   └── analysis.py        # 分析任务路由
└── services/
    ├── __init__.py
    ├── task_manager.py    # 异步任务管理
    └── project_scanner.py # 项目扫描服务
```

**理由**:
- 清晰的职责分离
- 易于扩展和维护
- 符合FastAPI最佳实践
- 与现有项目结构保持一致

### 4. 异步任务管理

**决策**: 使用 FastAPI BackgroundTasks + 内存任务队列

**理由**:
- 简单直接，无需额外依赖
- 适合单机部署场景
- 易于调试和监控

**备选方案**:
- Celery: 过于重量级，需要Redis/RabbitMQ
- RQ: 需要Redis依赖

**未来扩展**: 如需分布式部署，可迁移到Celery

### 5. API路由设计

**决策**: 采用以下API路由结构

```
# 健康检查和元信息
GET  /health
GET  /api/version

# LangChain工具（LangServe）
POST /langchain/codeql-compose/invoke
POST /langchain/codeql-compose/stream

# 项目管理
GET  /api/projects
GET  /api/projects/{case_id}
GET  /api/projects/{case_id}/files

# 漏洞分析
POST   /api/analysis/start
GET    /api/analysis/{task_id}/status
GET    /api/analysis/{task_id}/result
DELETE /api/analysis/{task_id}
GET    /api/analysis/tasks
```

**理由**:
- RESTful设计原则
- 清晰的资源层次
- LangServe标准路由
- 易于理解和使用

## Risks / Trade-offs

### 风险1: 长时间运行任务的超时问题

**风险**: 漏洞分析可能需要几分钟到几十分钟，HTTP请求可能超时

**缓解措施**:
- 使用异步任务模式，立即返回task_id
- 提供状态查询接口
- 支持WebSocket推送进度（可选）

### 风险2: 并发请求导致资源耗尽

**风险**: 多个并发分析任务可能消耗大量CPU和内存

**缓解措施**:
- 实现任务队列和并发限制
- 添加请求限流（rate limiting）
- 提供任务优先级机制

### 风险3: API安全性

**风险**: 未实现认证可能导致滥用

**缓解措施**:
- 第一版本部署在内网环境
- 添加IP白名单配置
- 后续版本添加API Key认证

### Trade-off: 简单性 vs 功能完整性

**选择**: 优先简单性，功能渐进式添加

**理由**:
- 快速交付可用版本
- 降低初期维护成本
- 根据实际使用反馈迭代

## Migration Plan

### 阶段1: 基础API搭建（第1-3周）
1. 安装依赖和创建目录结构
2. 实现基础服务器和健康检查
3. 实现项目管理API

### 阶段2: LangChain集成（第4-5周）
1. 集成LangServe
2. 暴露CodeQLComposeTool
3. 测试工具调用

### 阶段3: 分析任务API（第6-7周）
1. 实现异步任务管理
2. 实现分析API端点
3. 添加任务状态追踪

### 阶段4: 文档和测试（第8周）
1. 完善API文档
2. 编写测试用例
3. 性能测试和优化

### 回滚计划
- API层独立部署，不影响现有命令行功能
- 可随时停止API服务，回退到命令行模式
- 保持核心逻辑不变，确保向后兼容

## Open Questions

1. **是否需要实现WebSocket支持用于实时进度推送？**
   - 建议：第一版本不实现，使用轮询方式
   - 后续根据需求添加

2. **是否需要实现任务结果的持久化存储？**
   - 建议：第一版本使用内存存储
   - 后续可添加数据库支持

3. **是否需要支持批量分析API？**
   - 建议：第一版本不实现
   - 可通过客户端循环调用实现

4. **API版本管理策略？**
   - 建议：使用URL路径版本（/api/v1/...）
   - 第一版本为 v1

5. **是否需要实现API使用统计和监控？**
   - 建议：添加基础日志记录
   - 后续集成Prometheus等监控工具
