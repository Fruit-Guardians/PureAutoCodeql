# codeql-generation-agent Specification

## ADDED Requirements

### Requirement: 错误修复模式选择
CodeQL Error Agent SHALL 支持两种修复模式：`generate`（生成新查询）和 `inplace`（原地修改现有文件）。

#### Scenario: 生成模式（默认）
- **WHEN** Error Agent 在 `fix_mode='generate'` 模式下运行
- **THEN** 加载 `prompts/codeql_erroranalyze.md` prompt，指导 LLM 生成全新的 CodeQL 查询代码

#### Scenario: 原地修复模式
- **WHEN** Error Agent 在 `fix_mode='inplace'` 模式下运行
- **THEN** 加载 `prompts/codeql_fix_inplace.md` prompt，指导 LLM 使用 MCP 文件系统工具修改现有文件

#### Scenario: 模式切换时机
- **WHEN** CodeQL 查询首次生成成功后，进入错误修复迭代
- **THEN** Error Agent 自动切换到 `fix_mode='inplace'` 模式

### Requirement: 原地修复 Prompt
系统 SHALL 提供专门的 prompt 文件 `prompts/codeql_fix_inplace.md`，指导 LLM 使用 `@modelcontextprotocol/server-filesystem` 工具的正则替换功能。

#### Scenario: Prompt 包含文件路径
- **WHEN** Error Agent 构建原地修复 prompt
- **THEN** 注入 `[[QL_FILE_PATH]]` 占位符，包含当前 .ql 文件的完整绝对路径

#### Scenario: Prompt 指导工具使用
- **WHEN** LLM 读取 `codeql_fix_inplace.md` prompt
- **THEN** prompt 明确说明：
  - 使用 `@modelcontextprotocol/server-filesystem` 工具
  - 使用正则替换（regex replace）功能
  - 目标文件路径为 `[[QL_FILE_PATH]]`
  - 提供具体的修复操作示例

#### Scenario: Prompt 包含错误上下文
- **WHEN** 构建原地修复 prompt
- **THEN** 包含 `[[ERROR_LOG]]`、`[[CURR_QL_CONTENT]]`、`[[ROUND_INDEX]]` 等现有占位符，保持上下文完整性

### Requirement: 文件路径注入
Error Agent SHALL 在构建 prompt 时注入当前工作区中 .ql 文件的完整路径。

#### Scenario: 从工作区获取文件路径
- **WHEN** Error Agent 在原地修复模式下构建 prompt
- **THEN** 从 `temp/codeql_temp/<task_id>` 工作区中定位 .ql 文件，获取其绝对路径

#### Scenario: 路径格式正确性
- **WHEN** 注入 `[[QL_FILE_PATH]]` 占位符
- **THEN** 使用绝对路径格式（如 `/home/user/project/temp/codeql_temp/task_123/query.ql`），确保 MCP 工具能够准确定位文件

### Requirement: 占位符扩展
Error Agent 的 `build_prompt` 方法 SHALL 支持新的 `ql_file_path` 参数，并将其替换到 prompt 模板中。

#### Scenario: 占位符替换逻辑
- **WHEN** 调用 `build_prompt(ql_file_path='/path/to/file.ql', ...)`
- **THEN** 模板中的 `[[QL_FILE_PATH]]` 被替换为 `/path/to/file.ql`

#### Scenario: 可选参数处理
- **WHEN** `ql_file_path` 参数为 `None` 或未提供
- **THEN** `[[QL_FILE_PATH]]` 被替换为空字符串，不影响其他占位符的替换

## MODIFIED Requirements

### Requirement: CodeQL Error Agent 初始化
系统 SHALL 提供 `CodeQLErrorAgent` 类，用于诊断和修复 CodeQL 编译/运行时错误。该 Agent 从 `prompts/codeql_erroranalyze.md` 或 `prompts/codeql_fix_inplace.md` 加载 prompt 模板。

#### Scenario: 默认 Prompt 路径
- **WHEN** 创建 `CodeQLErrorAgent` 实例，未指定 `prompt_file` 参数
- **THEN** 默认使用 `prompts/codeql_erroranalyze.md` 作为 prompt 模板路径

#### Scenario: 自定义 Prompt 路径
- **WHEN** 创建 `CodeQLErrorAgent` 实例，指定 `prompt_file` 参数
- **THEN** 使用指定的 prompt 文件路径

#### Scenario: 动态切换 Prompt
- **WHEN** Error Agent 需要在不同修复模式下使用不同 prompt
- **THEN** 根据 `fix_mode` 参数动态选择加载 `codeql_erroranalyze.md` 或 `codeql_fix_inplace.md`

