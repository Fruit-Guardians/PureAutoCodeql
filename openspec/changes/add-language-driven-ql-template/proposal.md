## Why
在 CodeQL 生成与错误分析两个 Prompt 中存在占位符 [[QL_TEMPLATE]]，但当前未由语言进行驱动替换，且替换逻辑分散。为了规范化并提升一致性，需要在工具层（tools/codeql_compose.py）集中完成占位符替换，并根据 default_language 注入对应语言的 QL 模板骨架，保证 Java/Python 的可用性与向后兼容。

## What Changes
- 在 tools/codeql_compose.py 内集中处理 Prompt 占位符替换，统一注入 [[QL_TEMPLATE]] 与其他已有占位符。
- 当 default_language 为 java 时，从 agents/prompts/java_temple_ql.md 读取模板内容，替换 [[QL_TEMPLATE]]。
- 当 default_language 为 python 时，从 agents/prompts/python_template_ql.md 读取模板内容，替换 [[QL_TEMPLATE]]。
- 对未知语言或模板缺失的情况，提供向后兼容的回退策略（例如：空字符串或默认 Java 模板），不影响原有行为。
- 改动仅影响 Prompt 组装逻辑，CodeQLGenAgent 与 CodeQLErrorAgent 的类设计不变。

## Impact
- 受影响代码：tools/codeql_compose.py（集中化替换入口）。
- 受影响文档/提示词：agents/prompts/codeql_generate.md、agents/prompts/codeql_erroranalyze.md（依赖 [[QL_TEMPLATE]]）。
- 行为影响：确保在不同语言下，Prompt 能注入正确的 QL 模板骨架，提升生成质量，保持向后兼容。
