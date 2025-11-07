## MODIFIED Requirements

### Requirement: FastAPI Server Initialization

系统 SHALL 提供基于FastAPI的HTTP服务器，支持异步请求处理和自动API文档生成。

#### Scenario: 服务器启动成功

- **WHEN** 执行服务器启动命令
- **THEN** FastAPI应用成功初始化
- **AND** 服务器监听指定端口
- **AND** 自动生成OpenAPI文档可访问
- **AND** 加载默认LLM供应商配置

#### Scenario: 健康检查端点

- **WHEN** 客户端访问 `/health` 端点
- **THEN** 返回200状态码
- **AND** 返回服务器健康状态信息
- **AND** 包含LLM供应商可用性状态

### Requirement: LangChain工具API端点

系统 SHALL 使用LangServe将LangChain工具暴露为HTTP API端点，支持工具的远程调用和LLM供应商指定。

#### Scenario: CodeQL生成工具调用

- **WHEN** 客户端POST请求到 `/langchain/codeql-compose/invoke`
- **AND** 请求体包含有效的需求描述
- **THEN** 返回CodeQL查询生成结果
- **AND** 响应包含生成的查询代码或错误信息
- **AND** 使用指定的LLM供应商或默认供应商

#### Scenario: 带LLM配置的工具调用

- **WHEN** 客户端POST请求到 `/langchain/codeql-compose/invoke`
- **AND** 请求体包含LLM供应商配置
- **THEN** 使用指定的LLM供应商执行工具
- **AND** 返回包含LLM供应商信息的结果

#### Scenario: 流式工具调用

- **WHEN** 客户端POST请求到 `/langchain/codeql-compose/stream`
- **AND** 请求体包含有效的需求描述
- **THEN** 返回流式响应
- **AND** 实时推送生成过程的中间结果
- **AND** 使用指定的LLM供应商进行推理

#### Scenario: 工具调用参数验证

- **WHEN** 客户端发送无效参数
- **THEN** 返回422状态码
- **AND** 返回详细的验证错误信息
- **AND** 包含LLM供应商配置验证错误

### Requirement: 项目案例列表API

系统 SHALL 提供REST API端点用于获取和管理projects目录下的案例列表。

#### Scenario: 获取所有项目列表

- **WHEN** 客户端GET请求到 `/api/projects`
- **THEN** 返回200状态码
- **AND** 返回JSON格式的项目列表
- **AND** 项目路径下的projects目录的每个项目包含case_id、路径、描述等基本信息

#### Scenario: 获取项目详情

- **WHEN** 客户端GET请求到 `/api/projects/{case_id}`
- **AND** case_id存在于projects目录
- **THEN** 返回200状态码
- **AND** 返回项目的详细信息
- **AND** 包含CVE信息、文件结构、语言检测结果

#### Scenario: 项目不存在

- **WHEN** 客户端GET请求到 `/api/projects/{case_id}`
- **AND** case_id不存在
- **THEN** 返回404状态码
- **AND** 返回错误信息说明项目未找到

#### Scenario: 获取项目文件列表

- **WHEN** 客户端GET请求到 `/api/projects/{case_id}/files`
- **THEN** 返回项目的文件树结构
- **AND** 包含文件路径、大小、类型等信息

### Requirement: 漏洞分析异步任务API

系统 SHALL 提供异步任务API用于启动、查询和管理长时间运行的漏洞分析任务，支持LLM供应商动态指定。

#### Scenario: 启动分析任务

- **WHEN** 客户端POST请求到 `/api/analysis/start`
- **AND** 请求体包含有效的case_id和配置参数
- **THEN** 返回202状态码
- **AND** 立即返回任务ID
- **AND** 后台开始执行分析任务
- **AND** 使用指定的LLM供应商或默认供应商

#### Scenario: 启动带LLM配置的分析任务

- **WHEN** 客户端POST请求到 `/api/analysis/start`
- **AND** 请求体包含LLM供应商配置
- **THEN** 验证LLM供应商配置有效性
- **AND** 返回任务ID和确认的LLM配置
- **AND** 后台使用指定LLM供应商执行任务

#### Scenario: 查询任务状态

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/status`
- **AND** task_id有效
- **THEN** 返回200状态码
- **AND** 返回任务状态（pending/running/completed/failed）
- **AND** 返回任务进度信息（不包括进度条，只有运行到那个模块或者功能的信息）
- **AND** 返回任务使用的LLM供应商信息

#### Scenario: 获取分析结果

- **WHEN** 客户端GET请求到 `/api/analysis/{task_id}/result`
- **AND** 任务已完成
- **THEN** 返回200状态码
- **AND** 返回完整的分析结果
- **AND** 包含CVE分析、Sink/Source分析、CodeQL查询结果
- **AND** 包含任务实际使用的LLM供应商信息

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
- **AND** 包含任务使用的LLM供应商信息

## ADDED Requirements

### Requirement: LLM供应商管理API

系统 SHALL 提供LLM供应商管理和状态查询的API端点，支持供应商选择和可用性检查。

#### Scenario: 获取可用LLM供应商列表

- **WHEN** 客户端GET请求到 `/api/llm/providers`
- **THEN** 返回200状态码
- **AND** 返回所有支持的LLM供应商列表
- **AND** 包含供应商ID、名称、描述和可用性状态

#### Scenario: 获取LLM供应商状态

- **WHEN** 客户端GET请求到 `/api/llm/providers/{provider}/status`
- **AND** provider是有效的供应商ID
- **THEN** 返回200状态码
- **AND** 返回供应商连通性状态
- **AND** 包含响应时间和错误信息（如果有）
- **AND** 返回该供应商支持的模型列表

#### Scenario: 获取所有LLM供应商状态

- **WHEN** 客户端GET请求到 `/api/llm/status`
- **THEN** 返回200状态码
- **AND** 返回所有供应商的状态概览
- **AND** 包含可用性和性能指标
- **AND** 按响应时间排序

#### Scenario: 获取供应商支持的模型

- **WHEN** 客户端GET请求到 `/api/llm/providers/{provider}/models`
- **AND** provider是有效的供应商ID
- **THEN** 返回200状态码
- **AND** 返回该供应商支持的模型列表
- **AND** 包含模型ID、名称、类型（think/chat）和描述

#### Scenario: 测试LLM供应商连通性

- **WHEN** 客户端POST请求到 `/api/llm/providers/{provider}/test`
- **AND** provider是有效的供应商ID
- **THEN** 执行连通性测试
- **AND** 返回测试结果和响应时间
- **AND** 包含任何错误详情

#### Scenario: 供应商不存在

- **WHEN** 客户端请求不存在的供应商
- **THEN** 返回404状态码
- **AND** 返回错误信息说明供应商未找到

### Requirement: 动态LLM配置验证

系统 SHALL 提供LLM配置验证功能，确保任务启动前配置的有效性。

#### Scenario: 验证LLM配置

- **WHEN** 客户端POST请求到 `/api/llm/validate`
- **AND** 请求体包含LLM配置
- **THEN** 验证配置的完整性和有效性
- **AND** 测试供应商连通性
- **AND** 返回验证结果和建议

#### Scenario: 获取推荐配置

- **WHEN** 客户端GET请求到 `/api/llm/recommendations`
- **THEN** 返回推荐的LLM配置
- **AND** 基于当前可用性和性能
- **AND** 包含成本和性能考虑

### Requirement: 任务级别LLM配置

系统 SHALL 支持在任务级别覆盖默认LLM配置，提供灵活的供应商选择机制。

#### Scenario: 任务配置继承默认值

- **WHEN** 创建分析任务时未指定LLM配置
- **THEN** 使用系统默认LLM配置
- **AND** 在任务信息中记录使用的配置

#### Scenario: 任务配置覆盖默认值

- **WHEN** 创建分析任务时指定LLM配置
- **THEN** 验证自定义配置
- **AND** 使用自定义配置执行任务
- **AND** 在任务结果中包含实际使用的配置

#### Scenario: 部分LLM配置覆盖

- **WHEN** 创建分析任务时指定部分LLM配置
- **THEN** 与默认配置合并
- **AND** 自定义字段覆盖默认值
- **AND** 未指定字段使用默认值

### Requirement: LLM供应商配置模型

系统 SHALL 提供标准化的LLM供应商配置数据模型，支持API请求和响应。

#### Scenario: 完整LLM配置指定

- **WHEN** 客户端指定完整的LLM配置
- **THEN** 支持供应商、模型、API密钥、基础URL等所有字段
- **AND** 验证所有必需字段
- **AND** 返回配置确认信息

#### Scenario: 最小LLM配置指定

- **WHEN** 客户端仅指定供应商
- **THEN** 使用供应商默认模型和配置
- **AND** 自动填充合理默认值
- **AND** 返回完整配置信息

### Requirement: API请求验证和错误处理

系统 SHALL 对所有API请求进行参数验证，并提供统一的错误响应格式，包括LLM相关错误。

#### Scenario: 无效LLM供应商配置

- **WHEN** 客户端提供无效的LLM配置
- **THEN** 返回422状态码
- **AND** 返回详细的配置验证错误信息
- **AND** 包含修复建议

#### Scenario: LLM供应商不可达

- **WHEN** 指定的LLM供应商不可达
- **THEN** 返回503状态码
- **AND** 返回供应商不可用错误信息
- **AND** 建议使用其他供应商

#### Scenario: 请求参数验证

- **WHEN** 客户端发送包含无效参数的请求
- **THEN** 返回422状态码
- **AND** 返回详细的验证错误信息
- **AND** 错误信息包含字段名和错误原因

#### Scenario: 服务器内部错误

- **WHEN** 服务器处理请求时发生异常
- **THEN** 返回500状态码
- **AND** 返回通用错误信息
- **AND** 错误详情记录到日志

#### Scenario: 资源未找到

- **WHEN** 客户端请求不存在的资源
- **THEN** 返回404状态码
- **AND** 返回友好的错误信息

### Requirement: API文档和版本管理

系统 SHALL 自动生成API文档，并支持API版本管理，包含LLM配置相关文档。

#### Scenario: 访问API文档

- **WHEN** 客户端访问 `/docs` 端点
- **THEN** 返回Swagger UI界面
- **AND** 显示所有可用的API端点
- **AND** 包含LLM配置端点的文档
- **AND** 提供交互式API测试功能

#### Scenario: 访问OpenAPI规范

- **WHEN** 客户端访问 `/openapi.json` 端点
- **THEN** 返回OpenAPI 3.0格式的API规范
- **AND** 包含所有端点的完整定义
- **AND** 包含LLM配置模型定义

#### Scenario: 获取API版本信息

- **WHEN** 客户端GET请求到 `/api/version`
- **THEN** 返回200状态码
- **AND** 返回API版本号
- **AND** 返回服务器构建信息
- **AND** 返回支持的LLM供应商版本

### Requirement: CORS和中间件支持

系统 SHALL 支持跨域资源共享（CORS）和请求日志记录中间件，包含LLM配置信息。

#### Scenario: 跨域请求支持

- **WHEN** 客户端从不同域发起请求
- **AND** CORS配置允许该域
- **THEN** 请求成功处理
- **AND** 响应包含正确的CORS头

#### Scenario: 请求日志记录

- **WHEN** 任何API请求到达服务器
- **THEN** 请求信息被记录到日志
- **AND** 包含请求方法、路径、状态码、响应时间
- **AND** 不记录敏感的LLM配置信息（如API密钥）

### Requirement: 配置管理

系统 SHALL 支持通过环境变量和配置文件管理API服务器配置，包括LLM供应商配置。

#### Scenario: 从环境变量加载配置

- **WHEN** 服务器启动
- **THEN** 从环境变量读取配置
- **AND** 包含端口号、主机地址、CORS设置等
- **AND** 包含默认LLM供应商配置
- **AND** 使用默认值作为后备

#### Scenario: 配置验证

- **WHEN** 加载配置
- **AND** 配置值无效
- **THEN** 服务器启动失败
- **AND** 输出清晰的错误信息
- **AND** 包含LLM配置错误详情

#### Scenario: 动态配置更新

- **WHEN** 通过API更新LLM配置
- **THEN** 验证新配置的有效性
- **AND** 应用配置更改
- **AND** 记录配置变更历史