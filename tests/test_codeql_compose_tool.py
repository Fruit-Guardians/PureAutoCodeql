"""Test script for CodeQL Compose Tool."""

import asyncio
import os
import sys
from pathlib import Path

from langchain_openai import ChatOpenAI

# 添加项目根目录到Python路径
sys.path.append(str(Path(__file__).parent.parent))

# 导入集中化配置
from config import get_chat_config

from tools.codeql_compose import CodeQLComposeTool


class AgentResult:
    """代理结果类"""
    def __init__(self, content: str, success: bool, error: str = None):
        self.content = content
        self.success = success
        self.error = error

class SimpleAnalyzer:

    def __init__(self):
        # 使用集中化配置
        self.config = get_chat_config()
        
        # 初始化LLM
        self.llm = ChatOpenAI(
            model=self.config.model,
            api_key=self.config.api_key,
            base_url=self.config.base_url,
            temperature=self.config.temperature,
            streaming=self.config.streaming,
            max_tokens=self.config.max_tokens,
            max_retries=self.config.max_retries
        )
        
        self.mcp_client = None
        self.tools = []
    
    async def run_agent(self, prompt: str) -> AgentResult:
        """运行单个代理"""
        try:
            # 直接调用LLM
            response = await self.llm.ainvoke([{"role": "user", "content": prompt}])
            return AgentResult(content=response.content, success=True)
        except Exception as e:
            return AgentResult(content="", success=False, error=str(e))


async def test_codeql_compose_tool():
    analyzer = SimpleAnalyzer()
    database_path = r"C:\\Projects\\PureAutoCodeql2\\h5-vsan"
    codeql_tool = CodeQLComposeTool(
        analyzer=analyzer,
        database_path=database_path,
        language="java",
        max_rounds=5
    )

    requirement = "查询输出所有类名"
    print("Running \n")
    
    try:
        # 调用工具生成CodeQL查询
        result = await codeql_tool._arun(requirement)
        print("Result:")
        print(result)
        
    except Exception as e:
        print(f"Error during CodeQL composition: {str(e)}")


async def main():
    """主函数"""
    await test_codeql_compose_tool()


if __name__ == "__main__":
    asyncio.run(main())