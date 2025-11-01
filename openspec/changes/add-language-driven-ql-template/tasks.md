## 1. 实施计划（Implementation）
- [x] 1.1 在 tools/codeql_compose.py 中新增模板解析函数：基于 default_language 返回对应 QL 模板内容。
- [x] 1.2 在 ql 生成与错误分析两个 Prompt 构建前，集中构造占位符映射，统一替换（包含 [[QL_TEMPLATE]]、[[ROUND_INDEX]]、[[LANGUAGE]] 等）。
- [x] 1.3 语言映射：java → agents/prompts/java_temple_ql.md，python → agents/prompts/python_template_ql.md。
- [x] 1.4 兼容性：当语言未知或模板文件缺失时，回退为空字符串或默认 Java 模板，不中断流程。

## 2. 回归与验证（Validation）
- [ ] 2.1 运行 openspec validate add-language-driven-ql-template --strict
- [ ] 2.2 执行最小用例，确认 CodeQLGenAgent 输出含规范骨架；错误分析 Agent 能引用同一套骨架上下文。
