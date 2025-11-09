## Why
用户希望能够直接通过指定的MD文件开始CodeQL生成操作，而不需要完整的案例分析流程。这提供了更灵活的使用方式，允许用户跳过情报收集和语言检测步骤，直接使用已知的漏洞描述生成CodeQL查询。

## What Changes
- 在 `Analyze.py` 中添加新的 `--md-file` 命令行参数
- 创建新的异步函数 `run_md_direct_codeql()` 处理MD文件直接CodeQL生成
- 集成 `tools/codeql_compose.py` 中的默认函数进行CodeQL生成和运行
- 支持从MD文件中提取漏洞描述并直接传递给CodeQL生成工具
- 保持现有的案例分析功能不变

## Impact
- **Affected specs**: 新增 `analyze-interface` 规范
- **Affected code**: `Analyze.py` (主要入口文件), `tools/codeql_compose.py` (复用现有功能)
- **Breaking changes**: 否，向后兼容
- **New capabilities**: 支持MD文件直接CodeQL生成模式
