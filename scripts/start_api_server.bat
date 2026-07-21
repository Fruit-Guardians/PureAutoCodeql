@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem PureAutoCodeql API Server (BAT version)
rem Mirrors scripts/start_api_server.sh behavior

rem Change to project root (one level above this script)
pushd "%~dp0\.."

echo Starting PureAutoCodeql API Server...
echo Project root: %CD%

rem Read env defaults
set "HOST=%API_HOST%"
if not defined HOST set "HOST=127.0.0.1"
set "PORT=%API_PORT%"
if not defined PORT set "PORT=8000"
set "RELOAD=%API_RELOAD%"
if not defined RELOAD set "RELOAD=false"
set "WORKERS=%API_WORKERS%"
if not defined WORKERS set "WORKERS=1"
set "LOG_LEVEL=%API_LOG_LEVEL%"
if not defined LOG_LEVEL set "LOG_LEVEL=info"

:parse
if "%~1"=="" goto after_parse
if "%~1"=="--host" (
  if "%~2"=="" (
    echo --host requires a value
    call :show_help
    popd
    exit /b 1
  )
  set "HOST=%~2"
  shift
  shift
  goto parse
)
if "%~1"=="--port" (
  if "%~2"=="" (
    echo --port requires a value
    call :show_help
    popd
    exit /b 1
  )
  set "PORT=%~2"
  shift
  shift
  goto parse
)
if "%~1"=="--reload" (
  set "RELOAD=true"
  shift
  goto parse
)
if "%~1"=="--workers" (
  if "%~2"=="" (
    echo --workers requires a value
    call :show_help
    popd
    exit /b 1
  )
  set "WORKERS=%~2"
  shift
  shift
  goto parse
)
if "%~1"=="--log-level" (
  if "%~2"=="" (
    echo --log-level requires a value
    call :show_help
    popd
    exit /b 1
  )
  set "LOG_LEVEL=%~2"
  shift
  shift
  goto parse
)
if "%~1"=="--dev" (
  set "RELOAD=true"
  set "LOG_LEVEL=debug"
  echo Development mode enabled
  shift
  goto parse
)
if "%~1"=="--help" (
  call :show_help
  popd
  exit /b 0
)

echo Unknown option: %~1
call :show_help
popd
exit /b 1

:after_parse
set "UVICORN_ARGS=pure_auto_codeql.api.server:app --host %HOST% --port %PORT% --log-level %LOG_LEVEL%"
if /I "%RELOAD%"=="true" (
  set "UVICORN_ARGS=%UVICORN_ARGS% --reload"
) else (
  set "UVICORN_ARGS=%UVICORN_ARGS% --workers %WORKERS%"
)

echo Configuration:
echo    Host: %HOST%
echo    Port: %PORT%
echo    Reload: %RELOAD%
echo    Workers: %WORKERS%
echo    Log Level: %LOG_LEVEL%
echo.
echo Server will be available at:
echo    - API Docs: http://localhost:%PORT%/docs
echo    - ReDoc: http://localhost:%PORT%/redoc
echo    - Health Check: http://localhost:%PORT%/health

rem Start server via uv run to use the proper virtual env
uv run uvicorn %UVICORN_ARGS%

popd
endlocal
exit /b %ERRORLEVEL%

:show_help
echo Usage: scripts\start_api_server.bat [options]
echo.
echo Options:
echo   --host HOST          Server host ^(default: 127.0.0.1^)
echo   --port PORT          Server port ^(default: 8000^)
echo   --reload             Enable auto-reload
echo   --workers N          Number of workers ^(default: 1^)
echo   --log-level LEVEL    Log level ^(default: info^)
echo   --dev                Development mode ^(reload + debug^)
echo   --help               Show this help message
echo.
echo Environment variables:
echo   API_HOST, API_PORT, API_RELOAD, API_WORKERS, API_LOG_LEVEL
exit /b 0
