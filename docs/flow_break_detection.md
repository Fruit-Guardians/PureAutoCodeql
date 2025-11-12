---
title: Flow Break Detection & Auto Patching
---

# Flow Break Detection & Auto Patching

为了解决 CodeQL 查询在首次执行时因缺失 `isAdditionalFlowStep` 而提前“断流”的问题，系统在 Compose 流程中新增了“断流检测 → 补边生成 → 重跑”能力。本文件从 **开发实现** 与 **运维使用** 两个角度介绍该功能。

---

## 1. 实现概览（开发文档）

### 1.1 架构组件

文件：`services/flow_break_detection.py`

| 组件 | 描述 |
|------|------|
| `FlowQueryExtractor` | 从现有查询中抽取 Source / Sink / isAdditionalFlowStep 定义及代码片段 |
| `FlowDetectionSkeletonBuilder` | 按照 OpenSpec 骨架生成断流检测查询，复用已有 Source/Sink/补边逻辑 |
| `FlowBreakDetectionManager` | 协调整个检测流程（抽取 → 构建 → 执行 → 解析 → 合并） |
| `FlowBreakClauseGenerator` | 根据断流候选生成去重后的补边子句（默认使用同行列比对策略） |
| `FlowBreakQueryMerger` | 将新子句安全合并入原查询，自动注入 `FlowBreakSupport` 帮助模块 |

### 1.2 核心流程

1. **抽取组件**：从原查询解析 Source/Sink/isAdditionalFlowStep 的谓词定义，如缺失补边谓词则生成 `none()` 默认实现；
2. **骨架构建**：替换 `<SOURCE>/<SINK>/<ISADDITION>` 占位符，构造固定 Source/Sink + ANY 交集模式；
3. **断流执行**：以独立 QL 文件运行检测查询，输出包含候选节点的 SARIF；
4. **候选解析**：读取 SARIF，记录文件/行/列/提示信息，超出阈值时做截断；
5. **子句生成**：按配置限额生成补边子句，对同一断流点去重；
6. **查询合并**：注入 `FlowBreakSupport::connectSameLine` 帮助方法，将补边子句以 `or` 形式并入原 `isAdditionalFlowStep`；
7. **遥测记录**：输出检测轮次、候选数量、生成子句等信息到日志，便于调试。

### 1.3 Compose 集成点

文件：`tools/codeql_compose.py`

- 在 `_arun` 循环中，当查询执行成功但结果为空时优先尝试 flow break；
- 每轮至多调用一次检测，默认允许 **3 轮迭代**；
- 若补边成功，直接重跑查询（无需重新生成 QL）；
- 未命中或达到限制时，退回原有的 LLM 重试机制。

### 1.4 测试覆盖

新单测：`test/test_flow_break_detection.py`

- `FlowQueryExtractor` 抽取准确性；
- 骨架模板替换；
- 子句生成的限额与去重；
- 合并逻辑对 `isAdditionalFlowStep` 的覆盖与 helper 插入。

---

## 2. 运维与配置

### 2.1 开关与参数

均可通过环境变量调整：

| 变量 | 默认值 | 说明 |
|------|-------|------|
| `ENABLE_FLOW_BREAK_DETECTION` | `true` | 全局开关 |
| `MAX_FLOW_PATCH_ROUNDS` | `3` | 单个查询允许的补边迭代次数 |
| `MAX_FLOW_PATCH_CLAUSES_PER_ROUND` | `5` | 每轮最多生成的补边子句 |
| `MAX_FLOW_PATCH_TOTAL_CLAUSES` | `12` | 整体流程允许的子句总数 |
| `FLOW_BREAK_SOFT_LIMIT` | `200` | 断流候选节点软上限（超过则截断） |
| `FLOW_BREAK_CANDIDATE_CAP` | `50` | 实际采样上限 |
| `FLOW_BREAK_MIN_COL_GAP` | `1` | 同行连接时的最小列偏移，避免自环 |
| `FLOW_BREAK_TELEMETRY` | `true` | 是否输出遥测日志 |

> ⚠️ **建议**：在大规模数据库上，如需控制耗时，可适当降低 `MAX_FLOW_PATCH_ROUNDS` 或增大软上限与截断差距。

### 2.2 监控与日志

- 断流检测会通过 `logging` 输出 `Flow break telemetry` 结构化信息；
- 常见字段：轮次、候选总数、截断数量、生成子句数、执行错误等；
- 当检测执行失败或 SARIF 抽取异常时，会记录 `reason` 并自动降级。

### 2.3 降级策略

- 检测失败 / 达到候选上限 / 无新子句时：保持原查询与重试逻辑；
- 累计补边子句超过 `MAX_FLOW_PATCH_TOTAL_CLAUSES`：停止生成，直接进入空结果重试；
- 若需要彻底禁用该能力，可设置 `ENABLE_FLOW_BREAK_DETECTION=false`。

### 2.4 运维自检

1. 检查环境变量是否按预期下发；
2. 关注日志中是否存在重复的“Flow break telemetry”错误；
3. 可通过单测 `pytest test/test_flow_break_detection.py` 进行快速回归。

---

## 3. 常见问题 FAQ

**Q: 断流检测会修改原查询的语法结构吗？**  
A: 只有在补边成功时才会修改 `isAdditionalFlowStep` 的谓词体，原有注释和结构保持不变。

**Q: 生成的补边子句是否可能引入噪声？**  
A: 默认策略基于同一行的节点左右关系构建补边，并设置最小列间距与去重逻辑；如需更严格过滤，可收紧候选上限或禁用该功能。

**Q: 如何在日志中快速识别补边轮次？**  
A: 关注 `Flow break telemetry` 的 `round` 字段，以及是否存在 `generated_clause_count`。

---

如需进一步扩展补边策略，可在 `FlowBreakClauseGenerator` 中新增更精细的分类与子句模板，并确保单测覆盖新逻辑。

