## ADDED Requirements

### Requirement: 语言驱动的 QL 模板注入
系统 SHALL 根据 `default_language` 将对应模板文件内容注入 Prompt 占位符 `[[QL_TEMPLATE]]`。

#### Scenario: Java 模板注入
- **WHEN** `default_language` 为 `java`
- **THEN** 从 `agents/prompts/java_temple_ql.md` 读取模板内容，替换 `[[QL_TEMPLATE]]`

#### Scenario: Python 模板注入
- **WHEN** `default_language` 为 `python`
- **THEN** 从 `agents/prompts/python_template_ql.md` 读取模板内容，替换 `[[QL_TEMPLATE]]`

#### Scenario: 向后兼容回退
- **WHEN** 语言未知或模板文件缺失
- **THEN** 使用空字符串或默认 Java 模板作为回退，不影响既有流程

### Requirement: 占位符替换集中化
系统 SHALL 在 `tools/codeql_compose.py` 中集中处理 Prompt 占位符替换，包括 `[[QL_TEMPLATE]]` 与其他既有占位符，确保两类 Agent（生成/错误分析）采用一致的替换逻辑。

#### Scenario: 统一注入点
- **WHEN** 构建 CodeQL 生成 Agent 与 错误分析 Agent 的 Prompt
- **THEN** 使用相同的占位符映射与替换函数，保证行为一致

#### Scenario: 兼容性保障
- **WHEN** 占位符值为空或缺省
- **THEN** 替换为空字符串并保持流程可继续执行
