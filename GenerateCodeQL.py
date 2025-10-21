import asyncio
from pathlib import Path
from typing import Optional
from dataclasses import dataclass

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI

from agents.codeql_generator_agent import CodeQLGeneratorAgent


@dataclass
class AgentConfig:
    """Configuration for creating agents with consistent settings."""
    model: str = "deepseek-reasoner"
    api_key: str = "sk-1cf370f7a87c4ca6bc6fb39e7fd712ac"
    base_url: str = "https://api.deepseek.com/v1"
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None
    max_retries: int = 3


@dataclass
class AgentResult:
    """Result from an agent execution."""
    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """Multi-agent analyzer for CodeQL generation."""
    
    def __init__(self, config: AgentConfig = None):
        self.config = config or AgentConfig()
        self.llm = None
        self.mcp_client = None
        self.tools = None
    
    async def initialize(self) -> None:
        """Initialize LLM and MCP client for reuse across agents."""
        self.llm = ChatOpenAI(
            model=self.config.model,
            api_key=self.config.api_key,
            base_url=self.config.base_url,
            temperature=self.config.temperature,
            streaming=self.config.streaming,
            max_tokens=self.config.max_tokens,
            max_retries=self.config.max_retries,
        )

        self.mcp_client = MultiServerMCPClient(
            {
                "filesystem": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-filesystem",
                        str(Path.cwd()),
                    ],
                    "transport": "stdio",
                },
                "sequential-thinking": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-sequential-thinking",
                    ],
                    "transport": "stdio",
                },
            }
        )
        
        self.tools = await self.mcp_client.get_tools()
    
    async def run_agent(self, prompt: str) -> AgentResult:
        """Run a single agent with the given prompt."""
        try:
            if not self.llm or not self.tools:
                await self.initialize()
            
            agent = create_agent(self.llm, self.tools)
            content_parts = []
            
            async for event in agent.astream_events({"messages": [("user", prompt)]}, version="v1"):
                if event.get("event") == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join([c.get("text", "") for c in chunk.content if isinstance(c, dict)])
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            content_parts.append(text)
            
            return AgentResult(content="".join(content_parts), success=True)
        
        except Exception as e:
            return AgentResult(content="", success=False, error=str(e))


async def generate_codeql_query(requirement: str) -> None:
    """Generate CodeQL query based on user requirement.
    
    Args:
        requirement: Natural language description of the CodeQL query requirement
    """
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        codeql_agent = CodeQLGeneratorAgent(analyzer)
        
        result = await codeql_agent.generate_codeql(requirement)
        if not result.success:
            print(f"CodeQL generation failed: {result.error}")
        else:
            print(result.content)
    except Exception as e:
        print(f"CodeQL generation error: {e}")


async def main() -> None:
    requirement = input("请输入 CodeQL 查询需求: ")
    await generate_codeql_query(requirement)


if __name__ == "__main__":
    asyncio.run(main())

