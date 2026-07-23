# Language capability matrix

| 能力 | Java | Python | C/C++ |
| --- | --- | --- | --- |
| Source/Sink 定位 | 完整 | 完整 | 完整 |
| Source→Sink 路径结构 | 完整 | 完整 | 完整 |
| CodeQL 模块化 DataFlow API | 支持 | 支持 | 支持 |
| LSP | CodeQL LSP | CodeQL LSP | CodeQL LSP |
| 断流恢复 | 可配置 | 可配置 | 可配置 |
| Source/Sink 回退 | 可配置 | 可配置 | 可配置 |
| PR 真实黄金案例 | 路径穿越 | 命令注入 | 缓冲区问题 |

能力由 `pure_auto_codeql.services.language_capabilities` 注册。未知语言必须显式
跳过或失败；不得返回伪成功空结果。Source/Sink 候选统一包含文件、行号、符号、
证据、置信度和验证状态。文件不存在、行号或证据缺失时只能是 `unverified`。
