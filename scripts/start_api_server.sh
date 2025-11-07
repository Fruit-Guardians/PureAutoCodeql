#!/usr/bin/env bash
# PureAutoCodeql API Server 启动脚本

set -e

# 脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 切换到项目根目录
cd "$PROJECT_ROOT"

echo "Starting PureAutoCodeql API Server..."
echo "Project root: $PROJECT_ROOT"


# 解析命令行参数
HOST="${API_HOST:-0.0.0.0}"
PORT="${API_PORT:-8000}"
RELOAD="${API_RELOAD:-false}"
WORKERS="${API_WORKERS:-1}"
LOG_LEVEL="${API_LOG_LEVEL:-info}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            HOST="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --reload)
            RELOAD="true"
            shift
            ;;
        --workers)
            WORKERS="$2"
            shift 2
            ;;
        --log-level)
            LOG_LEVEL="$2"
            shift 2
            ;;
        --dev)
            RELOAD="true"
            LOG_LEVEL="debug"
            echo "Development mode enabled"
            shift
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --host HOST          Server host (default: 0.0.0.0)"
            echo "  --port PORT          Server port (default: 8000)"
            echo "  --reload             Enable auto-reload"
            echo "  --workers N          Number of workers (default: 1)"
            echo "  --log-level LEVEL    Log level (default: info)"
            echo "  --dev                Development mode (reload + debug)"
            echo "  --help               Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  API_HOST, API_PORT, API_RELOAD, API_WORKERS, API_LOG_LEVEL"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# 构建uvicorn命令参数
UVICORN_ARGS="api.server:app --host $HOST --port $PORT --log-level $LOG_LEVEL"

if [ "$RELOAD" = "true" ]; then
    UVICORN_ARGS="$UVICORN_ARGS --reload"
else
    UVICORN_ARGS="$UVICORN_ARGS --workers $WORKERS"
fi

echo "Configuration:"
echo "   Host: $HOST"
echo "   Port: $PORT"
echo "   Reload: $RELOAD"
echo "   Workers: $WORKERS"
echo "   Log Level: $LOG_LEVEL"
echo ""
echo "Server will be available at:"
echo "   - API Docs: http://localhost:$PORT/docs"
echo "   - ReDoc: http://localhost:$PORT/redoc"
echo "   - Health Check: http://localhost:$PORT/health"

# 启动服务器 (使用uv run确保在正确的虚拟环境中运行)
exec uv run uvicorn $UVICORN_ARGS
