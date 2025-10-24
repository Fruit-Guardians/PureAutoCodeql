import asyncio
import subprocess
from pathlib import Path
from typing import Optional, Dict, Any
from dataclasses import dataclass

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI

# Import agents
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.python_sink_path_agent import PythonPathAnalysisAgent
from agents.python_source_analysis_agent import PythonSourceAnalysisAgent


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
    """Multi-agent analyzer for vulnerability analysis workflows (Python-focused)."""

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

            async for event in agent.astream_events({"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 80}):
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


def write_python_analysis_output(
    cve_result: AgentResult,
    sink_result: AgentResult,
    source_result: AgentResult,
    output_path: Path = Path("output_python.md"),
) -> None:
    """Write combined analysis results to a Markdown file (Python headings)."""
    try:
        sections = [
            "# Multi-Agent Analysis Output (Python)\n",
            "\n## CVE Analysis\n",
            (cve_result.content if cve_result and cve_result.success else f"(failed) {cve_result.error if cve_result else 'no result'}"),
            "\n\n## Python Sink Analysis\n",
            (sink_result.content if sink_result and sink_result.success else f"(failed) {sink_result.error if sink_result else 'no result'}"),
            "\n\n## Python Source Analysis\n",
            (source_result.content if source_result and source_result.success else f"(failed) {source_result.error if source_result else 'no result'}"),
            "\n",
        ]
        output = "".join(sections)
        output_path.write_text(output, encoding="utf-8")
        print(f"Output written to {output_path}")
    except Exception as e:
        print(f"Failed to write analysis output: {e}")


def ensure_python_codeql_db(source_root: str, db_path: str) -> bool:
    """Ensure a Python CodeQL database exists; create it if missing.

    Returns True if database exists or created successfully, False otherwise.
    """
    try:
        db_dir = Path(db_path)
        if db_dir.exists():
            return True
        print(f"CodeQL DB not found at {db_path}, creating...")
        result = subprocess.run(
            [
                "codeql",
                "database",
                "create",
                db_path,
                "--language=python",
                "--source-root",
                source_root,
            ],
            capture_output=True,
            text=True,
            timeout=600,
        )
        if result.returncode != 0:
            print("Failed to create CodeQL database:")
            print(result.stderr or result.stdout)
            return False
        print("CodeQL database created successfully.")
        return True
    except Exception as e:
        print(f"Error ensuring CodeQL DB: {e}")
        return False


async def run_python_multi_agent_analysis(
    json_path: str,
    diff_path: str,
    source_root: str,
    database_path: str,
) -> None:
    """Run Python CVE, Sink, and Source analysis on the given project."""
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        # Prepare agents
        cve_agent = CVEAnalysisAgent(analyzer)
        sink_agent = PythonPathAnalysisAgent(analyzer, source_root=source_root)
        source_agent = PythonSourceAnalysisAgent(
            analyzer,
            source_root=source_root,
            database_path=database_path,
        )

        # Ensure CodeQL database
        if not ensure_python_codeql_db(source_root, database_path):
            print("Warning: Python CodeQL database unavailable. Source analysis may fail.")

        print("=== CVE Analysis (Python project) ===")
        cve_result = await cve_agent.analyze_cve(Path(json_path))
        if not cve_result.success:
            print(f"CVE analysis failed: {cve_result.error}")
        else:
            print(cve_result.content)
        print()

        print("=== Python Sink Path Analysis ===")
        sink_result = await sink_agent.analyze_python_paths(cve_result.content if cve_result.success else "", diff_path)
        if not sink_result.success:
            print(f"Python sink analysis failed: {sink_result.error}")
        else:
            print(sink_result.content)
        print()

        print("=== Python Source Analysis (via CodeQL) ===")
        source_result = await source_agent.analyze_python_sources(cve_result.content if cve_result.success else "")
        if not source_result.success:
            print(f"Python source analysis failed: {source_result.error}")
        else:
            print(source_result.content)

        write_python_analysis_output(cve_result, sink_result, source_result, Path("output_python.md"))

    except Exception as e:
        print(f"Multi-agent Python analysis error: {e}")


async def main() -> None:
    # Paths provided by user
    source_root = "Shakal-NG-1.3.2"
    json_path = "CVE-2024-8412.json"  # CVE JSON 文件路径
    diff_path = "CVE-2024-8412.diff"  # Diff 文件路径
    # 使用已存在的 Python CodeQL 数据库路径（根据你的 workspace）
    database_path = "python-db\\db-python"  # CodeQL 数据库路径（Python）

    await run_python_multi_agent_analysis(json_path, diff_path, source_root, database_path)


if __name__ == "__main__":
    asyncio.run(main())