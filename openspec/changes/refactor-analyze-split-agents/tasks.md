## 1. 目录结构创建
- [x] 1.1 创建 `agents/` 目录用于存放独立的 Agent 模块
- [x] 1.2 创建 `utils/` 目录用于存放通用工具函数
- [x] 1.3 在 `agents/` 目录下创建 `__init__.py` 文件使其成为 Python 包
- [x] 1.4 在 `utils/` 目录下创建 `__init__.py` 文件使其成为 Python 包

## 2. Agent 模块拆分
- [x] 2.1 创建 `agents/cve_analysis_agent.py`，迁移 `CVEAnalysisAgent` 类及其相关方法
- [x] 2.2 创建 `agents/java_sink_path_agent.py`，迁移 `JavaPathAnalysisAgent` 类及其相关方法
- [x] 2.3 创建 `agents/java_source_analysis_agent.py`，迁移 `JavaSourceAnalysisAgent` 类及其相关方法
- [x] 2.4 确保每个 Agent 模块包含必要的导入语句和类型注解

## 3. 工具函数模块拆分
- [x] 3.1 创建 `utils/io.py`，迁移 `read_json_text` 和 `write_analysis_output` 函数
- [x] 3.2 创建 `utils/java.py`，迁移 `find_path_from_java_file` 函数
- [x] 3.3 确保工具函数模块包含必要的导入语句和类型注解
- [x] 3.4 保持所有函数签名和行为完全不变

## 4. 共享配置和基础类处理
- [x] 4.1 保留 `AgentConfig`、`AgentResult`、`MultiAgentAnalyzer` 类在 `Analyze.py` 中
- [x] 4.2 通过构造函数注入方式将 `MultiAgentAnalyzer` 实例传递给各个 Agent
- [x] 4.3 避免在新模块中创建全局客户端或配置，防止循环依赖
- [x] 4.4 确保 LLM 和 MCP 客户端的初始化逻辑保持在 `Analyze.py` 中

## 5. 更新主文件导入路径
- [x] 5.1 在 `Analyze.py` 中添加对新 Agent 模块的导入语句
- [x] 5.2 在 `Analyze.py` 中添加对新工具函数模块的导入语句
- [x] 5.3 更新 `run_multi_agent_analysis` 函数中的 Agent 实例化代码
- [x] 5.4 确保主流程函数 `main()` 和入口点保持不变

## 6. 代码清理和优化
- [x] 6.1 从 `Analyze.py` 中移除已迁移的 Agent 类定义
- [x] 6.2 从 `Analyze.py` 中移除已迁移的工具函数定义
- [x] 6.3 保持 `Analyze.py` 中的注释和文档字符串完整性
- [x] 6.4 确保代码风格和格式在所有新文件中保持一致

## 7. 功能验证和测试
- [x] 7.1 验证 `Analyze.py` 可以正常导入所有新模块
- [x] 7.2 测试 CVE 分析功能是否正常工作（使用 `CVE-2021-21985.json`）
- [x] 7.3 测试 Java Sink 路径分析功能是否正常工作
- [x] 7.4 测试 Java Source 分析功能是否正常工作
- [x] 7.5 验证完整的多 Agent 工作流程能够正常执行
- [x] 7.6 确认 `output.md` 文件生成内容与重构前一致

## 8. 错误处理和边界情况
- [x] 8.1 验证文件不存在时的错误处理机制
- [x] 8.2 验证JSON解析错误时的错误处理机制  
- [x] 8.3 验证Java文件目录为空时的处理逻辑
- [x] 8.4 确保所有异常情况下的用户反馈清晰明确

## 9. 文档和注释更新
- [x] 9.1 为新的 Agent 模块添加适当的模块级文档字符串
- [x] 9.2 为新的工具函数模块添加适当的模块级文档字符串
- [x] 9.3 更新 `Analyze.py` 中的注释，反映新的模块结构
- [x] 9.4 确保所有类和函数的文档字符串保持完整和准确

## 10. 最终验证和清理
- [x] 10.1 运行完整的分析流程，确认输出结果与重构前完全一致
- [x] 10.2 检查是否存在未使用的导入语句或变量
- [x] 10.3 验证新的模块结构支持独立的单元测试
- [x] 10.4 确认重构后的代码结构更清晰、更易维护
