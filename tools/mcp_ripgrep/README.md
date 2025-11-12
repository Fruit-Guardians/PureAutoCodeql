# MCP Ripgrep Server (Windows-Compatible Fork)

这是一个修复了 Windows 路径处理问题的 mcp-ripgrep 本地版本。

## 修复内容

1. **跨平台路径转义**：Windows 使用双引号，Unix 使用单引号
2. **路径规范化**：自动将相对路径转换为绝对路径
3. **Windows 路径支持**：正确处理反斜杠和特殊字符

## 编译和安装

```bash
cd tools/mcp_ripgrep
npm install
npm run build
```

编译后的文件将位于 `dist/index.js`。

## 使用

在 `services/llm_service.py` 中已配置为使用本地版本。

