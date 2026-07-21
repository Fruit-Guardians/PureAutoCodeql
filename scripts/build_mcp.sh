#!/bin/bash
# 构建脚本：安装和编译 MCP 相关工具
# 可从任意 cwd 调用；始终以仓库根目录为基准。

set -euo pipefail

echo "========================================"
echo " PureAutoCodeQL - MCP 工具构建脚本"
echo "========================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

# 检查 Node.js 是否安装
if ! command -v node &> /dev/null; then
    echo "[错误] 未找到 Node.js，请先安装 Node.js (v18 或更高版本)"
    echo "下载地址: https://nodejs.org/"
    exit 1
fi

echo "[1/2] 检查 Node.js 版本..."
NODE_VERSION=$(node --version)
echo "Node.js 版本: $NODE_VERSION"

# 检查 Node.js 版本是否满足要求 (v18 或更高)
NODE_MAJOR=$(echo "$NODE_VERSION" | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_MAJOR" -lt 18 ]; then
    echo "[错误] Node.js 版本过低，需要 v18 或更高版本，当前版本: $NODE_VERSION"
    exit 1
fi
echo "✓ Node.js 版本检查通过"
echo ""

# 构建 mcp_ripgrep
echo "[2/2] 构建 mcp_ripgrep..."

if [ ! -d "tools/mcp_ripgrep" ]; then
    echo "[错误] tools/mcp_ripgrep 目录不存在"
    exit 1
fi

cd tools/mcp_ripgrep || {
    echo "[错误] 无法进入 tools/mcp_ripgrep 目录"
    exit 1
}

echo "当前工作目录: $(pwd)"

if [ ! -d "node_modules" ]; then
    echo "  安装依赖..."
    npm install || {
        echo "[错误] npm install 失败"
        exit 1
    }
    echo "✓ 依赖安装成功"
else
    echo "  依赖已存在，跳过安装"
fi

echo "  编译 TypeScript..."
npm run build || {
    echo "[错误] TypeScript 编译失败"
    exit 1
}
echo "✓ TypeScript 编译成功"

cd "$ROOT_DIR" || {
    echo "[错误] 无法返回仓库根目录"
    exit 1
}

# 检查编译结果
DIST_FILE="tools/mcp_ripgrep/dist/index.js"
echo "  检查编译结果: $DIST_FILE"

if [ -f "$DIST_FILE" ]; then
    FILE_SIZE=$(stat -f%z "$DIST_FILE" 2>/dev/null || stat -c%s "$DIST_FILE" 2>/dev/null || echo "0")
    if [ "$FILE_SIZE" -gt 0 ]; then
        echo ""
        echo "========================================"
        echo " ✓ 构建成功！"
        echo "========================================"
        echo " mcp_ripgrep 已编译到: $DIST_FILE"
        echo " 文件大小: $FILE_SIZE 字节"
        echo ""

        chmod +x "$DIST_FILE" || {
            echo "[警告] 无法设置执行权限，但不影响使用"
        }

        if grep -q "mcp-ripgrep\|MCP\|ripgrep" "$DIST_FILE" 2>/dev/null; then
            echo "✓ 文件内容验证通过"
        else
            echo "[警告] 文件内容验证失败，但文件已生成"
        fi
    else
        echo ""
        echo "[错误] 编译后的文件存在但为空"
        exit 1
    fi
else
    echo ""
    echo "[错误] 编译后的文件不存在: $DIST_FILE"
    echo "调试信息:"
    echo "- 当前目录: $(pwd)"
    echo "- dist 目录内容:"
    if [ -d "tools/mcp_ripgrep/dist" ]; then
        ls -la "tools/mcp_ripgrep/dist/" 2>/dev/null || echo "无法列出目录内容"
    else
        echo "dist 目录不存在"
    fi
    exit 1
fi
