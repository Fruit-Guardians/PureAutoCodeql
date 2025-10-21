# LangChain Tools for CodeQL Operations

这个目录包含用于 CodeQL 操作的 LangChain 工具集成。

## 工具列表

### 1. CodeQLGeneratorTool

根据自然语言需求生成 CodeQL 查询代码。

**特性：**
- 接受自然语言描述作为输入
- 使用 CodeQLGeneratorAgent 生成完整的 CodeQL 查询
- 自动提取和格式化生成的代码
- 支持异步调用

**使用示例：**
```python
from tools import CodeQLGeneratorTool
from Analyze import MultiAgentAnalyzer

# 初始化分析器
analyzer = MultiAgentAnalyzer(model="gpt-4")

# 创建工具
generator = CodeQLGeneratorTool(analyzer=analyzer)

# 生成 CodeQL 查询
codeql_code = await generator._arun(
    requirement="Find all methods that process user input"
)
print(codeql_code)
```

### 2. CodeQLRunnerTool

执行 CodeQL 查询并返回结果。

**特性：**
- 执行完整的 CodeQL 查询代码
- 支持指定数据库路径
- 格式化输出结果为可读文本
- 完善的错误处理
- 支持同步和异步调用

**使用示例：**
```python
from tools import CodeQLRunnerTool

# 创建工具
runner = CodeQLRunnerTool()

# 执行查询
result = runner._run(
    query_content="""
        import java
        from Method m
        select m
    """,
    database_path="./h5-vsan"
)
print(result)
```

## 与 LangChain Agent 集成

这些工具可以直接与 LangChain 的 agent 框架集成：

```python
from langchain.agents import initialize_agent, AgentType
from langchain_openai import ChatOpenAI
from tools import CodeQLGeneratorTool, CodeQLRunnerTool

# 初始化工具
generator_tool = CodeQLGeneratorTool(analyzer=your_analyzer)
runner_tool = CodeQLRunnerTool()

tools = [generator_tool, runner_tool]

# 创建 agent
llm = ChatOpenAI(temperature=0)
agent = initialize_agent(
    tools=tools,
    llm=llm,
    agent=AgentType.ZERO_SHOT_REACT_DESCRIPTION,
    verbose=True
)

# 使用 agent
response = await agent.arun(
    "Generate a CodeQL query to find SQL injection vulnerabilities "
    "and execute it against the database at ./h5-vsan"
)
```

## 输入参数

### CodeQLGeneratorTool

- `requirement` (str): 自然语言描述的 CodeQL 查询需求

### CodeQLRunnerTool

- `query_content` (str): 完整的 CodeQL 查询代码
- `database_path` (str): CodeQL 数据库目录路径

## 错误处理

两个工具都实现了完善的错误处理：

- **CodeQLGeneratorTool**: 捕获生成过程中的错误，返回详细的错误信息
- **CodeQLRunnerTool**: 处理查询执行失败、超时、语法错误等情况

所有错误都会以字符串形式返回，包含错误描述和上下文信息。

## 依赖项

- `langchain >= 1.0.1`
- `pydantic`
- 项目中的 `agents.codeql_generator_agent`
- 项目中的 `utils.codeql`

## 更多示例

查看 `examples/langchain_tools_usage.py` 获取更多使用示例和最佳实践。

