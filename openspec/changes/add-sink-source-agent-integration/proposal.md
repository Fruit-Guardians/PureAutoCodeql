# 变更提案：将Sink和Source查找Agent集成到CodeQLComposeTool

- 变更编号（change-id）：add-sink-source-agent-integration
- 相关能力：source-analysis-agent, codeql-compose-tool
- 受影响模块：
  - agents/unified_sink_path_agent.py（修改提示词，要求使用CodeQLComposeTool）
  - agents/unified_source_analysis_agent.py（修改提示词，要求使用CodeQLComposeTool）
  - prompts/sink_prompt_manager.py（更新提示词模板）
  - prompts/source_prompt_manager.py（更新提示词模板）

## 背景与动机
当前系统中，Sink和Source查找Agent使用文件工具直接分析源代码来查找潜在的危险函数调用和数据源点。这种方式存在以下问题：
- 依赖静态文本分析，可能遗漏复杂的代码模式
- 无法利用CodeQL的语义分析能力
- 分析结果不够精确，误报率较高
- 与现有的CodeQL生成能力没有整合

通过将这两个Agent集成到CodeQLComposeTool，可以：
- 利用CodeQL的强大语义分析能力提高查找精度
- 统一工具使用方式，简化架构
- 利用已有的`exec_mode='run'`模式获取文本格式结果
- 保持现有输出格式100%兼容

## 目标
- 修改Sink和Source Agent的提示词，要求使用CodeQLComposeTool进行查找
- Agent通过CodeQLComposeTool的`exec_mode='run'`模式执行查询
- 获取SARIF结果文件路径后，通过文件工具读取内容
- 基于SARIF内容生成最终的Sink/Source点报告
- 保持现有输出格式完全不变

## 非目标
- 不修改CodeQLComposeTool的核心功能
- 不改变Sink和Source Agent的接口签名
- 不修改最终输出格式
- 不影响现有的CVE分析流程

## 提议的方案（高层设计）
- 更新`prompts/sink_prompt_manager.py`和`prompts/source_prompt_manager.py`中的提示词模板
- 在提示词中明确要求使用`codeql_compose`工具，设置`exec_mode='run'`
- 指导Agent直接从工具返回结果中提取查询结果内容
- 利用`exec_mode='run'`返回的文本格式结果进行Sink/Source点分析
- 生成符合现有格式的Sink/Source点报告

## API / 接口变更
- `UnifiedSinkPathAgent.analyze_paths()` - 保持接口不变，内部使用CodeQLComposeTool
- `UnifiedSourceAnalysisAgent.analyze_sources()` - 保持接口不变，内部使用CodeQLComposeTool
- 提示词模板 - 更新为要求使用CodeQLComposeTool的版本

## 验收标准（Acceptance Criteria）
- Sink Agent使用CodeQLComposeTool查找危险函数点
- Source Agent使用CodeQLComposeTool查找数据源点
- 两个Agent都能直接从`exec_mode='run'`返回结果中提取查询信息
- 输出格式与现有版本完全一致
- 查找精度相比原有文件工具有显著提升

## 影响面
- 组件：提示词管理器、Sink/Source Agent
- 依赖：CodeQLComposeTool的`exec_mode='run'`功能
- 输出：无变化（保持兼容）

## 风险与缓解
- CodeQL查询生成失败：提供回退机制，在无法生成有效查询时使用原有文件工具
- 查询结果解析错误：增加异常处理，确保能正确处理文本格式结果
- 性能影响：CodeQL查询执行可能比直接文件分析慢，但精度提升值得这个代价

## 迁移/回滚计划
- 新的提示词模板向后兼容，可以通过配置开关控制是否使用CodeQLComposeTool
- 如出现问题，可以快速回退到原有的文件工具方式
- 保持原有接口不变，确保调用方无需修改

## 时间线（建议）
- D1：提示词模板更新和测试
- D2：Agent集成测试和验证
- D3：端到端测试和输出格式验证
