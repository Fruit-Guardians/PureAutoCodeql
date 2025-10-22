import asyncio
import re
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
    model: str = "deepseek-chat"
    api_key: str = "sk-a2d1b4e295d6404694f45f45cb236c91"
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
    
    async def run_agent_stream(self, prompt: str, output_callback=None):
        """Run a single agent with the given prompt and stream output."""
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
                            if output_callback:
                                output_callback(text)
            
            return AgentResult(content="".join(content_parts), success=True)
        
        except Exception as e:
            return AgentResult(content="", success=False, error=str(e))


def extract_codeql_from_content(content: str) -> str:
    """Extract CodeQL code from content wrapped in <codeql></codeql> tags."""
    pattern = r'<codeql>(.*?)</codeql>'
    match = re.search(pattern, content, re.DOTALL)
    if match:
        return match.group(1).strip()
    return content.strip()

async def generate_codeql_query() -> None:
    """Generate CodeQL query based on user requirement with streaming output."""
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        codeql_agent = CodeQLGeneratorAgent(analyzer)
        
        # 直接在代码中定义需求
        requirement = "查找Java 的可能的Source点"
        
        print("正在生成 CodeQL 查询...")
        print("=" * 50)
        
        # 流式输出
        full_content = ""
        def stream_callback(text: str):
            nonlocal full_content
            full_content += text
            print(text, end='', flush=True)
        
        prompt = codeql_agent.build_prompt(requirement)
        result = await analyzer.run_agent_stream(prompt, stream_callback)
        
        print("\n" + "=" * 50)
        
        if not result.success:
            print(f"CodeQL 生成失败: {result.error}")
        else:
            # 提取 CodeQL 代码
            codeql_code = extract_codeql_from_content(full_content)
            
            print(codeql_code)
            
    except Exception as e:  
        print(e)

async def main() -> None:
    await generate_codeql_query()


if __name__ == "__main__":
    asyncio.run(main())

