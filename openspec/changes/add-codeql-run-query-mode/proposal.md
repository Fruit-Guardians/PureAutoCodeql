# 变更提案：为 CodeQLComposeTool 增加“常规查询（run）模式”，并保持现有 analyze->SARIF 模式不变

- 变更编号（change-id）：add-codeql-run-query-mode
- 相关能力：codeql-compose-tool
- 受影响模块：
  - tools/codeql_compose.py（新增执行模式与返回结构）
  - utils/codeql.py（新增基于 `codeql query run` + `bqrs decode` 的执行与结果导出工具）
  - config.py（如需新增可选配置项，例如结果导出目录、超时与解码格式）

## 背景与动机
当前系统中，CodeQL 查询执行主要通过 `codeql database analyze`，输出为 SARIF（并在 compose 成功时自动转换为同名 JSON 便于可视化）。在某些“常规查询/快速搜索”场景中，用户希望：
- 不使用 analyze，而是使用 `codeql query run` 直接得到原始查询结果（BQRS），再解码为可读文本。
- Agent 调用该模式能够直接拿到“查询结果内容”和“结果文件路径”。
- 查询结果内容以文本形式落盘到 `temp\search_temp` 目录，便于外部工具快速查看与下游处理。

## 目标
- 为 CodeQL 组合工具（CodeQLComposeTool）新增一个“常规查询（run）模式”。
- 保持现有 analyze→SARIF 的模式与返回完全不变（向后兼容）。
- 在 run 模式下，返回：
  - 执行状态与结果内容（Agent 可读）
  - 结果文件的保存路径（文本文件，位于 `temp\search_temp`）

## 非目标
- 不改动 compose 的生成与修复迭代流程逻辑，仅扩展执行与返回方式。
- 不对 SARIF→JSON 自动转换策略进行变更（仍沿用已存在的逻辑与配置）。

## 提议的方案（高层设计）
- 在 `tools/codeql_compose.py` 的 `CodeQLComposeTool` 中，新增一个可选参数 `exec_mode`：
  - `analyze`（默认）：保持现状，使用 `codeql database analyze`，输出 SARIF，维持当前返回信息结构；仍可进行 SARIF→JSON 自动转换。
  - `run`：新模式，使用 `codeql query run` 生成 BQRS，再调用 `codeql bqrs decode` 解码为文本，文本结果保存到 `temp\search_temp\query_<timestamp>.txt`，Agent 返回“结果内容的摘要/片段 + 文件路径”。
- 在 `utils/codeql.py` 中：
  - 新增工具函数（例如 `run_query_and_decode_to_text`）：
    - 输入：`query_content`, `database_path`, `language?`, `output_dir?`（默认为 `./temp/search_temp`）
    - 步骤：
      1) 调用 `create_temporary_qlpack` 产出临时 `.ql` 与 `qlpack.yml`。
      2) 运行 `codeql query run <query> --database <db> --output <bqrs>`。
      3) 运行 `codeql bqrs decode --format=table <bqrs>`（或其它合适的纯文本格式），得到文本结果。
      4) 将文本结果写入 `<output_dir>/query_<timestamp>.txt`，返回 `success`、`output`（原始解码文本或首段）、`result_file`（文本文件路径）。
    - 注意：创建目录、Windows 路径兼容、超时与错误处理。

## API / 返回结构（不破坏现有行为）
- `CodeQLComposeTool._arun(requirement: str, ..., exec_mode: str = 'analyze') -> str`
  - `exec_mode='analyze'`：保持原有返回字符串格式，包含 SARIF 路径与（若可用）路径 JSON 信息。
  - `exec_mode='run'`：返回字符串包含：
    - 简要的查询内容说明（可选）
    - “查询结果文件路径”：`temp\search_temp\query_<timestamp>.txt`
    - 可选的结果内容前若干行预览（避免输出过长）

## 验收标准（Acceptance Criteria）
- analyze 模式：
  - 使用先前的最小样例数据库与简单查询，执行后仍然产出 `output/result_*.sarif`，并保持 JSON 自动转换功能可用；原有 compose 流程不受影响。
- run 模式：
  - 在相同数据库与简单查询下，执行后生成 `temp\search_temp\query_<timestamp>.txt`，内容为可读文本（解码自 BQRS）。
  - `_arun(..., exec_mode='run')` 的返回中包含该文件路径，且能预览到部分查询结果内容（如果有结果）。
  - 无查询结果时，文件仍然创建且包含“空结果”或表头信息，返回信息明确标识无结果。

## 影响面
- 组件：`tools/codeql_compose.py`、`utils/codeql.py`，可能微调 `config.py`（可选）。
- 运行环境依赖：需要本地 `codeql` 可执行，且数据库构建正确；新模式使用到 `codeql bqrs decode` 子命令。

## 风险与缓解
- Windows 路径符号兼容：统一用 `pathlib.Path` 生成路径，最终以字符串返回。
- 性能与超时：为 `query run` / `bqrs decode` 设定合理超时，必要时可配置化。
- 输出大小：避免在返回字符串中直接携带过大的结果正文，仅返回片段 + 文件路径。

## 迁移/回滚计划
- 新增模式默认为关闭（不显式传入 `exec_mode='run'` 不会触发），对旧调用完全兼容。
- 如出现问题，可回退到仅 analyze 模式（不影响现有功能）。

## 时间线（建议）
- D1：提案评审与定稿
- D2：实现与自测（包含 Windows 与常见语言最小样例）
- D3：联调与验收
