# codeql-generator-agent Specification

## ADDED Requirements

### Requirement: CodeQL 代码生成 Agent
系统 SHALL 提供一个新的 Agent（`codeql_generator_agent.py`），用于根据用户的自然语言描述生成 CodeQL 查询代码。

#### Scenario: 接收用户查询需求
- **WHEN** 用户提供自然语言描述的 CodeQL 查询需求（如"查询所有 source 点"或"查询调用 getParameter 函数的位置"）
- **THEN** Agent 接收该需求作为输入并开始处理

#### Scenario: 生成 CodeQL 代码
- **WHEN** Agent 处理用户需求
- **THEN** 生成符合 CodeQL 语法规范的查询代码

#### Scenario: 输出格式化的 CodeQL 代码
- **WHEN** 生成完成
- **THEN** 输出的代码用 `<codeql></codeql>` 标签包裹，便于后续解析和使用

#### Scenario: 支持常见查询场景
- **WHEN** 用户请求查询 source 点、特定函数、数据流路径等常见场景
- **THEN** Agent 能够正确识别场景并生成对应的 CodeQL 代码

#### Scenario: 仅输出生成结果
- **WHEN** 生成 CodeQL 代码
- **THEN** 仅打印包含 `<codeql></codeql>` 标签的代码，不包含其他说明或样式修饰

#### Scenario: 代码风格符合项目约定
- **WHEN** 编写 `codeql_generator_agent.py`
- **THEN** 代码风格保持简洁高效，仅在函数上添加注释（docstring），代码内部不添加额外注释

#### Scenario: 复用项目 LLM 配置
- **WHEN** 初始化 Agent 的 LLM 客户端
- **THEN** 复用项目现有的 LLM 配置（如 ChatOpenAI 模型参数），保持一致性

