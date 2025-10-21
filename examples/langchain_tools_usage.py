"""
LangChain 工具在 CodeQL 操作中的使用示例。

本示例演示如何与 LangChain 代理一起使用 CodeQLGeneratorTool 和 CodeQLRunnerTool。
"""

import asyncio
import sys
from pathlib import Path

# Add parent directory to path to import tools module
sys.path.insert(0, str(Path(__file__).parent.parent))


async def example_standalone_usage():
    """Example of using the tools directly without an agent."""
    print("=" * 60)
    print("Example 1: Standalone Tool Usage")
    print("=" * 60)
    
    # Note: CodeQLGeneratorTool requires a MultiAgentAnalyzer instance
    # For this example, we'll demonstrate the CodeQLRunnerTool which can work standalone
    
    from tools import CodeQLRunnerTool
    
    # Create the runner tool
    runner_tool = CodeQLRunnerTool()
    
    # Example CodeQL query
    sample_query = """
    import java
    
    from Method m
    where m.getName().matches("get%")
    select m, m.getDeclaringType()
    """
    
    # Specify your database path
    database_path = "./h5-vsan"
    
    print("\nExecuting CodeQL query...")
    print(f"Database: {database_path}")
    print(f"Query:\n{sample_query}")
    print("-" * 60)
    
    # Execute the query (synchronous)
    result = runner_tool._run(
        query_content=sample_query,
        database_path=database_path
    )
    
    print("\nResult:")
    print(result)


async def example_with_agent():
    """Example of using the tools with a LangChain agent."""
    print("\n" + "=" * 60)
    print("Example 2: Using Tools with LangChain Agent")
    print("=" * 60)
    
    # This would require setting up a full agent with OpenAI or other LLM
    # Here's the conceptual code:
    
    print("""
To use these tools with a LangChain agent:

1. Initialize your LLM and agent:
   ```python
   from langchain.agents import initialize_agent, AgentType
   from langchain_openai import ChatOpenAI
   from tools import CodeQLGeneratorTool, CodeQLRunnerTool
   
   # Create MultiAgentAnalyzer instance (from your existing code)
   from Analyze import MultiAgentAnalyzer
   analyzer = MultiAgentAnalyzer(...)
   
   # Initialize tools
   generator_tool = CodeQLGeneratorTool(analyzer=analyzer)
   runner_tool = CodeQLRunnerTool()
   
   tools = [generator_tool, runner_tool]
   
   # Create agent
   llm = ChatOpenAI(temperature=0)
   agent = initialize_agent(
       tools=tools,
       llm=llm,
       agent=AgentType.ZERO_SHOT_REACT_DESCRIPTION,
       verbose=True
   )
   ```

2. Use the agent:
   ```python
   response = await agent.arun(
       "Generate a CodeQL query to find all methods that take user input, "
       "then execute it against the database at ./h5-vsan"
   )
   print(response)
   ```

The agent will automatically:
- Use CodeQLGeneratorTool to generate the query
- Use CodeQLRunnerTool to execute it
- Return the results to you
""")


async def example_generator_with_analyzer():
    """Example showing how to use CodeQLGeneratorTool with a real analyzer."""
    print("\n" + "=" * 60)
    print("Example 3: CodeQL Generator Tool with Analyzer")
    print("=" * 60)
    
    print("""
To use the CodeQLGeneratorTool:

```python
from tools import CodeQLGeneratorTool
from Analyze import MultiAgentAnalyzer

# Initialize your analyzer (requires OpenAI API key configuration)
analyzer = MultiAgentAnalyzer(
    model="gpt-4",
    api_key="your-api-key"  # Or configure via environment
)

# Create the generator tool
generator_tool = CodeQLGeneratorTool(analyzer=analyzer)

# Generate CodeQL code
requirement = "Find all methods that process user input"
codeql_code = await generator_tool._arun(requirement)

print(f"Generated CodeQL:\\n{codeql_code}")
```
""")


async def main():
    """Run all examples."""
    print("\n")
    print("╔" + "═" * 58 + "╗")
    print("║" + " " * 10 + "LangChain CodeQL Tools Examples" + " " * 16 + "║")
    print("╚" + "═" * 58 + "╝")
    
    # Run standalone example (this actually executes)
    # Note: Will only work if CodeQL database exists at ./h5-vsan
    if Path("./h5-vsan").exists():
        await example_standalone_usage()
    else:
        print("\nSkipping standalone example - database not found at ./h5-vsan")
        print("Create a CodeQL database first or modify the path in the example.")
    
    # Show conceptual examples
    await example_with_agent()
    await example_generator_with_analyzer()
    
    print("\n" + "=" * 60)
    print("Examples complete!")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    asyncio.run(main())

