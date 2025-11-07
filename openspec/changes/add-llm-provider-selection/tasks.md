## 1. API模型扩展
- [ ] 1.1 扩展 `AnalysisRequest` 模型，添加LLM供应商相关字段
- [ ] 1.2 创建 `LLMProviderConfig` 数据模型
- [ ] 1.3 创建 `LLMProviderStatus` 响应模型
- [ ] 1.4 创建 `LLMProviderListResponse` 响应模型
- [ ] 1.5 更新现有响应模型以支持LLM配置信息

## 2. LLM供应商API端点
- [ ] 2.1 创建 `api/llm_routes.py` 模块
- [ ] 2.2 实现 GET `/api/llm/providers` 端点
- [ ] 2.3 实现 GET `/api/llm/providers/{provider}/status` 端点
- [ ] 2.4 实现 GET `/api/llm/models` 端点，获取可用模型列表
- [ ] 2.5 在主服务器中注册LLM路由

## 3. 任务管理器增强
- [ ] 3.1 修改 `task_manager.py` 支持任务级别LLM配置
- [ ] 3.2 更新任务创建方法接受LLM配置参数
- [ ] 3.3 实现LLM配置验证逻辑
- [ ] 3.4 修改任务执行函数使用自定义LLM配置
- [ ] 3.5 在任务状态和结果中包含LLM配置信息

## 4. 分析路由更新
- [ ] 4.1 修改 `/api/analysis/start` 端点处理LLM配置
- [ ] 4.2 添加LLM配置验证中间件
- [ ] 4.3 更新错误处理以支持LLM相关错误
- [ ] 4.4 确保向后兼容性（默认LLM配置）

## 5. 配置系统增强
- [ ] 5.1 扩展 `config.py` 支持动态LLM供应商配置
- [ ] 5.2 实现LLM配置验证功能
- [ ] 5.3 添加LLM供应商连通性检查
- [ ] 5.4 支持临时LLM配置创建和清理

## 6. 测试和验证
- [ ] 6.1 编写LLM供应商API单元测试
- [ ] 6.2 编写分析任务LLM配置集成测试
- [ ] 6.3 测试不同LLM供应商的任务执行
- [ ] 6.4 验证向后兼容性
- [ ] 6.5 测试错误场景和边界条件

## 7. 文档和部署
- [ ] 7.1 更新API文档说明LLM配置功能
- [ ] 7.2 添加使用示例到README
- [ ] 7.3 更新环境变量配置说明
- [ ] 7.4 验证部署配置和启动流程