from __future__ import annotations

import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional

import uvicorn
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from api.config import get_config
from api.langchain_routes import create_langchain_router
from api.models import ErrorResponse, HealthResponse, VersionResponse
from api.projects_routes import router as projects_router
from api.analysis_routes import router as analysis_router

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时执行
    logger.info("Starting PureAutoCodeql API Server...")
    logger.info(f"Projects directory: {config.projects_dir}")
    logger.info(f"Server will listen on {config.host}:{config.port}")
    
    yield
    
    # 关闭时执行
    logger.info("Shutting down PureAutoCodeql API Server...")


# 获取配置
config = get_config()

# 创建FastAPI应用
app = FastAPI(
    title=config.api_title,
    description=config.api_description,
    version=config.api_version,
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)


if config.cors_enabled:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.cors_origins,
        allow_credentials=config.cors_allow_credentials,
        allow_methods=config.cors_allow_methods,
        allow_headers=config.cors_allow_headers,
    )
    logger.info(f"CORS enabled for origins: {config.cors_origins}")


@app.middleware("http")
async def log_requests(request: Request, call_next):
    start_time = time.time()
    
    if config.log_requests:
        logger.info(f"[*] {request.method} {request.url.path}")
    
    # 处理请求
    try:
        response = await call_next(request)
        process_time = time.time() - start_time
        response.headers["X-Process-Time"] = str(process_time)

        if config.log_requests:
            logger.info(
                f"[*] {request.method} {request.url.path} "
                f"- Status: {response.status_code} "
                f"- Time: {process_time:.3f}s"
            )
        
        return response
    except Exception as e:
        process_time = time.time() - start_time
        logger.error(
            f"[!] {request.method} {request.url.path} "
            f"- Error: {str(e)} "
            f"- Time: {process_time:.3f}s"
        )
        raise


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """全局异常处理器"""
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    
    error_response = ErrorResponse(
        error="InternalServerError",
        message="An internal server error occurred",
        detail=str(exc) if config.log_level == "DEBUG" else None,
        timestamp=datetime.now(),
    )
    
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=error_response.model_dump(mode="json"),
    )


@app.exception_handler(404)
async def not_found_handler(request: Request, exc):
    """404错误处理器"""
    error_response = ErrorResponse(
        error="NotFound",
        message=f"The requested resource was not found: {request.url.path}",
        timestamp=datetime.now(),
    )
    
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND,
        content=error_response.model_dump(mode="json"),
    )


@app.get("/health", response_model=HealthResponse, tags=["System"])
async def health_check() -> HealthResponse:
    return HealthResponse(
        status="healthy",
        version=config.api_version,
        timestamp=datetime.now(),
    )


@app.get(f"{config.api_prefix}/version", response_model=VersionResponse, tags=["System"])
async def get_version() -> VersionResponse:
    return VersionResponse(
        api_version=config.api_version,
        build_time=None,
        commit_hash=None,
    )


@app.get("/", tags=["System"])
async def root():
    return {
        "message": "Welcome to PureAutoCodeql API",
        "version": config.api_version,
        "docs": "/docs",
        "redoc": "/redoc",
        "health": "/health",
    }


# 集成项目管理路由
app.include_router(projects_router, prefix=config.api_prefix)
logger.info("[*] Registered projects routes")

# 集成分析任务路由
app.include_router(analysis_router)
logger.info("[*] Registered analysis routes")

# 集成LangChain工具路由
# 注意: 需要在实际使用时传入analyzer实例
langchain_router = create_langchain_router(
    analyzer=None,  # 在生产环境中应该传入实际的analyzer实例
    database_path=None,  # 使用默认路径
    language="java",
)
app.include_router(langchain_router)
logger.info("[*] Registered LangChain tool routes")


def start_server(
    host: Optional[str] = None,
    port: Optional[int] = None,
    reload: Optional[bool] = None,
    workers: Optional[int] = None,
):
    uvicorn.run(
        "api.server:app",
        host=host or config.host,
        port=port or config.port,
        reload=reload if reload is not None else config.reload,
        workers=workers or config.workers,
        log_level=config.log_level.lower(),
    )


if __name__ == "__main__":
    start_server()
