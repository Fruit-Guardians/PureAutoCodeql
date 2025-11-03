## Why
为现有项目新增一个专用 Agent，将 `CVE-2021-21985.json` 转换为面向漏洞分析的 Markdown 文本，避免改动现有 `main.py` 的演示逻辑，分离职责、便于后续扩展。

## What Changes
- 新增 `Analyze.py`：基于 LangChain，沿用与 `main.py` 相同的 LLM 与 MCP 客户端配置。
- 通过代码读取 `CVE-2021-21985.json`，将其作为上下文，构造提示词让 Agent 输出为实际 Markdown 文本。
- 输出内容聚焦：仅打印转换完成的 Markdown 结果，不做额外样式修饰。
- 代码风格要求：风格简洁，仅在函数上添加注释（docstring）。

## Impact
- Affected specs: `cve-analysis-agent`
- Affected code: `Analyze.py`
- 非破坏性变更：不修改现有代码，仅新增文件。
