## Why

当前HTTP API的分析任务无法动态指定LLM供应商，所有任务都使用环境变量配置的默认LLM供应商。在实际应用中，用户可能需要根据不同的分析任务需求、成本考虑或供应商可用性来选择不同的LLM供应商（如DeepSeek、SiliconFlow、智谱等）。需要通过HTTP API提供动态指定LLM供应商的功能，增强系统的灵活性和可用性。

## What Changes

- 扩展分析任务请求模型，添加LLM供应商和模型配置参数
- 修改分析任务API，支持在启动任务时指定LLM供应商和模型
- 添加LLM供应商状态查询API，返回可用供应商及其连接状态
- 更新任务管理器，支持任务级别的LLM配置覆盖
- 增强配置系统，支持临时性的LLM供应商配置
- **BREAKING**: 分析任务请求模型新增必需字段，需要默认值处理

## Impact

- **Affected specs**: `http-api-server` capability
- **Affected code**: 
  - `api/models.py` - 扩展 `AnalysisRequest` 和相关模型
  - `api/analysis_routes.py` - 修改任务启动逻辑
  - `api/task_manager.py` - 支持任务级别LLM配置
  - `config.py` - 增强LLM配置灵活性
  - 新增 `api/llm_routes.py` - LLM供应商相关API端点