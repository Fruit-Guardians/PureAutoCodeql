## 1. 实现清单
- [x] 1.1 新增 `config.py`，集中化 LLM 配置
- [x] 1.2 在 `config.py` 中提供两类模型配置：think（deepseek-reasoner）与 chat（deepseek-chat）
- [x] 1.3 `config.py` 支持从环境变量或安全存储读取 `API_KEY` 与 `BASE_URL`，避免硬编码
- [x] 1.4 为两类配置提供简单访问接口（如 `get_llm_config(role)` 或常量），并附中文注释
- [x] 1.5 重构 `Analyze.py`：移除本地 `AgentConfig`，改为引用 `config.py`，默认非 CodeQL 使用 chat
- [x] 1.6 重构 `Analyze_Python.py`：同上，默认非 CodeQL 使用 chat
- [x] 1.7 重构 `GenerateCodeQL.py`：将 LLM 获取切换为来自 `config.py` 的 think（用于 CodeQL 生成）
- [x] 1.8 审核 `agents/` 目录：
  - [x] 1.8.1 CodeQL 相关 Agent（`codeql_generator_agent.py`、`codeql_runner_agent.py`、`tools/codeql_compose.py` 若使用 LLM）强制使用 think
  - [x] 1.8.2 其他 Agent（`cve_analysis_agent.py`、`java_*`、`python_*` 等）默认使用 chat
- [x] 1.9 审核 `tools/`、`rag_codeql_tool.py` 等路径，若直接创建 LLM，统一改造为从 `config.py` 获取
- [x] 1.10 所有新增/修改代码的注释使用中文，保持实现简洁
- [x] 1.11 回归：运行示例流程与 CodeQL 生成，确认无回归与行为符合预期
- [x] 1.12 文档：在 README.md 或相关开发说明中，简述 `config.py` 使用方式

## 2. 校验与流程
- [x] 2.1 `openspec validate refactor-llm-config-split --strict`
- [x] 2.2 评审通过后再开始实现
- [x] 2.3 实现完成后更新勾选状态并提交 PR
