# CodeQL断点检测功能

## 简介

CodeQL断点检测功能是PureAutoCodeql项目的一个增强特性，当CodeQL查询结果为空时，系统能够自动检测数据流中的断流点，并生成相应的`isAdditionalFlowStep`条件来连接这些断流点。

## 功能特点

- 自动检测数据流中的断流点
- 分析断流点的原因和位置
- 生成相应的`isAdditionalFlowStep`条件
- 保存分析结果供后续使用
- 无缝集成到现有的CodeQL查询生成流程中

## 使用方法

### 1. 通过CodeQLComposeTool使用

```python
from tools.codeql_compose import CodeQLComposeTool
from services.llm_service import get_resilient_llm_config, LLMRole

# 创建LLM配置
config = get_resilient_llm_config(LLMRole.CHAT)

# 创建CodeQLComposeTool实例
compose_tool = CodeQLComposeTool(
    analyzer=config,
    default_database_path="/path/to/codeql/database",
    default_language="java"
)

# 使用工具生成CodeQL查询
result = await compose_tool._arun(
    requirement="查找所有从用户输入到SQL查询执行的数据流",
    language="java",
    database_path="/path/to/codeql/database"
)

# 如果查询结果为空，系统将自动触发断点检测流程
```

### 2. 直接使用CodeQLBreakpointAgent

```python
from agents.codeql_gen_agents.codeql_breakpoint_detect_agent import CodeQLBreakpointAgent
from services.llm_service import get_resilient_llm_config, LLMRole, MultiAgentAnalyzer

# 创建LLM配置和分析器
config = get_resilient_llm_config(LLMRole.CHAT)
analyzer = MultiAgentAnalyzer(config)

# 创建断点检测代理
agent = CodeQLBreakpointAgent(
    analyzer=analyzer,
    source_root="/path/to/source/code"
)

# 使用代理检测断点
result = await agent.detect_breakpoints(
    codeql_results="CodeQL查询结果...",
    language="java",
    show_thinking=True
)

if result.success:
    print(f"断点分析结果: {result.content}")
else:
    print(f"断点检测失败: {result.error}")
```

## 示例输出

当检测到断流点时，系统会生成类似以下的`isAdditionalFlowStep`条件：

```ql
override predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node sink) {
  exists(StringMethodCallExpr ma, BinaryExpr be |
    // 字符串转换操作
    ma.getMethod().hasName("toLowerCase") and
    ma.getQualifier() = src.asExpr() and
    ma.getEnclosingStmt() = sink.asExpr().getEnclosingStmt()
    
    or
    
    // 字符串拼接操作
    be.getOperator() = "+" and
    (be.getLeftOperand() = src.asExpr() or be.getRightOperand() = src.asExpr()) and
    be.getEnclosingStmt() = sink.asExpr().getEnclosingStmt()
  )
}
```

## 文件结构

- `agents/codeql_gen_agents/codeql_breakpoint_detect_agent.py` - 断点检测代理实现
- `tools/codeql_compose.py` - 集成了断点检测功能的CodeQL组合工具
- `tools/extract_ql.py` - 提供断流点查询生成功能
- `prompts/codeql_breakpoint_detect.md` - 断点检测代理的提示词模板

## 测试

项目包含以下测试脚本：

- `test_breakpoint_integration.py` - 测试断点检测代理的基本功能
- `test_compose_breakpoint_integration.py` - 测试CodeQLComposeTool中的断点检测集成
- `test_full_breakpoint_workflow.py` - 测试完整的断点检测工作流
- `demo_breakpoint_detection.py` - 演示断点检测功能的使用

运行测试：

```bash
python test_breakpoint_integration.py
python test_compose_breakpoint_integration.py
python test_full_breakpoint_workflow.py
python demo_breakpoint_detection.py
```

## 注意事项

1. 断点检测功能在查询结果为空时自动触发
2. 分析结果会保存到`breakpoint_analysis.md`文件中
3. 生成的`isAdditionalFlowStep`条件需要根据具体场景进行调整
4. Java、Python 与 C/C++ 均通过语言能力注册表选择模板、LSP、断流恢复和回退策略

## 贡献

欢迎提交问题报告和功能请求！如果您想贡献代码，请遵循以下步骤：

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用MIT许可证。详情请参阅LICENSE文件。
