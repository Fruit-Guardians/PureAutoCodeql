"""LangChain工具API路由 - 使用LangServe暴露工具"""

from __future__ import annotations

from typing import Optional

from fastapi import APIRouter
from langserve import add_routes

from tools.codeql_compose import CodeQLComposeTool
from api.config import get_config


def create_langchain_router(
    analyzer: Optional[any] = None,
    database_path: Optional[str] = None,
    language: str = "java",
) -> APIRouter:
    """
    创建LangChain工具路由
    
    Args:
        analyzer: LangChain分析器实例
        database_path: CodeQL数据库路径
        language: 目标语言
        
    Returns:
        配置好的FastAPI路由器
    """
    router = APIRouter(prefix="/langchain", tags=["LangChain Tools"])
    
    config = get_config()
    
    # 初始化CodeQLComposeTool
    if database_path is None:
        # 使用默认数据库路径（如果未提供）
        database_path = str(config.projects_dir / "python_kb" / "db")
    
    codeql_tool = CodeQLComposeTool(
        analyzer=analyzer,
        database_path=database_path,
        language=language,
        max_rounds=5,
    )
    
    # 使用LangServe添加工具路由
    # 这会自动创建以下端点:
    # POST /langchain/codeql-compose/invoke - 同步调用
    # POST /langchain/codeql-compose/batch - 批量调用
    # POST /langchain/codeql-compose/stream - 流式调用
    # POST /langchain/codeql-compose/stream_log - 流式日志
    # GET /langchain/codeql-compose/playground - 交互式playground
    add_routes(
        router,
        codeql_tool,
        path="/codeql-compose",
        enabled_endpoints=["invoke", "batch", "stream", "stream_log", "playground"],
    )
    
    return router
