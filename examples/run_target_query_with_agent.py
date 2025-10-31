"""
Run templates/java/target_query.ql via a LangChain Agent using CodeQLRunnerTool.
Falls back to direct tool execution if no LLM is configured.
"""

import asyncio
import os
import sys
from pathlib import Path

# Allow importing project-local modules (tools/)
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from tools import CodeQLRunnerTool  # type: ignore

# 导入集中化配置
from config import get_chat_config

QUERY_PATH = PROJECT_ROOT / "templates/java/target_query.ql"
DATABASE_PATH = PROJECT_ROOT / "h5-vsan"


async def run_with_agent(query_content: str, database_path: str, config) -> str:
    """Run the CodeQL query via a LangChain Agent if LLM is configured."""
    # Lazy import to avoid requiring langchain deps unless needed
    from langchain.agents import initialize_agent, AgentType

    # Try OpenAI first; user can also configure other LangChain-compatible LLMs if desired
    llm = None
    try:
        from langchain_openai import ChatOpenAI  # type: ignore
        # Configure ChatOpenAI with the same settings as Analyze.py
        llm = ChatOpenAI(
            model=config.model,
            api_key=config.api_key,
            base_url=config.base_url,
            temperature=0,
        )
    except Exception as e:
        # If LLM client not available or not configured, raise to caller
        raise RuntimeError(
            "LangChain agent requires langchain_openai and a compatible API."
        ) from e

    runner_tool = CodeQLRunnerTool()

    agent = initialize_agent(
        tools=[runner_tool],
        llm=llm,
        agent=AgentType.ZERO_SHOT_REACT_DESCRIPTION,
        verbose=True,
        handle_parsing_errors=True,
    )

    # Instruct the agent to use the tool with explicit arguments
    prompt = (
        "Use the codeql_runner tool to execute the provided CodeQL query. "
        "Pass the following exact JSON arguments to the tool:\n"  # Help the agent be precise
        f"query_content: <BEGIN_QUERY>\n{query_content}\n<END_QUERY>\n"
        f"database_path: {database_path}\n"
        f"language: java\n"
    )

    # Use async interface for consistency
    result = await agent.arun(prompt)
    return result


def run_without_agent(query_content: str, database_path: str) -> str:
    """Directly run the CodeQL query using CodeQLRunnerTool (no LLM required)."""
    runner_tool = CodeQLRunnerTool()
    return runner_tool._run(
        query_content=query_content,
        database_path=database_path,
        language="java",
    )


async def main() -> None:
    if not QUERY_PATH.exists():
        raise FileNotFoundError(f"Query file not found: {QUERY_PATH}")

    query_content = QUERY_PATH.read_text(encoding="utf-8")

    if not DATABASE_PATH.exists():
        print(f"[WARN] CodeQL database not found at: {DATABASE_PATH}")
        print("       Create or point to a valid CodeQL database before running.")
        # Continue anyway; CodeQL tool will return a meaningful error

    database_path = str(DATABASE_PATH)

    print("=" * 60)
    print("Running templates/java/target_query.ql")
    print(f"Database: {database_path}")
    print("Mode: Agent (Analyze.py config) -> fallback to Direct Tool if needed")
    print("=" * 60)

    config = get_chat_config()
    try:
        output = await run_with_agent(query_content, database_path, config)
    except Exception as e:
        print(f"[WARN] Agent mode failed ({e}). Falling back to direct tool execution...")
        output = run_without_agent(query_content, database_path)

    print("\nResult:\n")
    print(output)


if __name__ == "__main__":
    asyncio.run(main())
