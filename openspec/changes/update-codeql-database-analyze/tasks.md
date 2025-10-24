## 1. Implementation
- [x] 1.1 更新 `utils/codeql.py`：切换到 `codeql database analyze`，增加 `--format` 与 `--output` 参数，命名使用调用时间戳
- [x] 1.2 更新 `tools/codeql_runner_tool.py`：对外暴露或默认传入上述参数，保证输出到 `/output/`
- [x] 1.3 若有相关单元测试，更新或新增用例，校验命令拼装与输出路径
- [ ] 1.4 本地验证：执行一次查询，确认生成 `/output/result_YYYYMMDD_HHMMSS.sarif`
- [ ] 1.5 运行 `openspec validate update-codeql-database-analyze --strict` 确认规范

## 2. Documentation
- [x] 2.1 在 `README.md` 或相关文档补充新的执行方式与输出格式说明
