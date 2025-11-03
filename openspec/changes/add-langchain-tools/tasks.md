# 实施任务清单

## 1. 目录和文件结构设置
- [x] 1.1 创建 `tools/` 目录
- [x] 1.2 创建 `tools/__init__.py` 并导出工具类
- [x] 1.3 创建 `tools/codeql_generator_tool.py`
- [x] 1.4 创建 `tools/codeql_runner_tool.py`

## 2. CodeQLGeneratorTool 实现
- [x] 2.1 实现 LangChain BaseTool 继承
- [x] 2.2 定义工具名称和描述
- [x] 2.3 定义输入参数 schema
- [x] 2.4 实现 `_run` 方法调用 CodeQLGeneratorAgent
- [x] 2.5 实现错误处理和返回格式化
- [x] 2.6 支持同步和异步调用

## 3. CodeQLRunnerTool 实现
- [x] 3.1 实现 LangChain BaseTool 继承
- [x] 3.2 定义工具名称和描述
- [x] 3.3 定义输入参数 schema（query_content, database_path）
- [x] 3.4 实现 `_run` 方法调用 utils.codeql.execute_codeql_query
- [x] 3.5 实现结果格式化和错误处理
- [x] 3.6 支持同步和异步调用

## 4. 辅助功能增强
- [x] 4.1 检查 utils/codeql.py 是否需要添加辅助函数
- [x] 4.2 如需要，添加结果格式化或简化函数
- [x] 4.3 确保工具输出格式适合 LangChain 使用

## 5. 集成和测试
- [x] 5.1 编写简单的使用示例
- [x] 5.2 验证工具可以被 LangChain agent 正确调用
- [x] 5.3 确保错误处理机制完善

