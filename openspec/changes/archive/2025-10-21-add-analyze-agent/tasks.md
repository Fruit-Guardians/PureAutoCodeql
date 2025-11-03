## 1. Implementation
- [x] 1.1 新增 `Analyze.py`，复用 `main.py` 的 LLM 与 MCP 客户端配置（相同 `ChatOpenAI` 模型参数、`MultiServerMCPClient` 服务器设置）。
- [x] 1.2 在代码中读取 `CVE-2021-21985.json` 文件内容作为上下文文本。
- [x] 1.3 构造 Prompt，引导 Agent 将该 JSON 转换为 Markdown，重点描述利用类型、漏洞点、影响范围、利用条件、修复建议等，且只输出转换后的结果。
- [x] 1.4 使用 LangChain 创建 Agent，执行并将结果直接打印到标准输出。
- [x] 1.5 代码风格保持简洁，仅在函数上添加注释（docstring），不对输出样式进行额外修饰。
- [x] 1.6 不修改现有代码，仅新增文件。

## 2. Validation
- [ ] 2.1 在本地运行 `Analyze.py`，确认能读取 `CVE-2021-21985.json` 并输出 Markdown。
- [ ] 2.2 模拟异常：文件缺失、JSON 非法、模型不可用，确认能给出清晰错误信息（可直接抛出异常或简短提示，保持风格简洁）。
- [ ] 2.3 与 `main.py` 的依赖与运行环境兼容（如相同的 `pyproject.toml` 配置能运行通过）。
