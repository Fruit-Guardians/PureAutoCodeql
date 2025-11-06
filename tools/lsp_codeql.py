#!/usr/bin/env python3
# codeql_lsp_service.py
# A tiny HTTP service that keeps CodeQL LSP hot and serves fast diagnostics.
#
# Start:
#   python codeql_lsp_service.py --codeql "C:\path\to\codeql.exe" --pack-root "E:\your\pack\root" --port 8766
#
# Use:
#   POST http://127.0.0.1:8766/check
#   Body (application/json): { "code": "<CodeQL text>" }
#   Response JSON: { "diagnostics": [...], "summary": {...} }
#
# Optional endpoints:
#   GET  /health   -> {"ok": true}
#   POST /shutdown -> stop the server
#
# Notes:
#   - --pack-root 必须是包含 codeql-pack.yml/qlpack.yml 的目录（你的查询包根）。
#   - 服务启动时会预热一次：`codeql query compile --check-only`。
#   - 之后每次 /check 只是 didChange + 等待 publishDiagnostics，延迟很低。
#   - 默认使用 CodeQL 的“默认搜索路径”，无需 --search-path。

import argparse, json, os, sys, subprocess, threading, queue, time, pathlib, urllib.parse, http.server, socketserver

Path = pathlib.Path

# ---------------- LSP helpers ----------------
def to_uri(p: Path) -> str:
    posix = p.as_posix()
    if os.name == "nt":
        return "file:///" + urllib.parse.quote(posix, safe="/:._-")
    return "file://" + urllib.parse.quote(posix, safe="/:._-")

def write_msg(proc, payload: dict):
    if proc.poll() is not None:
        raise RuntimeError(f"language-server exited with code {proc.returncode}")
    data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    header = f"Content-Length: {len(data)}\r\n\r\n".encode('ascii')
    proc.stdin.write(header); proc.stdin.write(data); proc.stdin.flush()

def reader(stream, q: queue.Queue):
    while True:
        header = b""
        while b"\r\n\r\n" not in header:
            b1 = stream.read(1)
            if not b1:
                return
            header += b1
        try:
            headers = header.decode('ascii', errors='replace').split("\r\n")
            content_len = 0
            for h in headers:
                if h.lower().startswith("content-length:"):
                    content_len = int(h.split(":",1)[1].strip()); break
        except Exception:
            return
        body = stream.read(content_len)
        if not body:
            return
        try:
            msg = json.loads(body.decode('utf-8'))
            q.put(msg)
        except Exception:
            pass

def find_pack_root(start: Path) -> Path:
    cur = start
    while True:
        if (cur / "codeql-pack.yml").exists() or (cur / "qlpack.yml").exists():
            return cur
        if cur.parent == cur:
            return start
        cur = cur.parent

def summarize(diags):
    s = {"errors":0, "warnings":0, "information":0, "hints":0}
    for d in diags:
        sev = d.get("severity", 1) or 1
        if   sev == 1: s["errors"] += 1
        elif sev == 2: s["warnings"] += 1
        elif sev == 3: s["information"] += 1
        elif sev == 4: s["hints"] += 1
    return s

# ---------------- Hot LSP Engine ----------------
class HotCodeQL:
    def __init__(self, codeql: str, pack_root: Path, synchronous: bool=False, init_timeout: float=25.0, quiet_logs: bool=False):
        self.codeql = codeql
        self.pack_root = pack_root
        self.synchronous = synchronous
        self.init_timeout = init_timeout
        self.quiet_logs = quiet_logs

        # single virtual doc we keep open & update
        self.tmp_dir = self.pack_root / ".codeql-lsp-service"
        self.tmp_dir.mkdir(parents=True, exist_ok=True)
        self.doc_path = self.tmp_dir / "_agent_check.ql"
        if not self.doc_path.exists():
            self.doc_path.write_text("// temp ql file\n", encoding="utf-8")

        self.proc = None
        self.q = queue.Queue()
        self.uri = to_uri(self.doc_path)
        self.version = 0

    def start(self):
        # check codeql
        subprocess.run([self.codeql, "version"], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

        # prewarm (best effort): compile --check-only
        try:
            subprocess.run([self.codeql, "query", "compile", "--check-only", str(self.doc_path)],
                           check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            pass

        # start LSP
        cmd = [self.codeql, "execute", "language-server", "--check-errors=ON_CHANGE"]
        if self.synchronous:
            cmd.append("--synchronous")
        env = os.environ.copy()
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)

        t_out = threading.Thread(target=reader, args=(self.proc.stdout, self.q), daemon=True); t_out.start()
        if not self.quiet_logs:
            def _stderr_printer():
                while True:
                    line = self.proc.stderr.readline()
                    if not line:
                        return
                    try:
                        sys.stderr.write("[server] " + line.decode('utf-8', errors='replace'))
                    except Exception:
                        pass
            t_err = threading.Thread(target=_stderr_printer, daemon=True); t_err.start()

        root_uri = to_uri(self.pack_root)
        init_req = {
            "jsonrpc":"2.0","id":1,"method":"initialize",
            "params":{
                "processId": None,
                "clientInfo": {"name":"codeql-lsp-service","version":"1.0"},
                "rootUri": root_uri,
                "workspaceFolders":[{"uri": root_uri, "name": self.pack_root.name}],
                "capabilities":{"textDocument":{}, "workspace":{"configuration": True}},
                "trace":"off"
            }
        }
        write_msg(self.proc, init_req)

        deadline = time.time() + self.init_timeout
        initialized_ok = False
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise RuntimeError(f"LSP exited early: {self.proc.returncode}")
            try:
                payload = self.q.get(timeout=0.2)
            except queue.Empty:
                continue
            if isinstance(payload, dict) and payload.get("method") == "workspace/configuration" and "id" in payload:
                items = payload.get("params", {}).get("items", [])
                write_msg(self.proc, {"jsonrpc":"2.0","id":payload["id"],"result":[{} for _ in items]})
                continue
            if isinstance(payload, dict) and payload.get("method") == "client/registerCapability" and "id" in payload:
                write_msg(self.proc, {"jsonrpc":"2.0","id":payload["id"],"result":None})
                continue
            if isinstance(payload, dict) and payload.get("id") == 1:
                initialized_ok = True; break
        if not initialized_ok:
            raise RuntimeError("No response to initialize()")

        write_msg(self.proc, {"jsonrpc":"2.0","method":"initialized","params":{}})
        write_msg(self.proc, {"jsonrpc":"2.0","method":"workspace/didChangeConfiguration","params":{"settings":{}}})

        # didOpen once
        text = self.doc_path.read_text(encoding="utf-8", errors="replace")
        self.version = 1
        write_msg(self.proc, {
            "jsonrpc":"2.0","method":"textDocument/didOpen",
            "params":{"textDocument":{"uri": self.uri, "languageId":"ql", "version": self.version, "text": text}}
        })

        # drain initial diagnostics
        self._collect(tail=0.5)

    def _collect(self, tail: float):
        diags = []
        deadline = time.time() + tail
        while time.time() < deadline:
            if self.proc.poll() is not None:
                break
            try:
                payload = self.q.get(timeout=0.2)
            except queue.Empty:
                continue
            if not isinstance(payload, dict):
                continue
            if payload.get("method") == "workspace/configuration" and "id" in payload:
                items = payload.get("params", {}).get("items", [])
                write_msg(self.proc, {"jsonrpc":"2.0","id":payload["id"],"result":[{} for _ in items]})
                continue
            if payload.get("method") == "client/registerCapability" and "id" in payload:
                write_msg(self.proc, {"jsonrpc":"2.0","id":payload["id"],"result":None})
                continue
            if payload.get("method") == "textDocument/publishDiagnostics":
                params = payload.get("params", {})
                if params.get("uri") == self.uri:
                    diags = params.get("diagnostics", [])
                    deadline = time.time() + 0.6
        return diags

    def check_code(self, code_text: str, wait_secs: float = 8.0):
        # update local file (可选：也能直接只发 didChange，不写盘；为稳妥这里仍写盘)
        try:
            self.doc_path.write_text(code_text, encoding="utf-8")
        except Exception:
            pass
        self.version += 1
        write_msg(self.proc, {
            "jsonrpc":"2.0","method":"textDocument/didChange",
            "params":{
                "textDocument":{"uri": self.uri, "version": self.version},
                "contentChanges":[{"text": code_text}]
            }
        })
        diags = self._collect(tail=wait_secs)
        return {"diagnostics": diags, "summary": summarize(diags)}

    def shutdown(self):
        try:
            write_msg(self.proc, {"jsonrpc":"2.0","method":"textDocument/didClose","params":{"textDocument":{"uri": self.uri}}})
        except Exception:
            pass
        try:
            write_msg(self.proc, {"jsonrpc":"2.0","id":2,"method":"shutdown","params":None})
            time.sleep(0.1)
            write_msg(self.proc, {"jsonrpc":"2.0","method":"exit","params":{}})
        except Exception:
            pass
        try:
            self.proc.terminate()
        except Exception:
            pass

# ---------------- HTTP server ----------------
class Handler(http.server.BaseHTTPRequestHandler):
    # shared engine
    engine: HotCodeQL = None

    def _send_json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            return self._send_json({"ok": True})
        return self._send_json({"error":"not found"}, 404)

    def do_POST(self):
        if self.path == "/check":
            length = int(self.headers.get("Content-Length","0"))
            try:
                body = self.rfile.read(length)
                data = json.loads(body.decode('utf-8'))
                code = data.get("code","")
                if not isinstance(code, str):
                    return self._send_json({"error":"bad payload: 'code' must be string"}, 400)
                res = self.engine.check_code(code)
                # 如果LSP进程退出，尝试重启服务
                if "error" in res and ("LSP进程退出" in res["error"] or "LSP检查异常" in res["error"]):
                    print(f"[LSP] 检测到LSP进程异常: {res['error']}")
                    try:
                        print("[LSP] 尝试重启LSP引擎...")
                        self.engine.shutdown()
                        time.sleep(2)
                        self.engine.start()
                        print("[LSP] LSP引擎重启成功，重新检查代码...")
                        res = self.engine.check_code(code)
                        if "error" not in res:
                            return self._send_json(res, 200)
                        else:
                            return self._send_json({"error": f"LSP重启后仍然失败: {res['error']}"}, 500)

                            
                    except Exception as restart_error:
                        return self._send_json({"error": f"LSP重启失败: {restart_error}"}, 500)
                return self._send_json(res, 200)
            except Exception as e:
                return self._send_json({"error": f"{type(e).__name__}: {e}"}, 500)

        if self.path == "/shutdown":
            self._send_json({"ok": True})
            threading.Thread(target=self.server.shutdown, daemon=True).start()
            return

        return self._send_json({"error":"not found"}, 404)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--codeql", default="codeql", help="Path to codeql executable or 'codeql' if in PATH")
    ap.add_argument("--pack-root", required=True, help="Directory containing codeql-pack.yml/qlpack.yml")
    ap.add_argument("--port", type=int, default=8766, help="HTTP port")
    ap.add_argument("--synchronous", action="store_true", help="Run LS single-threaded (more stable, slightly slower)")
    ap.add_argument("--quiet-logs", action="store_true", help="Do not mirror LS stderr")
    args = ap.parse_args()

    pack_root = Path(args.pack_root).resolve()
    if not pack_root.exists():
        print(f"[ERR] pack root not found: {pack_root}", file=sys.stderr); sys.exit(1)
    if not ((pack_root / "codeql-pack.yml").exists() or (pack_root / "qlpack.yml").exists()):
        print(f"[ERR] pack root missing codeql-pack.yml/qlpack.yml: {pack_root}", file=sys.stderr); sys.exit(1)

    # start engine
    engine = HotCodeQL(codeql=args.codeql, pack_root=pack_root, synchronous=args.synchronous,
                       init_timeout=25.0, quiet_logs=args.quiet_logs)
    try:
        engine.start()
    except Exception as e:
        print(f"[ERR] failed to start LSP: {e}", file=sys.stderr); sys.exit(1)

    Handler.engine = engine
    with socketserver.ThreadingTCPServer(("127.0.0.1", args.port), Handler) as httpd:
        print(f"[READY] CodeQL LSP service on http://127.0.0.1:{args.port}")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            pass
        finally:
            engine.shutdown()
            print("[BYE] shutdown complete.")

if __name__ == "__main__":
    main()
