## ADDED Requirements

### Requirement: 集中化 LLM 配置与模型分层
系统 SHALL 在项目根目录新增 `config.py`，集中管理大语言模型（LLM）的 API 与模型配置，并将模型按用途分为两类：
- think（思考/推理）模型：用于 CodeQL 生成/执行等需要强推理的场景
- chat（普通对话）模型：用于常规分析与总结场景

`config.py` 至少包含：
- 数据结构：能够表达 `model`、`api_key`、`base_url`、`temperature`、`streaming`、`max_tokens`、`max_retries`
- 安全读取：`api_key`/`base_url` 支持优先从环境变量读取，避免硬编码
- 简洁接口：提供统一访问（如 `get_llm_config(role)` 或常量/工厂函数）
- 注释：中文，且保持实现简洁

#### Scenario: 环境变量优先
- WHEN 通过环境变量提供 `DEEPSEEK_API_KEY` 与 `DEEPSEEK_BASE_URL`
- THEN `config.py` 读取并优先使用该值
- AND 未设置时可回退到本地配置或安全存储（实现可选）

#### Scenario: 两类模型可用
- WHEN 其他模块调用 `config.py` 获取配置
- THEN 能获取 `think` 与 `chat` 两类配置对象
- AND 字段完整（见上）

### Requirement: 角色映射与默认策略
系统 SHALL 将不同用途的 Agent/工具绑定到对应模型：
- CodeQL 生成/纠错/执行相关（含生成器、纠错器、执行器与 Compose 工具链）→ 使用 `think`（deepseek-reasoner）
- 其它 Agent（如 CVE 分析、Java/Python 路径与源码分析等）→ 使用 `chat`（deepseek-chat）

受影响的主要文件（非穷尽）：
- Analyze.py（默认使用 chat，CodeQL 相关通过工具/Agent 使用 think）
- Analyze_Python.py（默认使用 chat）
- GenerateCodeQL.py（生成逻辑使用 think）
- agents/codeql_generator_agent.py、agents/codeql_runner_agent.py、tools/codeql_compose.py（统一使用 think）
- 其它直接实例化 LLM 或定义 AgentConfig 的位置

#### Scenario: CodeQL 生成使用推理模型
- WHEN 执行 CodeQL 生成/Compose/纠错/执行流程
- THEN 使用 `think` 配置（`model=deepseek-reasoner`）

#### Scenario: 常规分析使用聊天模型
- WHEN 运行 CVE 分析、Java/Python 路径/源码分析等非 CodeQL 生成/执行场景
- THEN 使用 `chat` 配置（`model=deepseek-chat`）

### Requirement: 迁移与兼容
系统 SHALL 移除分散在各文件中的 `AgentConfig`，统一改为从 `config.py` 引用；不改变 CLI 接口。

#### Scenario: 内联配置移除
- GIVEN 现有文件内存在本地 `AgentConfig` 或直接 `ChatOpenAI` 实例化
- WHEN 重构完成后
- THEN 统一由 `config.py` 提供配置，代码保持中文注释且实现简洁
