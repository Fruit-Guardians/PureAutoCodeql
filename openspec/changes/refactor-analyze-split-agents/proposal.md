## Why
当前 `Analyze.py` 将多 Agent（CVE 分析、Java Sink 路径分析、Java Source 分析）与通用工具函数混合在单文件中，导致：
- 代码结构复杂、维护成本高，扩展新 Agent 或复用工具不便
- Agent 之间的职责边界不清晰，易产生耦合
- 测试与演进困难（无法按模块进行单元测试和替换）

为提升可维护性与扩展性，拟将三个 Agent 抽离为独立模块（文件），保留 `Analyze.py` 作为主调用与 Agent 创建入口，同时抽离通用工具函数为独立模块。

## What Changes
- 目录与文件重构（仅组织结构调整，不改动现有功能和行为）：
  - 保留 `Analyze.py` 作为 orchestrator（主流程控制、Agent 创建、工作流编排、输出汇总）
  - 新增 `agents/` 目录，拆分三类 Agent：
    - `agents/cve_analysis_agent.py`：仅负责 CVE JSON → Markdown 的分析
    - `agents/java_sink_path_agent.py`：仅负责 Java 路径收集与 Sink 线索分析（保留对 diff 的输入处理）
    - `agents/java_source_analysis_agent.py`：仅负责 Source 点识别与标注
  - 新增 `utils/` 目录，抽离通用函数：
    - `utils/io.py`：`read_json_text(path)`, `write_analysis_output(...)`
    - `utils/java.py`：`find_path_from_java_file(java_file_path, source_root)`（保持签名不变）
  - 如需共享配置与客户端：
    - `agents/base.py`（可选）：定义 `AgentConfig`、`AgentResult` 与复用的 `MultiAgentAnalyzer`/LLM/MCP 初始化逻辑，或将其保留在 `Analyze.py` 并通过构造注入到各 Agent（优先选择构造注入，减少跨模块依赖）
- 导入路径更新：`Analyze.py` 通过 `from agents.* import ...` 与 `from utils.* import ...` 使用新模块
- 保持所有类/函数对外接口不变（行为与输入输出一致），以确保主流程与现有使用方式无需变动

## Impact
- Affected specs: `multi-agent-analysis`（仅内部结构优化，不涉及功能变更）
- Affected code:
  - 拆分并迁移：`CVEAnalysisAgent`、`JavaPathAnalysisAgent`、`JavaSourceAnalysisAgent`
  - 抽离并迁移：`find_path_from_java_file`、`read_json_text`、`write_analysis_output`
  - 保持：`Analyze.py` 入口与主流程、`run_multi_agent_analysis`/`main`
- 向后兼容：
  - 运行入口与命令使用方式保持不变（仍通过 `Analyze.py` 执行）
  - Agent 行为与输出结构保持不变（Markdown 报告、Source/Sink 分析汇总）

## Proposed File Layout
```
/ (project root)
├── Analyze.py                  # 主调用/工作流编排/Agent 创建
├── agents/
│   ├── cve_analysis_agent.py   # CVE → Markdown
│   ├── java_sink_path_agent.py # Java Sink 路径/差异分析
│   └── java_source_analysis_agent.py # Source 点识别
└── utils/
    ├── io.py                   # read_json_text, write_analysis_output
    └── java.py                 # find_path_from_java_file
```
（可选）若需共享基础类型与初始化逻辑：
```
agents/base.py                 # AgentConfig, AgentResult, MultiAgentAnalyzer（或保留于 Analyze.py 并以依赖注入）
```

## Non-Goals（本次不做）
- 不更改 Agent 的提示词、算法或数据流逻辑（除必要的导入路径调整）
- 不引入新的外部依赖或改变 LLM/MCP 的初始化参数与使用方式
- 不修改 JSON/CVE/Java 文件的读取路径和工作目录配置

## Risks / Trade-offs
- 可能引入相对导入或循环依赖风险：通过“构造注入”传入 `analyzer`/配置对象，避免在模块顶部初始化全局客户端
- 需要小范围修正导入路径：`Analyze.py` 与测试脚本需更新为新模块路径
- 若未来添加更多 Agent：建议统一定义 `BaseAgent` 接口（可在 `agents/base.py` 提供最小约束）以规范输入/输出

## Migration Plan
1. 创建 `agents/` 与 `utils/` 目录并新增文件（仅移动与拆分，不改行为）
2. 将三个 Agent 类移动至对应文件；将工具函数移动至 `utils/`
3. 在 `Analyze.py` 更新导入路径，保持主流程、函数签名与打印输出不变
4. 运行现有流程，确认输出与行为一致（CVE → Sink → Source → `output.md`）
5. 验证异常路径与错误处理保持一致（文件缺失、JSON 解析异常、目录为空）

## Acceptance Criteria
- 以 `Analyze.py` 运行整套流程，输出与当前版本一致，无行为差异
- 三个 Agent 与工具函数成功拆分至独立文件，导入路径正确
- 无循环依赖，LLM/MCP 初始化在单处复用（保留在 `Analyze.py` 或 `agents/base.py`）
- 单元测试/脚本可分别引用各 Agent 与工具函数进行独立测试

## Dependencies
- 现有依赖保持不变：`langchain_openai.ChatOpenAI`、`langchain_mcp_adapters.MultiServerMCPClient`、`@modelcontextprotocol` 工具、`ripgrep`（如有）等
- 文件路径与工作目录：`C:\Projects\Temp\AgentCodeqlTest`、`h5-vsan-service.jar_Decompiler.com`、`CVE-2021-21985.json`、`CVE-2021-21985.diff`