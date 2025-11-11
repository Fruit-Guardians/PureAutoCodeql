## 1. 添加依赖
- [x] 1.1 在`pyproject.toml`的`[project.dependencies]`中添加`tiktoken>=0.5.0`

## 2. 创建Token限制包装器
- [x] 2.1 在`services/llm_service.py`中创建`_limit_tool_output_tokens`函数
- [x] 2.2 实现Token计数逻辑（使用tiktoken，失败时用字符数/4估算）
- [x] 2.3 实现Token截断逻辑（超过8000时保留前40%和后60%）
- [x] 2.4 添加截断反馈信息
- [x] 2.5 支持元组格式(content, artifact)的返回值

## 3. 修改MultiAgentAnalyzer.initialize方法
- [x] 3.1 在获取MCP工具后，遍历所有工具
- [x] 3.2 为每个工具的_run和_arun方法添加Token限制包装（BaseTool内部方法）
- [x] 3.3 确保包装后的工具保留原有的错误处理属性

## 4. 添加调试日志
- [x] 4.1 在`_limit_tool_output_tokens`中添加截断日志
- [x] 4.2 在工具包装器中添加调用跟踪日志
- [x] 4.3 在初始化时记录包装的工具数量
- [x] 4.4 添加`get_logger`导入

## 5. 测试验证
- [ ] 5.1 手动测试大量ripgrep结果的Token截断
- [ ] 5.2 手动测试大文件读取的Token截断
- [ ] 5.3 验证tiktoken导入失败时的fallback机制
- [ ] 5.4 确保所有MCP工具正常工作
- [ ] 5.5 检查日志确认包装器被正确调用
