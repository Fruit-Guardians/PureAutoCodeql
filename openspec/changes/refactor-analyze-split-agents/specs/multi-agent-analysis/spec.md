## MODIFIED Requirements

### Requirement: 模块化 Agent 架构
系统 SHALL 将多 Agent 分析功能组织为独立的模块结构，提升代码可维护性和扩展性。

#### Scenario: Agent 模块独立性
- **WHEN** 需要使用特定的分析 Agent（CVE、Java Sink、Java Source）
- **THEN** 每个 Agent SHALL 位于独立的模块文件中（`agents/` 目录下）
- **AND** 可以独立导入和测试，不依赖其他 Agent 的实现细节

#### Scenario: 工具函数模块化
- **WHEN** 需要使用通用工具函数（文件 I/O、Java 路径解析）
- **THEN** 工具函数 SHALL 位于独立的 `utils/` 模块中
- **AND** 可以被多个 Agent 或外部代码复用

#### Scenario: 主调度器职责分离
- **WHEN** 执行多 Agent 分析工作流
- **THEN** `Analyze.py` SHALL 作为主调度器，负责：
  - Agent 实例创建和配置管理
  - 工作流编排和数据流控制
  - 结果汇总和输出生成
- **AND** 不 SHALL 包含具体的分析逻辑实现

### Requirement: 构造注入依赖管理
系统 SHALL 通过构造函数注入方式管理 Agent 间的依赖关系，避免循环依赖。

#### Scenario: Agent 初始化
- **WHEN** 创建 Agent 实例
- **THEN** `MultiAgentAnalyzer` 实例 SHALL 通过构造函数传入各个 Agent
- **AND** Agent 不 SHALL 直接创建或持有全局的 LLM/MCP 客户端

#### Scenario: 配置共享
- **WHEN** 多个 Agent 需要相同的配置
- **THEN** `AgentConfig`、`AgentResult` 等共享类型 SHALL 保留在 `Analyze.py` 中
- **AND** 通过参数传递或依赖注入提供给各个 Agent

## ADDED Requirements

### Requirement: 模块文件结构
系统 SHALL 按照以下结构组织代码文件：

#### Scenario: Agent 模块组织
- **WHEN** 查看项目结构
- **THEN** SHALL 存在以下 Agent 模块文件：
  - `agents/cve_analysis_agent.py`：包含 `CVEAnalysisAgent` 类
  - `agents/java_sink_path_agent.py`：包含 `JavaPathAnalysisAgent` 类  
  - `agents/java_source_analysis_agent.py`：包含 `JavaSourceAnalysisAgent` 类
- **AND** 每个模块 SHALL 包含相应的 `__init__.py` 文件

#### Scenario: 工具模块组织
- **WHEN** 查看项目结构
- **THEN** SHALL 存在以下工具模块文件：
  - `utils/io.py`：包含 `read_json_text`、`write_analysis_output` 函数
  - `utils/java.py`：包含 `find_path_from_java_file` 函数
- **AND** 每个模块 SHALL 包含相应的 `__init__.py` 文件

### Requirement: 向后兼容性保证
系统 SHALL 在重构后保持完全的向后兼容性。

#### Scenario: 运行入口不变
- **WHEN** 用户执行 `python Analyze.py` 或调用 `main()` 函数
- **THEN** 程序行为 SHALL 与重构前完全一致
- **AND** 输出格式和内容 SHALL 保持不变

#### Scenario: 函数签名保持
- **WHEN** 调用任何迁移的函数或方法
- **THEN** 函数签名（参数、返回值类型）SHALL 保持完全不变
- **AND** 函数行为和副作用 SHALL 保持一致

#### Scenario: 错误处理一致性
- **WHEN** 遇到异常情况（文件不存在、JSON 解析错误、目录为空等）
- **THEN** 错误处理逻辑和用户反馈 SHALL 与重构前保持一致
- **AND** 异常类型和错误消息 SHALL 保持不变