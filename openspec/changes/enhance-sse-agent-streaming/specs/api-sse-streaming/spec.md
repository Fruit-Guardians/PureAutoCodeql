## ADDED Requirements

### Requirement: Agent信息事件传输
系统 SHALL 在SSE流式事件中包含当前执行的Agent信息，包括Agent名称和类型。

#### Scenario: Agent开始执行
- **WHEN** 任何Agent（CVE分析、Sink分析、Source分析、CodeQL生成）开始执行
- **THEN** 推送 `AGENT_START` 事件，包含 `agent_name`（如"CVEAnalysisAgent"）和 `agent_type`（如"cve_analysis"）

#### Scenario: Agent执行完成
- **WHEN** Agent执行完成（成功或失败）
- **THEN** 推送 `AGENT_COMPLETE` 事件，包含Agent名称、执行结果和耗时信息

#### Scenario: 多Agent串行执行
- **WHEN** 分析任务包含多个Agent顺序执行
- **THEN** 每个Agent的开始和完成事件按顺序推送，客户端能清晰识别当前执行的Agent

### Requirement: Agent思考过程流式输出
系统 SHALL 将Agent的思考过程实时推送到SSE流中，使客户端能观察Agent的推理步骤。

#### Scenario: Agent思考输出
- **WHEN** Agent生成思考内容（如通过 `show_thinking=True` 或内部日志）
- **THEN** 推送 `AGENT_THINKING` 事件，`message` 字段包含思考文本，`data` 字段包含Agent上下文

#### Scenario: 增量思考内容
- **WHEN** Agent的思考内容以流式方式生成（LLM流式输出）
- **THEN** 每个增量内容作为独立的 `AGENT_THINKING` 事件推送，客户端按序拼接

#### Scenario: 思考内容完整性
- **WHEN** Agent完成思考输出
- **THEN** 所有思考内容已通过多个 `AGENT_THINKING` 事件完整传输，无丢失或乱序

### Requirement: Agent工具调用事件
系统 SHALL 在Agent调用工具时推送相应事件，包含工具名称、参数和结果。

#### Scenario: 工具调用开始
- **WHEN** Agent调用工具（如CodeQL执行、文件读取、LSP查询）
- **THEN** 推送 `AGENT_TOOL_CALL` 事件，包含工具名称、调用参数

#### Scenario: 工具调用结果
- **WHEN** 工具调用返回结果
- **THEN** 在同一 `AGENT_TOOL_CALL` 事件或后续事件中包含执行结果和状态

#### Scenario: 工具调用失败
- **WHEN** 工具调用失败或超时
- **THEN** 推送 `AGENT_TOOL_CALL` 事件，`data.status` 标记为 "failed"，包含错误信息

### Requirement: 事件模型扩展
系统 SHALL 扩展 `StreamEvent` 模型以支持Agent相关字段。

#### Scenario: Agent字段可选
- **WHEN** 事件不是Agent级别的（如任务级别的 `STEP_START`）
- **THEN** `agent_name` 和 `agent_type` 字段为空或null，不影响现有事件

#### Scenario: 向后兼容
- **WHEN** 客户端使用旧版本的事件模型
- **THEN** 新增字段不影响旧客户端的正常工作，旧客户端忽略未知字段

#### Scenario: 新事件类型枚举
- **WHEN** 系统定义新的事件类型（`AGENT_START`、`AGENT_THINKING`、`AGENT_TOOL_CALL`、`AGENT_COMPLETE`）
- **THEN** 这些类型在 `StreamEventType` 枚举中定义，API文档中有明确说明

### Requirement: CLI模式兼容性
系统 SHALL 确保事件回调机制不干扰CLI模式的正常运行。

#### Scenario: 可选事件回调参数
- **WHEN** Agent方法接收 `event_callback` 参数
- **THEN** 该参数为可选参数，默认值为None，不传入时不影响Agent正常执行

#### Scenario: CLI模式下无事件推送
- **WHEN** CLI模式下运行Agent（`event_callback=None`）
- **THEN** 所有事件推送逻辑被跳过（通过 `if event_callback:` 条件判断），不产生额外开销

#### Scenario: CLI终端输出保持不变
- **WHEN** CLI模式下使用 `show_thinking=True`
- **THEN** 终端输出功能（emoji、格式化输出）保持原有行为，不受事件回调机制影响

#### Scenario: API模式与CLI模式独立
- **WHEN** API模式下传入 `event_callback`
- **THEN** 事件推送到SSE流，同时CLI的 `show_thinking` 可独立控制终端输出（两者不冲突）

