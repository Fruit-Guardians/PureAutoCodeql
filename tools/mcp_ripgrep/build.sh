#!/bin/bash
echo "Building mcp-ripgrep..."
cd "$(dirname "$0")"

if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

echo "Compiling TypeScript..."
npm run build

if [ -f "dist/index.js" ]; then
    echo "Build successful! Output: dist/index.js"
    chmod +x dist/index.js
else
    echo "Build failed!"
    exit 1
fi

