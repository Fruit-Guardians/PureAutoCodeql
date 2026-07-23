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

from pure_auto_codeql.observability import configure_telemetry

from .analysis_routes import router as analysis_router
from .config import get_config
from .durable_task_manager import (
    close_durable_task_manager,
    get_durable_task_manager,
)
from .models import ErrorResponse, HealthResponse, VersionResponse
from .projects_routes import router as projects_router
from .security import SlidingWindowRateLimiter, TokenVerifier

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


_LOOPBACK_HOSTS = {"127.0.0.1", "localhost", "::1"}


def _warn_on_insecure_posture(cfg) -> None:
    """启动时对不安全配置发出醒目告警（不改变默认可用性，仅提示）。"""
    is_loopback = cfg.host in _LOOPBACK_HOSTS

    if not cfg.auth_token:
        if is_loopback:
            logger.warning(
                "API 未设置 auth_token：当前仅监听回环地址 %s，风险较低。"
                "如需对外暴露，请先设置 API_AUTH_TOKEN。",
                cfg.host,
            )
        else:
            logger.warning(
                "󰀪  安全告警：API 绑定到非回环地址 %s 且未设置 auth_token，"
                "所有接口（含项目导入、分析触发）将无鉴权暴露。"
                "强烈建议设置 API_AUTH_TOKEN 后再对外提供服务。",
                cfg.host,
            )

    if cfg.workers and cfg.workers > 1:
        logger.warning(
            "󰀪  workers=%d：任务状态保存在单进程内存中，多 worker 下同一 task_id "
            "在其它进程不可见（会返回 404）。当前实现请使用 workers=1，或改用共享存储。",
            cfg.workers,
        )


def _enforce_security_posture(cfg) -> None:
    if cfg.host not in _LOOPBACK_HOSTS and not cfg.auth_token:
        raise RuntimeError(
            "API refuses to bind a non-loopback address without API_AUTH_TOKEN"
        )


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时执行
    configure_telemetry("pure-auto-codeql-api")
    logger.info("Starting PureAutoCodeql API Server...")
    logger.info(f"Projects directory: {config.projects_dir}")
    logger.info(f"Server will listen on {config.host}:{config.port}")
    _warn_on_insecure_posture(config)
    _enforce_security_posture(config)
    durable_manager = get_durable_task_manager()
    if durable_manager:
        await durable_manager.initialize()

    yield

    # 关闭时执行
    logger.info("Shutting down PureAutoCodeql API Server...")
    await close_durable_task_manager()


# 获取配置
config = get_config()
rate_limiter = SlidingWindowRateLimiter(config.rate_limit_per_minute)

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

    public_paths = {"/", "/health", "/docs", "/redoc", "/openapi.json"}
    if config.auth_token and request.url.path not in public_paths:
        if not TokenVerifier(config.auth_token).verify(request.headers.get("Authorization", "")):
            logger.warning(
                "audit auth_denied method=%s path=%s client=%s",
                request.method,
                request.url.path,
                request.client.host if request.client else "unknown",
            )
            return JSONResponse(
                status_code=status.HTTP_401_UNAUTHORIZED,
                content=ErrorResponse(
                    error="Unauthorized",
                    message="Valid Bearer token required",
                    timestamp=datetime.now(),
                ).model_dump(mode="json"),
            )
    identity = request.client.host if request.client else "unknown"
    if not rate_limiter.allow(identity):
        logger.warning("audit rate_limited path=%s client=%s", request.url.path, identity)
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content=ErrorResponse(
                error="RateLimited",
                message="Request rate limit exceeded",
                timestamp=datetime.now(),
            ).model_dump(mode="json"),
        )

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
    durable_manager = get_durable_task_manager()
    dependencies = await durable_manager.health() if durable_manager else {"mode": True}
    return HealthResponse(
        status="healthy" if all(dependencies.values()) else "unhealthy",
        version=config.api_version,
        timestamp=datetime.now(),
        dependencies=dependencies,
    )


@app.get(
    f"{config.legacy_api_prefix}/version",
    response_model=VersionResponse,
    include_in_schema=False,
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
app.include_router(analysis_router, prefix=config.api_prefix)
logger.info("[*] Registered analysis routes")

# Transitional compatibility surface. New clients should use /api/v1.
if config.legacy_api_prefix and config.legacy_api_prefix != config.api_prefix:
    app.include_router(projects_router, prefix=config.legacy_api_prefix, include_in_schema=False)
    app.include_router(analysis_router, prefix=config.legacy_api_prefix, include_in_schema=False)


def start_server(
    host: Optional[str] = None,
    port: Optional[int] = None,
    reload: Optional[bool] = None,
    workers: Optional[int] = None,
):
    uvicorn.run(
        "pure_auto_codeql.api.server:app",
        host=host or config.host,
        port=port or config.port,
        reload=reload if reload is not None else config.reload,
        workers=workers or config.workers,
        log_level=config.log_level.lower(),
    )


if __name__ == "__main__":
    start_server()
