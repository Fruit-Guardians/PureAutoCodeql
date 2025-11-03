# Implementation Tasks

## 1. 创建 Agent 核心模块
- [x] 1.1 创建 `agents/codeql_generator_agent.py` 文件
- [x] 1.2 实现 Agent 初始化函数，复用项目现有的 LLM 配置
- [x] 1.3 设计 Agent 的 system prompt，明确其生成 CodeQL 代码的职责
- [x] 1.4 实现接收用户查询需求的输入接口

## 2. 实现 CodeQL 代码生成逻辑
- [x] 2.1 实现核心生成函数，将用户需求转换为 CodeQL 代码
- [x] 2.2 添加输出格式化逻辑，确保代码用 `<codeql></codeql>` 包裹
- [x] 2.3 支持常见查询场景：source 点查询、函数查询、数据流分析等
- [x] 2.4 确保生成的代码符合 CodeQL 语法规范

## 3. 集成与导出
- [x] 3.1 更新 `agents/__init__.py`，导出新的 agent 函数
- [x] 3.2 创建或更新主入口文件，提供调用该 agent 的方式
- [x] 3.3 确保代码风格符合项目约定（简洁、仅函数注释）

## 4. 测试与验证
- [ ] 4.1 测试生成 source 点查询的 CodeQL 代码
- [ ] 4.2 测试生成函数查询的 CodeQL 代码
- [ ] 4.3 测试生成数据流分析的 CodeQL 代码
- [ ] 4.4 验证输出格式正确（包含 `<codeql></codeql>` 标签）

## 5. 文档
- [ ] 5.1 在函数上添加清晰的 docstring 说明用途和参数
- [ ] 5.2 如有必要，更新 README.md 说明新 agent 的使用方法

