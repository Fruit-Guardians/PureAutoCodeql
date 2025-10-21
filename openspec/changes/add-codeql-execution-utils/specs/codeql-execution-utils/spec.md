## ADDED Requirements

### Requirement: CodeQL Query Execution
系统 SHALL 提供工具函数用于执行 LLM 生成的 CodeQL 查询并返回结果。

#### Scenario: 执行 CodeQL 查询
- **WHEN** 调用 `execute_codeql_query(query_content, database_path)` 函数
- **THEN** 将查询内容保存到临时文件，使用 `codeql query run` 命令执行查询，并返回执行结果

#### Scenario: 指定数据库路径
- **WHEN** 执行 CodeQL 查询
- **THEN** 使用传入的 `database_path` 参数作为 CodeQL 数据库路径，该路径由调用方（如 `Analyze.py` 的 main 函数）提供

#### Scenario: 处理查询执行失败
- **WHEN** CodeQL 查询执行失败（如语法错误、数据库不存在）
- **THEN** 捕获异常并返回包含错误信息的结果对象

### Requirement: CodeQL Result Parsing
系统 SHALL 提供工具函数用于解析 CodeQL 查询的输出结果。

#### Scenario: 解析 SARIF 格式结果
- **WHEN** 调用 `parse_codeql_results(result_output)` 函数
- **THEN** 解析 CodeQL 输出的 SARIF 格式或 CSV 格式结果，返回结构化的数据

#### Scenario: 处理空结果
- **WHEN** CodeQL 查询返回空结果
- **THEN** 返回空列表或明确的"无结果"标识

### Requirement: Query File Management
系统 SHALL 提供辅助函数用于管理临时查询文件。

#### Scenario: 保存查询到临时文件
- **WHEN** 调用 `save_query_to_file(query_content)` 函数
- **THEN** 将查询内容保存到临时 `.ql` 文件，返回文件路径

#### Scenario: 清理临时文件
- **WHEN** 查询执行完成后
- **THEN** 自动清理临时创建的查询文件（可选，或由调用方决定）

### Requirement: Error Handling and Type Safety
系统 SHALL 确保所有 CodeQL 工具函数具有完善的错误处理和类型注解。

#### Scenario: 类型注解
- **WHEN** 定义工具函数
- **THEN** 使用 Python 类型注解标注参数和返回值类型

#### Scenario: 异常处理
- **WHEN** 执行过程中发生错误（文件 I/O、子进程调用等）
- **THEN** 捕获异常并返回包含错误信息的结果，而不是直接抛出异常

### Requirement: Code Style Consistency
系统 SHALL 保持与项目现有代码风格一致。

#### Scenario: 注释风格
- **WHEN** 编写 `utils/codeql.py` 模块
- **THEN** 仅在函数定义处添加 docstring，代码内部不添加注释

#### Scenario: 代码简洁性
- **WHEN** 实现工具函数
- **THEN** 代码保持高效简洁，避免不必要的输出样式修饰
