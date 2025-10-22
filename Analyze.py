import asyncio
from pathlib import Path
from typing import Optional, Dict, Any, List
from dataclasses import dataclass

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI

# Import agents and utilities
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.java_sink_path_agent import JavaPathAnalysisAgent
from agents.java_source_analysis_agent import JavaSourceAnalysisAgent
from utils.io import write_analysis_output


@dataclass
class AgentConfig:
    """Configuration for creating agents with consistent settings."""
    # model: str = "gpt-5-chat-latest"
    # api_key: str = "sk-34hrOV0eZWNgcNTGPXXpLJ086uRoXmA7aCPTVICu2gAZQ7tu"
    # base_url: str = "https://yunwu.ai/v1"
    model: str = "deepseek-reasoner"
    api_key: str = "sk-a2d1b4e295d6404694f45f45cb236c91"
    base_url: str = "https://api.deepseek.com/v1"
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None  # 最大输出token数量
    max_retries: int = 3  # 最大重试次数



@dataclass
class AgentResult:
    """Result from an agent execution."""
    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """Multi-agent analyzer for vulnerability analysis workflows."""
    
    def __init__(self, config: AgentConfig = None):
        """Initialize the multi-agent analyzer."""
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


async def run_multi_agent_analysis(json_path: str = "CVE-2021-21985.json", 
                                 diff_path: str = "CVE-2021-21985.diff",
                                 source_root: str = "h5-vsan-service.jar_Decompiler.com") -> None:
    """Run the complete multi-agent analysis workflow with CVE, Sink, and Source agents.
    
    Args:
        json_path: Path to the CVE JSON file
        diff_path: Path to the diff file
        source_root: Root directory for Java source files
    """
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        cve_agent = CVEAnalysisAgent(analyzer)
        sink_agent = JavaPathAnalysisAgent(analyzer, source_root)
        source_agent = JavaSourceAnalysisAgent(analyzer, source_root)
        
        print("=== CVE Analysis ===")
        cve_result = await cve_agent.analyze_cve(Path(json_path))
        if not cve_result.success:
            print(f"CVE analysis failed: {cve_result.error}")
        else:
            print(cve_result.content)
        print()
        
        print("=== Java Sink Path Analysis ===")
        sink_result = await sink_agent.analyze_java_paths(cve_result.content if cve_result.success else "", diff_path)
        if not sink_result.success:
            print(f"Java sink analysis failed: {sink_result.error}")
        else:
            print(sink_result.content)
        print()
        
        print("=== Java Source Analysis ===")
        source_result = await source_agent.analyze_java_sources(cve_result.content if cve_result.success else "")
        if not source_result.success:
            print(f"Java source analysis failed: {source_result.error}")
        else:
            print(source_result.content)
        
        write_analysis_output(cve_result, sink_result, source_result, Path("output.md"))
    except Exception as e:
        print(f"Multi-agent analysis error: {e}")


async def main() -> None:
    source_root = "h5-vsan-service.jar_Decompiler.com"  # Java源码根目录
    json_path = "CVE-2021-21985.json"  # CVE JSON文件路径
    diff_path = "CVE-2021-21985.diff"  # Diff文件路径
    
    # 执行分析
    await run_multi_agent_analysis(json_path, diff_path, source_root)


if __name__ == "__main__":
    asyncio.run(main())
