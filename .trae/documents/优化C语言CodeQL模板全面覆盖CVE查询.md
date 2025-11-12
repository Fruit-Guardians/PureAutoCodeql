## 仓库概览
- 语言与范围：以 Python 为主进行编排，集成 CodeQL 针对 C/C++、Python、Java 的查询生成与执行
- 关键目录：`core/`（管线）、`services/`（CodeQL执行与语法检查）、`agents/`（CVE/Source/Sink分析与生成）、`tools/`（查询合成）、`prompts/`（跨语言模板）、`projects/`（C/C++与Python查询样例与KB）、`utils/`（CLI封装与输出整合）
- C/C++资产：`prompts/c_template_ql.md` 为通用模板；示例查询在 `projects/C_QL/*.ql`；执行生成临时 `qlpack.yml` 并产出 `./output/result_*.sarif`
- 输出：管线汇总生成 `output/analysis_output_<CVE>_<timestamp>/<CVE>_output.md`，并整理 JSON 结果与路径选择报告

## 目标
- 将 C 语言模板参数化与知识库化，实现对常见模式的通用覆盖与快速个案定制
- 建立 CVE→模板参数的集中映射，减少人工填充与漏项
- 强化数据流、别名、跨函数建模以提升召回，同时通过精确限定与 Sanitizer 降低误报

## 模板增强（`prompts/c_template_ql.md`）
- 统一骨架：`import cpp` + `TaintTracking`，封装 `VulnConfig` 的 `isSource/isSink/isAdditionalFlowStep`
- 可插拔子模块：
  - 边界缺失类（memcpy/memmove/strcpy/sprintf/scanf族）
  - 长度校验与比较缺失（`len`, `sizeof`, `nbytes`）
  - 整数溢出/符号转换/指针算术越界
  - 解析/解码返回值误用（负值、NULL、错误码）
- 参数位点占位：函数名、危险API、参数索引、关键结构字段、文件/函数限定 `inTarget`
- 结构体字段跟踪与别名：增加 `isAdditionalFlowStep` 对 `->field`、数组索引、指针解引用的传播

## CVE知识库与参数映射（新增轻量 JSON）
- 新增 `projects/C_QL/c_cases.json`（结构：`cve`, `functions`, `dangerApis`, `paramIndex`, `keyVars`, `guards`, `files`）
- 在 `tools/codeql_compose.py` 加载并注入映射到模板占位；支持多模式合成（边界/格式化/溢出）
- 参考 Python KB 用法：`projects/python_kb/knowledge_base/cases.json` 的字段与标签体系

## 查询生成管线优化
- 入口：在 `core/pipeline.py` 的 `CodeQLGenerationStep` 接入 C KB 参数注入，支持多模板合成与LSP校验循环
- 临时包：规范 `utils/codeql.py:create_temporary_qlpack` 的 `qlpack.yml`（命名规则、版本锁定、语言包 `codeql/cpp-all`）
- 多变体生成：对单CVE支持多危险API/参数组合并行生成，统一结果聚合

## 误报/漏报控制
- 强化 `inTarget`：文件/函数/命名空间多条件 OR + 近邻调用上下文限定（调用链窗口）
- Guard/Sanitizer：把补丁中新增的边界检查、长度比较、返回值检查抽象成 `isSanitizer` 并用于路径剪枝
- 常见误报抑制：字符串常量源、零长度、`sizeof(dst)` 非 `sizeof(*dst)`、安全API变体（`snprintf` 限长）

## 数据流与别名建模
- 传播增强：结构体字段、数组切片、指针算术、`memcpy` 源/目的倒置等特殊步
- 跨函数：参数→返回值、回调与函数指针、虚调用/宏展开可见性策略
- 整数与边界：溢出与符号扩展在长度计算中的传播（参与 sink 参数）

## Sink/Source 体系
- Sink分类：内存操作（memcpy族/strcpy族）、格式化输出（printf/sprintf族）、输入解析（scanf/strtol族）、指针/索引写入
- Source分类：外部输入、网络/文件读取、结构体字段、函数返回值、长度/计数来源
- 统一危害标签：`buffer-overflow`, `integer-overflow`, `format-string`, `oob-read/write` 等，用于报告与覆盖统计

## Sanitizer 与 Guard库
- 建库：常见安全检查模式（`min(len, size)`, `bound <= cap`, `ret >= 0`, `ptr != NULL`）
- 模板调用：将 Guard 映射到 `VulnConfig.isSanitizer` 并在 `TaintTracking::Configuration` 应用

## 输出与反馈闭环
- 将 `services/path_selection/*` 的 coverage 与标签用于回填 KB 参数（调整 `dangerApis/paramIndex`）
- 在 `core/pipeline.py` 报告中记录每CVE的模板变体、命中/未命中及原因摘要
- 统一 JSON 命名与语言标注，便于后续统计

## 验证与基准
- 建测试语料：选取 30+ 典型 C/C++ CVE（含 PoC/补丁），每类模式≥5例
- 指标：Precision/Recall/F1、平均路径长度、误报原因分布；每次改动自动跑基准
- 单元测试：对模板合成器与占位注入进行解析与生成的稳定性测试

## 里程碑
- Phase 1：KB与模板占位统一；最小管线接入
- Phase 2：Sink/Source/Guard 库完善；误报控制落地
- Phase 3：跨函数/别名建模增强；覆盖扩大与基准化
- Phase 4：闭环反馈与统计面板；持续优化精度与召回