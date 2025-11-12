## ADDED Requirements
### Requirement: 断流检测优先于空结果重试
系统 SHALL 在“空结果重试机制”之前执行一次“断流检测→自动补边生成→带补边重跑”的流程，以提升查询命中率与收敛速度。

#### Scenario: 使用固定 Source/Sink 与 ANY 交集法定位断流点
- **WHEN** 上一轮已生成并执行了带有 Source/Sink 的数据流查询
- **THEN** 系统 SHALL 构造并执行断流检测查询，复用相同的 Source/Sink 与既有 `isAdditionalFlowStep` 条件，按照如下骨架（仅示意，实际实现中以等价语义为准）：

```ql
/** ====== 与上个查询相同的 Source/Sink 和补边 ====== */
class FixedSourceNode extends DataFlow::Node { FixedSourceNode() { <SINK> } }
class FixedSinkNode   extends DataFlow::Node { FixedSinkNode()   { <SOURCE> } }

predicate anyNode(DataFlow::Node n) { exists(DataFlow::Node m | m = n) }

module ForwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { src instanceof FixedSourceNode }
  predicate isSink(DataFlow::Node sink)  { anyNode(sink) }
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { <ISADDITION> }
}
module F = TaintTracking::Global<ForwardCfg>;

module BackwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { anyNode(src) }
  predicate isSink(DataFlow::Node sink)  { sink instanceof FixedSinkNode }
}
module B = TaintTracking::Global<BackwardCfg>;

predicate forwardReachable(DataFlow::Node n) { exists(DataFlow::Node s, DataFlow::Node t | F::flow(s, t) and t = n) }
predicate backwardReachable(DataFlow::Node n) { exists(DataFlow::Node s, DataFlow::Node t | B::flow(s, t) and s.getLocation().getFile() = n.getLocation().getFile()) }

from DataFlow::Node n
where forwardReachable(n) and backwardReachable(n) and inAssignOrInit(n)
select n.getLocation()
```

#### Scenario: 从断流点自动生成补边规则
- **WHEN** 断流检测返回若干候选断流点
- **THEN** 系统 MUST 基于断流点位置与节点语境，自动生成若干 `isAdditionalFlowStep(src,dst)` 条件子句，至少覆盖：
  - 赋值/初始化：为 `lhs ← rhs` 生成 `isAdditionalFlowStep(rhs, lhs)`
  - 字段/属性：为 `base.field` 生成 `isAdditionalFlowStep(base, base.field)` 或等价语义
  - 集合/数组：为 `arr[i] ← val` 生成 `isAdditionalFlowStep(val, arr[i])`
  - 调用/返回：为 `y ← f(x)` 生成 `isAdditionalFlowStep(x, param)`、`isAdditionalFlowStep(ret, y)` 的等价跨边补全（以语言/库语义映射为准）
- **AND** 每轮新增的子句数量 SHALL 受上限控制（例如 ≤ 5），并进行去重与环路避免

#### Scenario: 合并补边并迭代执行
- **WHEN** 生成了新的补边条件
- **THEN** 系统 SHALL 将这些子句合并到原始查询的 `isAdditionalFlowStep` 条件中重跑
- **AND** 若仍为空结果，可继续“检测→生成→重跑”的迭代，直到出现结果或达到迭代上限（例如 ≤ 3 轮）

#### Scenario: 失败与降级
- **WHEN** 断流检测或补边生成失败，或达到上限后仍无结果
- **THEN** 系统 SHALL 回退到既有“空结果检测/交互”机制，维持向后兼容行为

#### Scenario: 可观测性
- **WHEN** 执行断流检测与补边生成流程
- **THEN** 系统 SHALL 记录断流点位置、生成的子句、迭代次数与最终结果，供排障与调优


### Requirement: 集成边界与配置
为避免与既有“空结果检测/交互”（见 `services/codeql_execution.py` 与 `tools/codeql_compose.py` 的空结果重试逻辑）重复与冲突，本能力的编排 SHALL 落在“查询生成/执行的迭代层”（即 Compose 层），不修改执行服务的空结果提示行为。

#### Scenario: 执行顺序约束
- **WHEN** 一次查询执行成功但无路径结果
- **THEN** 先尝试“断流检测→补边→带补边重跑（有限迭代）”，仅当仍为空时，才进入既有“空结果重试/交互”策略

#### Scenario: 可配置开关与上限
- **THEN** 提供以下可配置项（默认值建议）
  - `enableFlowBreakDetection = true`
  - `maxFlowPatchRounds = 3`
  - `maxClausesPerRound = 5`
  - `maxTotalClauses = 12`
  - 去重策略：文本去重 + 语义等价去重（按 src/dst 规范化）


### Requirement: 提取与占位替换规则
系统 SHALL 能从“上一轮已执行的查询文本”中稳健地抽取 Source/Sink 定义与原有 `isAdditionalFlowStep` 条件，并替换至断流检测骨架中的 `<SOURCE>/<SINK>/<ISADDITION>` 占位符。

#### Scenario: 多种写法的稳健抽取
- **WHEN** 上一轮查询的 Source/Sink 写法不同（如 ConfigSig 的 `isSource/isSink`、或显式 `class MySource extends DataFlow::Node` 等）
- **THEN** 通过正则+简单 QL 语法启发组合抽取到语义等价片段，优先抽取“完整谓词/类体”以便无缝嵌入

#### Scenario: 追加补边而非覆盖
- **WHEN** 已存在 `isAdditionalFlowStep` 条件
- **THEN** 将生成的新子句并入（OR 连接）而非覆盖原条件；若不存在则创建整条谓词

#### Scenario: inAssignOrInit 的等价约束
- **WHEN** 目标语言或库不具备 `inAssignOrInit` 辅助谓词
- **THEN** 以等价语义（赋值/初始化/声明处）筛选节点，或在骨架中以注释指示由模板/知识库替换


### Requirement: 语言与规则覆盖
系统 SHALL 覆盖 Java / Python / C/C++ 三类语言的常见断流模式，并为库/API 特性保留扩展点。

#### Scenario: Java 覆盖
- **THEN** 至少覆盖：
  - 赋值/字段/属性：`base.field ← expr`；集合 `list.add(x)`，`map.put(k,v)`；数组 `arr[i] ← v`
  - 方法调用：参数流入 `param_i`、返回值流出 `ret`，常见容器/流式 API 的封装流转

#### Scenario: Python 覆盖
- **THEN** 至少覆盖：
  - 赋值/字典/列表/切片：`d[k] ← v`、`lst[i] ← v`、`obj.attr ← v`
  - 调用返回/动态属性：`y ← f(x)`、`setattr/getattr`、常用库的容器传播

#### Scenario: C/C++ 覆盖
- **THEN** 至少覆盖：
  - 指针与结构体字段：`p->field ← v`、`(*p).field ← v`
  - 数组与缓冲区：`buf[i] ← v`、memcpy/memmove 等 API 的数据传播语义


### Requirement: 退避与跳过条件
为避免误报与性能回退，系统 SHALL 定义跳过与降级条件。

#### Scenario: 候选过多
- **WHEN** 单次断流检测返回候选节点 > 200
- **THEN** 进行基于文件/函数的采样或优先级排序（靠近 Sink 的优先），仅选取前 N（如 50）生成补边

#### Scenario: 无法抽取 Source/Sink
- **WHEN** 无法从上一轮查询解析出 Source/Sink
- **THEN** 跳过断流检测，直接进入既有空结果重试/交互流程

#### Scenario: 解析/执行错误
- **WHEN** 断流检测 QL 语法错误或执行失败
- **THEN** 不中止主流程，记录错误并降级到既有空结果策略


### Requirement: 性能与安全

#### Scenario: 性能约束
- **THEN** 单轮断流检测与补边合并总耗时建议 ≤ 30s（数据库大小依赖），不显著劣化整体吞吐

#### Scenario: 安全与稳健性
- **THEN** 所有自动生成的补边子句必须可追溯（日志记录来源断流点与规则），并可一键禁用；避免自环/重复/跨文件不合理连接

