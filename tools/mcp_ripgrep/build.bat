@echo off
echo Building mcp-ripgrep...
cd /d "%~dp0"
if not exist "node_modules" (
    echo Installing dependencies...
    call npm install
)
echo Compiling TypeScript...
call npm run build
if exist "dist\index.js" (
    echo Build successful! Output: dist\index.js
) else (
    echo Build failed!
    exit /b 1
)

