#!/bin/bash
# 构建脚本：安装和编译 MCP 相关工具

echo "========================================"
echo " PureAutoCodeQL - MCP 工具构建脚本"
echo "========================================"
echo ""

# 检查 Node.js 是否安装
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到 Node.js，请先安装 Node.js (v18 或更高版本)"
    echo "下载地址: https://nodejs.org/"
    exit 1
fi

echo "[1/2] 检查 Node.js 版本..."
node --version
echo ""

# 构建 mcp_ripgrep
echo "[2/2] 构建 mcp_ripgrep..."
cd tools/mcp_ripgrep

if [ ! -d "node_modules" ]; then
    echo "  安装依赖..."
    npm install
    if [ $? -ne 0 ]; then
        echo "[错误] npm install 失败"
        cd ../..
        exit 1
    fi
else
    echo "  依赖已存在，跳过安装"
fi

echo "  编译 TypeScript..."
npm run build
if [ $? -ne 0 ]; then
    echo "[错误] TypeScript 编译失败"
    cd ../..
    exit 1
fi

cd ../..

# 检查编译结果
if [ -f "tools/mcp_ripgrep/dist/index.js" ]; then
    echo ""
    echo "========================================"
    echo " ✓ 构建成功！"
    echo "========================================"
    echo " mcp_ripgrep 已编译到: tools/mcp_ripgrep/dist/index.js"
    echo ""
    chmod +x tools/mcp_ripgrep/dist/index.js
else
    echo ""
    echo "[错误] 编译后的文件不存在"
    exit 1
fi

