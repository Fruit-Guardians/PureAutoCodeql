# LangChain Tool: CodeQLComposeTool

该目录提供统一的 CodeQL 生成与校验工具 `CodeQLComposeTool`。

## 工具

### CodeQLComposeTool

- 从自然语言 `requirement` 生成 CodeQL 查询
- 迭代式“生成→执行→纠错”，在成功后返回包含查询与执行信息的文本
- 支持从输出中的 ```ql 代码块或 `<codeql></codeql>` 标签提取最终查询
- 需要提供 CodeQL 数据库路径以完成验证执行

**入参（初始化）**
- `analyzer`: 复用的多 Agent 分析器实例
- `database_path`: CodeQL 数据库路径
- `language`: 目标语言（`java`/`python`/`cpp`），默认 `java`
- `max_rounds`: 最大迭代轮数，默认 5

**入参（运行）**
- `requirement` (str): 自然语言描述的 CodeQL 查询需求

## 使用示例

```python
from tools import CodeQLComposeTool
from Analyze import MultiAgentAnalyzer

analyzer = MultiAgentAnalyzer()
await analyzer.initialize()

tool = CodeQLComposeTool(
    analyzer=analyzer,
    database_path="./h5-vsan",
    language="java",
)

output_text = await tool._arun("查找可能的Source点")

# 提取最终QL（优先```ql，其次<codeql>）
import re
match = re.search(r"```ql\s*\n(.*?)\n```", output_text, re.DOTALL)
ql = match.group(1).strip() if match else output_text
```

## 与 Agent 集成

- 可作为 LangChain 工具在自定义 Agent 中调用
- 也可在工作流中直接实例化并调用 `_arun(requirement)`

## 结果说明

- 成功时返回包含如下内容的文本：
  - 生成成功提示与轮次
  - 最终查询（```ql 代码块）
  - 可选的 SARIF 输出路径
- 失败时返回包含错误详情的文本，包含最后一次尝试的查询和错误原因

## 依赖项

- `langchain >= 1.0.1`
- `pydantic`
- 本项目中的 `agents/codeql_gen_agents/*` 与 `utils/codeql.py`

