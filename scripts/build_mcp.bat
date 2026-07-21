@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
REM 构建脚本：安装和编译 MCP 相关工具
REM 可从任意 cwd 调用；始终以仓库根目录为基准。
echo ========================================
echo  PureAutoCodeQL - MCP 工具构建脚本
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT_DIR=%%~fI"
cd /d "%ROOT_DIR%"

REM 检查 Node.js 是否安装
where.exe node.exe >nul 2>&1
if errorlevel 1 (
    node --version >nul 2>&1
    if errorlevel 1 (
        echo [错误] 未找到 Node.js，请先安装 Node.js (v18 或更高版本)
        echo 下载地址: https://nodejs.org/
        exit /b 1
    )
)

echo [1/2] 检查 Node.js 版本...
node --version
echo.

REM 构建 mcp_ripgrep
echo [2/2] 构建 mcp_ripgrep...
if not exist "tools\mcp_ripgrep" (
    echo [错误] tools\mcp_ripgrep 目录不存在
    exit /b 1
)

cd tools\mcp_ripgrep

if not exist "node_modules" (
    echo   安装依赖...
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo [错误] npm install 失败
        exit /b 1
    )
) else (
    echo   依赖已存在，跳过安装
)

echo   编译 TypeScript...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo [错误] TypeScript 编译失败
    exit /b 1
)

cd /d "%ROOT_DIR%"

REM 检查编译结果
if exist "tools\mcp_ripgrep\dist\index.js" (
    echo.
    echo ========================================
    echo  ✓ 构建成功！
    echo ========================================
    echo  mcp_ripgrep 已编译到: tools\mcp_ripgrep\dist\index.js
    echo.
) else (
    echo.
    echo [错误] 编译后的文件不存在
    exit /b 1
)
