## Why
现有的多Agent分析系统已经具备CVE分析Agent和Java路径分析Agent（用于查找Sink点），但缺少专门用于查找Source点的Agent。为了完善漏洞分析工作流，需要新增一个Source分析Agent，与现有的Sink分析Agent形成完整的数据流分析能力。同时需要一个简单的输出函数将Source和Sink的分析结果整合到统一的输出文件中。

## What Changes
- 新增 `JavaSourceAnalysisAgent` 类：专门用于分析Java源代码中的Source点（数据来源点）
- 该Agent接收与 `JavaPathAnalysisAgent`（Sink分析Agent）相同的输入数据：CVE分析结果和Java文件路径
- 新增 `write_analysis_output` 函数：将Source和Sink两个Agent的分析结果写入到 `output.md` 文件
- 更新 `run_multi_agent_analysis` 函数：集成第三个Agent并调用输出函数
- 保持代码风格一致：简洁高效，仅在函数上添加docstring注释

## Impact
- Affected specs: `source-analysis-agent`
- Affected code: `Analyze.py`
- 非破坏性变更：扩展现有多Agent架构，不修改现有Agent的功能
- 增强分析能力：提供完整的Source-to-Sink数据流分析