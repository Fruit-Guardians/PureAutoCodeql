# CodeQL LSP 启动失败修复说明

适用范围：本工程 `PureAutoCodeql` 的语言服务包装模块 `pure_auto_codeql/tools/lsp_codeql.py`

## 问题症状
- 启动后语言服务未完成初始化，进程退出并打印：`[ERR] failed to start LSP: No response to initialize()`。
- HTTP 健康检查 `GET /health` 无法连接（远程服务器不可达）。
- 语言服务日志出现多次 `Creating pack state ... with library path [] and dbscheme [empty]`。

## 根因分析
- 缺少包搜索路径（主要原因）：语言服务在 `initialize` 阶段需要解析工作区内的 CodeQL packs（如 `codeql/cpp-all`）。最初未显式传入 `--search-path`，且客户端 `workspace/configuration` 返回空对象，导致语言服务无法定位标准库包，初始化卡住直至超时。
- JSON-RPC 头不完整（次要）：仅发送 `Content-Length`，在 Windows 上 CodeQL 语言服务对 `Content-Type` 更严格，可能忽略不合规的消息。
- 初始化超时过短（次要）：语言服务初始化会预构建 pack 状态与缓存，25 秒易误判超时。

## 修复方案
1. 在语言服务启动命令中显式传入 `--search-path`，包含：
   - 当前使用的 pack 根目录（临时或工作区 pack）。
   - 用户包缓存目录：`%USERPROFILE%\.codeql\packages`（Windows；Linux/macOS 使用 `~/.codeql/packages`）。
2. 给所有 LSP 请求添加 `Content-Type: application/vscode-jsonrpc; charset=utf-8` 头。
3. 将初始化超时提升至 60 秒，并启用 `--synchronous` 与 `-v --log-to-stderr` 便于稳定与排错。

## 代码变更
文件：`pure_auto_codeql/tools/lsp_codeql.py`

- 增加 JSON-RPC 头：
```python
def write_msg(proc, payload: dict):
    data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    header = (
        f"Content-Length: {len(data)}\r\n"
        f"Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n"
    ).encode('ascii')
    proc.stdin.write(header)
    proc.stdin.write(data)
    proc.stdin.flush()
```

- 启动命令加入 `--search-path`、日志参数，并提升超时：
```python
cmd = [self.codeql, "execute", "language-server", "--check-errors=ON_CHANGE"]

search_dirs = [str(self.pack_root)]
user_packages = Path.home() / ".codeql" / "packages"
if user_packages.exists():
    search_dirs.append(str(user_packages))
sep = ";" if os.name == "nt" else ":"
cmd.extend(["--search-path", sep.join(search_dirs)])
cmd.extend(["-v", "--log-to-stderr"])

# main() 中：
engine = HotCodeQL(..., init_timeout=60.0, ...)
```

## 启动与验证
以 PowerShell 为例：

- 启动：
```powershell
python -m tools.lsp_codeql --pack-root "d:\Tmp_CTF\qwbwork\test\PureAutoCodeql\temp\codeql_temp\20251107_230709_944988" --port 8770 --synchronous
```

- 健康探测：
```powershell
Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8770/health' -TimeoutSec 5 | Select-Object -ExpandProperty Content
# 预期输出: {"ok": true}
```

- 语法检查验证：
```powershell
$body = @{ code = @"
import cpp

from Function f
select f, "ok"
"@ }
Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8770/check' -Method POST -Body ($body | ConvertTo-Json -Depth 3) -ContentType 'application/json' | Select-Object -ExpandProperty Content
# 预期输出: 无诊断或仅提示级别诊断（解析成功）
```

## 统一建议（API 服务）
- 为保证一致性，建议在 `services/lsp_service.py` 启动语言服务时也增加：
  - `--search-path "<pack_root>;<USERPROFILE>\.codeql\packages"`
  - 可配置的初始化超时（默认 60 秒）
  - `--synchronous`、`-v --log-to-stderr`
- 如需由客户端提供 packs，可在 `workspace/configuration` 返回包含搜索路径或附加 packs 的设置；当前通过 CLI 参数已足够。

## 常见排障
- 依赖缺失：
  - 在 pack 根目录执行 `codeql pack install -v`
  - 使用 `codeql resolve packs --kind library --format=json` 验证 `codeql/<lang>-all` 可用
- 搜索路径分隔符：Windows 使用 `;`，Linux/macOS 使用 `:`。
- 版本兼容：确保 CLI 与已安装 packs 版本匹配（例如 `codeql/cpp-all` 6.x）。
- 慢启动：保持 `init_timeout` ≥ 60 秒；`--synchronous` 提升稳定性。

## 变更记录
- 添加 JSON-RPC `Content-Type` 头，保证 Windows 下消息不被忽略。
- 增加 `--search-path`（包含 pack 根与用户包缓存），解决 packs 无法解析导致的初始化阻塞。
- 提升初始化超时至 60 秒，加入 `-v --log-to-stderr` 与 `--synchronous`，便于稳定和诊断。
- 验证通过：`/health` 返回 `{"ok": true}`；示例 C++ 片段 `/check` 返回空诊断（解析正常）。