# NVD CVE 查询脚本

此仓库提供一个轻量级命令行助手，可直接从国家漏洞数据库（NVD）的 REST API 获取特定 CVE 标识符的整理信息。

## 环境需求

- Python 3.8 或更新版本（已在 Python 3.13 上测试）。
- 可访问 `https://services.nvd.nist.gov` 的网络连接。
- （可选）NVD API 密钥，用于提升请求频率限制。可在 <https://nvd.nist.gov/developers/request-an-api-key> 申请。

## 快速开始

```bash
python fetch_cve.py CVE-2023-34362
```

脚本会打印结构化摘要，其中包含发布时间、CVSS 分值与向量、英文描述、映射的 CWE 弱点、受影响的 CPE 以及去重后的参考链接。

要在一次执行中查询多个 CVE，请继续追加参数：

```bash
python fetch_cve.py CVE-2023-34362 CVE-2024-12345
```

当提供多个标识符时，每个 CVE 的结果都会以分隔横幅区分。

## 使用 API 密钥

如果你拥有 API 密钥，可以通过环境变量设置：

```bash
set NVD_API_KEY=your_key_here        # PowerShell / cmd
export NVD_API_KEY=your_key_here     # bash / zsh
```

也可以通过 `--api-key` 参数显式传入：

```bash
python fetch_cve.py CVE-2023-34362 --api-key your_key_here
```

脚本会将该密钥添加到 `apiKey` HTTP 头部，从而获得高于匿名请求的吞吐率。

## 错误处理

- 缺失的 CVE 项会在标准错误输出 `Error: CVE <id> not found in NVD data.`。
- 网络抖动会自动重试最多三次，并在重试间隔中短暂停顿。
- 对于 HTTP 429 响应，会遵循服务端返回的 `Retry-After` 头部等待。

## 扩展脚本

代码围绕三个辅助层组织：

1. `fetch_cve_payload` 负责从 API 获取原始 JSON，并实现基础重试逻辑。
2. `extract_vulnerability_info` 将响应规范化为紧凑的 Python 字典（元数据、度量、弱点、参考链接、受影响的 CPE）。
3. `format_vulnerability_info` 将处理后的数据格式化为控制台输出。

如果你想在更大的工具中复用这一查询能力，可以从其他 Python 模块导入这些函数。

## 故障排查

- 确认系统时间准确；某些网络在时间漂移时会阻断 TLS 请求。
- 如果在没有 API 密钥的情况下频繁收到 `429` 限速错误，请稍候几分钟或申请密钥。
- 在代理环境下运行时，请设置标准的 `HTTP_PROXY` 和 `HTTPS_PROXY` 环境变量，让 `urllib` 能正确转发流量。
