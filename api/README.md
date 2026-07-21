# API 模块

本目录包含 PureAutoCodeQL 的 FastAPI 服务端实现。

## 文件说明

- **`server.py`** - FastAPI 应用主入口
- **`models.py`** - API 数据模型定义（请求/响应/事件）
- **`task_manager.py`** - 分析任务管理器
- **`analysis_routes.py`** - 分析任务相关的API路由
- **`projects_routes.py`** - 项目管理相关的API路由
- **`config.py`** - API服务配置

## 文档

- **`SSE_REFERENCE.md`** - 📡 **Server-Sent Events (SSE) 流式事件参考文档**
  - 完整的SSE事件类型说明
  - 所有Agent的事件流程
  - 前端实现示例（JavaScript/TypeScript/React/Vue）
  - 错误处理和最佳实践

## 快速开始

### 启动API服务器

```bash
# Linux/Mac
./scripts/start_api_server.sh

# Windows (PowerShell)
.\scripts\start_api_server.ps1

# 或直接运行（默认仅监听本机）
uvicorn api.server:app --host 127.0.0.1 --port 8000 --reload
```

默认情况下，API 只绑定 `127.0.0.1`，项目导入只允许 `imports/` 目录下的源路径，并拒绝请求体中的构建命令。需要开放远程访问或构建命令时，请显式设置对应的 `API_*` 环境变量，并建议同时设置 `API_AUTH_TOKEN`。API 运行配置中，`API_*` 环境变量优先于 `config/keys.toml` 的 `[settings]` 默认值。

### API文档

服务启动后访问：
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

### 主要端点

#### 分析任务

```
POST   /api/analysis/start          - 启动新的分析任务
GET    /api/analysis/{task_id}/status - 获取任务状态
GET    /api/analysis/{task_id}/result - 获取任务结果
GET    /api/analysis/{task_id}/stream - SSE流式事件 🔥
GET    /api/analysis/tasks            - 列出所有任务
DELETE /api/analysis/{task_id}       - 取消任务
```

#### 项目管理

```
GET    /api/projects           - 列出所有项目
GET    /api/projects/{case_id} - 获取项目详情
```

## SSE 流式事件

本项目实现了完整的 SSE 流式事件系统，用于实时推送分析进度和Agent执行详情。

### 快速连接

```javascript
const eventSource = new EventSource(
  '/api/analysis/{task_id}/stream'
);

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('事件类型:', data.type);
  console.log('Agent:', data.agent_name);
  console.log('消息:', data.message);
};
```

### 详细文档

请查看 **[SSE_REFERENCE.md](./SSE_REFERENCE.md)** 获取：
- 完整的事件类型列表
- Agent执行流程说明
- 前端集成示例代码
- 错误处理和最佳实践

## 开发

### 添加新的API端点

1. 在相应的 `*_routes.py` 文件中定义路由
2. 在 `models.py` 中添加请求/响应模型
3. 在 `server.py` 中注册路由

### 添加新的SSE事件类型

1. 在 `models.py` 的 `StreamEventType` 枚举中添加新类型
2. 在Agent或Pipeline中推送新事件
3. 更新 `SSE_REFERENCE.md` 文档

## 测试

```bash
# 手工测试SSE流式输出（需要先启动 API 服务和可分析案例）
python scripts/smoke_sse_stream.py

# 手工测试 CodeQL LSP 语法检查（需要本机 CodeQL/LSP 环境）
python scripts/smoke_lsp_syntax.py

# 使用curl测试
curl -N http://localhost:8000/api/analysis/{task_id}/stream
```

## 相关模块

- **core/** - 核心分析逻辑（Orchestrator, Pipeline, Context）
- **agents/** - 各种分析Agent实现
- **services/** - LLM服务、知识库等
- **utils/** - 工具函数

## 注意事项

- SSE连接会在分析完成或出错时自动关闭
- 长时间运行的分析建议实现前端重连机制
- 事件历史默认保留最近1000条
- 任务ID为UUID格式

## 许可证

与项目主体保持一致
