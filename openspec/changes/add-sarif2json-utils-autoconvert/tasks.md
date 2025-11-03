## 1. Implementation
- [x] 1.1 新增 `utils/sarif_utils.py`，将 `sarif2json.py` 的核心逻辑提取为可复用函数；保持输出 JSON 结构不变。
- [x] 1.2 在 `tools/codeql_compose.py` 中，CodeQL 执行成功后自动调用工具函数，输出与 SARIF 同名的 `.json` 文件到同目录（通常为 `output`）。
- [x] 1.3 在 `config.py` 中新增 `Sarif2JsonConfig`（`max_results`、`threadflow_index`、`rule_filter`），默认值分别为 3、0、None。
- [x] 1.4 调整 `sarif2json.py`：保留为薄 CLI 包装或迁移说明（避免重复实现）。
- [x] 1.5 为新增或修改的代码补充中文注释；不修改无关注释。
- [x] 1.6 用示例 SARIF 验证 JSON 生成：检查路径规范化、相对路径可选、数据结构字段完整。
- [ ] 1.7 必要时更新 README/开发说明（如已有相关文档）。

## 2. Validation
- [x] 2.1 运行 `openspec validate add-sarif2json-utils-autoconvert --strict`，修复格式/场景错误。
