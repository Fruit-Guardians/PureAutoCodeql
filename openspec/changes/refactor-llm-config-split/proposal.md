## Why
在当前实现中，LLM 的 API 与模型配置分散在各个文件中（例如 Analyze.py 中的 AgentConfig），不利于统一管理与切换。此次变更将：
- 将 LLM 配置集中到一个新的 config.py 文件中，便于统一维护与复用。
- 明确区分“思考（推理）模型”和“普通对话模型”的两类配置，满足 CodeQL 查询生成/验证更强推理需求。
- 统一策略：CodeQL 相关 Agent 使用 DeepSeek Reasoner，其余 Agent 使用 DeepSeek Chat。

## What Changes
- 新增 config.py，集中管理 LLM 配置：
  - 定义两类模型配置：think（思考/推理）、chat（普通对话）。
  - 暴露简洁的访问方式（常量/数据类/工厂函数），代码引用统一从 config.py 获取。
- 修改相关 Python 文件以适配：
  - 删除/替换分散的 AgentConfig，改为从 config.py 引用。
  - 为 CodeQL 相关 Agent 绑定 think（deepseek-reasoner）。
  - 为其他 Agent 绑定 chat（deepseek-chat）。
  - 注释使用中文，代码保持简洁。
- 不改变对外 CLI 接口与使用方式，仅为内部配置重构（但默认模型选择会变化，属行为变更）。

## Impact
- Affected specs: `llm-config`（新增能力）
- Affected code:
  - Analyze.py（移除内置 AgentConfig，改用 config.py 并支持按用途选择模型）
  - agents/*（若直接实例化 LLM 或使用 Analyzer 的 LLM，需要适配两类模型策略）
  - tools/*、GenerateCodeQL.py、rag_codeql_tool.py（若存在直接使用 LLM 的路径，需要引用 config.py 策略）
- 风险与缓解：
  - 行为变化：默认模型选择策略调整 → 在提案通过后统一修改并回归验证。
  - 密钥管理：鼓励使用环境变量/配置文件注入，避免硬编码；文档说明。
