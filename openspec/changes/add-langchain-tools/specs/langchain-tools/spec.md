# LangChain 工具集成规范

## ADDED Requirements

### Requirement: CodeQL 生成工具
系统 SHALL 提供一个 LangChain 工具用于根据自然语言需求生成 CodeQL 查询代码。

#### Scenario: 成功生成 CodeQL 查询
- **WHEN** 用户通过工具提供自然语言需求描述
- **THEN** 工具调用 CodeQLGeneratorAgent 生成相应的 CodeQL 代码
- **AND** 返回格式化的 CodeQL 查询字符串

#### Scenario: 生成失败处理
- **WHEN** CodeQLGeneratorAgent 执行失败或超时
- **THEN** 工具返回包含错误信息的结果
- **AND** 错误信息应明确说明失败原因

### Requirement: CodeQL 执行工具
系统 SHALL 提供一个 LangChain 工具用于执行 CodeQL 查询并返回结果。

#### Scenario: 成功执行查询
- **WHEN** 用户提供有效的 CodeQL 查询代码和数据库路径
- **THEN** 工具调用 utils.codeql.execute_codeql_query 执行查询
- **AND** 返回格式化的查询结果

#### Scenario: 查询执行失败
- **WHEN** CodeQL 查询语法错误或数据库路径无效
- **THEN** 工具返回包含错误信息的结果
- **AND** 错误信息应包含 CodeQL CLI 的原始错误输出

#### Scenario: 执行超时
- **WHEN** 查询执行时间超过预设限制（300秒）
- **THEN** 工具终止查询执行
- **AND** 返回超时错误信息

### Requirement: LangChain 标准接口
两个工具 SHALL 遵循 LangChain BaseTool 接口规范。

#### Scenario: 工具注册
- **WHEN** 导入工具类
- **THEN** 工具应具有明确的 name 和 description 属性
- **AND** 工具应定义清晰的输入 schema

#### Scenario: 同步调用
- **WHEN** 通过 LangChain agent 或手动调用工具的 run 方法
- **THEN** 工具应正确执行并返回字符串结果

#### Scenario: 异步调用
- **WHEN** 通过 arun 方法异步调用工具
- **THEN** 工具应支持异步执行
- **AND** 返回格式与同步调用一致

### Requirement: 工具组织结构
工具代码 SHALL 存放在独立的 `tools/` 目录中。

#### Scenario: 模块导入
- **WHEN** 其他模块需要使用这些工具
- **THEN** 可以通过 `from tools import CodeQLGeneratorTool, CodeQLRunnerTool` 导入
- **AND** 工具类在 `tools/__init__.py` 中正确导出

#### Scenario: 代码组织
- **WHEN** 查看项目结构
- **THEN** 每个工具应有独立的文件
- **AND** 文件命名遵循 snake_case 约定

