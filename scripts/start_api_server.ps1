# PureAutoCodeql API Server 启动脚本 (PowerShell 版本)
# 说明：与 scripts/start_api_server.sh 行为一致，支持相同参数与环境变量。

$ErrorActionPreference = "Stop"

# 脚本所在目录与项目根目录
$ScriptDir = Split-Path -Path $PSCommandPath -Parent
if (-not $ScriptDir) { $ScriptDir = Split-Path -Path $MyInvocation.MyCommand.Path -Parent }
$ProjectRoot = Split-Path -Path $ScriptDir -Parent

# 切换到项目根目录
Set-Location -Path $ProjectRoot

Write-Host "Starting PureAutoCodeql API Server..."
Write-Host "Project root: $ProjectRoot"

# 读取环境变量（提供默认值）
$ApiHost  = if ($env:API_HOST) { $env:API_HOST } else { "0.0.0.0" }
$ApiPort  = if ($env:API_PORT) { $env:API_PORT } else { "8000" }
$Reload   = if ($env:API_RELOAD) { $env:API_RELOAD } else { "false" }
$Workers  = if ($env:API_WORKERS) { $env:API_WORKERS } else { "1" }
$LogLevel = if ($env:API_LOG_LEVEL) { $env:API_LOG_LEVEL } else { "info" }

function Show-Help {
    Write-Host "Usage: .\\scripts\\start_api_server.ps1 [options]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  --host HOST          Server host (default: 0.0.0.0)"
    Write-Host "  --port PORT          Server port (default: 8000)"
    Write-Host "  --reload             Enable auto-reload"
    Write-Host "  --workers N          Number of workers (default: 1)"
    Write-Host "  --log-level LEVEL    Log level (default: info)"
    Write-Host "  --dev                Development mode (reload + debug)"
    Write-Host "  --help               Show this help message"
    Write-Host ""
    Write-Host "Environment variables:"
    Write-Host "  API_HOST, API_PORT, API_RELOAD, API_WORKERS, API_LOG_LEVEL"
}

# 解析命令行参数（支持 --host/--port/--reload/--workers/--log-level/--dev/--help）
$argList = @()
if ($null -ne $args) { $argList = @($args) }
$idx = 0
while ($idx -lt $argList.Count) {
    switch ($argList[$idx]) {
        "--host" {
            if ($idx + 1 -ge $argList.Count) { Write-Host "--host 需要一个参数"; exit 1 }
            $ApiHost = $argList[$idx + 1]
            $idx += 2
            continue
        }
        "--port" {
            if ($idx + 1 -ge $argList.Count) { Write-Host "--port 需要一个参数"; exit 1 }
            $ApiPort = $argList[$idx + 1]
            $idx += 2
            continue
        }
        "--reload" {
            $Reload = "true"
            $idx += 1
            continue
        }
        "--workers" {
            if ($idx + 1 -ge $argList.Count) { Write-Host "--workers 需要一个参数"; exit 1 }
            $Workers = $argList[$idx + 1]
            $idx += 2
            continue
        }
        "--log-level" {
            if ($idx + 1 -ge $argList.Count) { Write-Host "--log-level 需要一个参数"; exit 1 }
            $LogLevel = $argList[$idx + 1]
            $idx += 2
            continue
        }
        "--dev" {
            $Reload = "true"
            $LogLevel = "debug"
            Write-Host "Development mode enabled"
            $idx += 1
            continue
        }
        "--help" {
            Show-Help
            exit 0
        }
        Default {
            Write-Host "Unknown option: $($argList[$idx])"
            Write-Host "Use --help for usage information"
            exit 1
        }
    }
}

# 构建 uvicorn 参数
$UvicornArgs = @(
    "api.server:app",
    "--host", $ApiHost,
    "--port", $ApiPort,
    "--log-level", $LogLevel
)

if ($Reload -eq "true") {
    $UvicornArgs += "--reload"
} else {
    $UvicornArgs += @("--workers", $Workers)
}

Write-Host "Configuration:"
Write-Host "   Host: $ApiHost"
Write-Host "   Port: $ApiPort"
Write-Host "   Reload: $Reload"
Write-Host "   Workers: $Workers"
Write-Host "   Log Level: $LogLevel"
Write-Host ""
Write-Host "Server will be available at:"
Write-Host "   - API Docs: http://localhost:$ApiPort/docs"
Write-Host "   - ReDoc: http://localhost:$ApiPort/redoc"
Write-Host "   - Health Check: http://localhost:$ApiPort/health"

# 启动服务器（使用 uv run 确保在正确的虚拟环境中运行）
& uv run uvicorn @UvicornArgs
