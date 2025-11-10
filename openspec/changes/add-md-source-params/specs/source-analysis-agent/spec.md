## ADDED Requirements
### Requirement: MD文档和源代码路径参数支持
系统 SHALL 在 `Analyze.py` 中支持 `--src-path` 参数，与 `--md-file` 参数配合使用，用于指定源代码路径以生成source点查询报告。

#### Scenario: 指定MD文档和源代码路径
- **WHEN** 用户使用 `--md-file` 和 `--src-path` 参数
- **THEN** 系统 SHALL 读取MD文档内容作为漏洞描述
- **AND** 使用指定的源代码路径进行source点分析
- **AND** 生成source点查询报告而非仅生成CodeQL查询

#### Scenario: 验证源代码路径有效性
- **WHEN** 用户提供了 `--src-path` 参数
- **THEN** 系统 SHALL 验证路径存在性
- **AND** 如果路径不存在，显示错误信息并退出

#### Scenario: 源代码语言自动检测
- **WHEN** 用户提供了 `--src-path` 参数但未指定语言
- **THEN** 系统 SHALL 自动检测源代码的主要编程语言
- **AND** 使用检测到的语言进行source点分析

### Requirement: MD源代码分析执行函数
系统 SHALL 提供 `run_md_source_analysis` 函数，处理MD文档和源代码路径的组合分析。

#### Scenario: 执行MD源代码分析
- **WHEN** 调用 `run_md_source_analysis` 函数
- **THEN** 读取并解析MD文档内容
- **AND** 扫描指定的源代码路径
- **AND** 调用UnifiedSourceAnalysisAgent进行source点分析
- **AND** 生成结构化的分析报告

#### Scenario: 分析报告输出
- **WHEN** MD源代码分析完成
- **THEN** 输出包含source点位置、类型和风险等级的报告
- **AND** 支持Markdown格式输出
- **AND** 可选择保存到指定文件

## MODIFIED Requirements
### Requirement: 命令行参数解析
系统 SHALL 更新 `parse_arguments` 函数，添加新的 `--src-path` 参数支持。

#### Scenario: 添加源代码路径参数
- **WHEN** 解析命令行参数
- **THEN** 支持 `--src-path` 参数指定源代码路径
- **AND** 与现有的 `--md-file` 参数配合使用
- **AND** 提供相应的帮助信息和使用示例

### Requirement: 主函数执行逻辑
系统 SHALL 更新 `main` 函数，支持新的MD源代码分析模式。

#### Scenario: 检测MD源代码分析模式
- **WHEN** 用户同时提供 `--md-file` 和 `--src-path` 参数
- **THEN** 调用 `run_md_source_analysis` 函数
- **AND** 而非调用现有的 `run_md_direct_codeql` 函数
