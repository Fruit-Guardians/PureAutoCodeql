# GitHub 安全公告查询脚本说明（`fetch_ghsa.py`）

## 概述

`fetch_ghsa.py` 是一个命令行辅助脚本，用于从 GitHub Security Advisory 数据库获取并整理安全公告信息。  
它既支持直接通过 GHSA（GitHub Security Advisory）标识符查询，也支持使用 CVE 编号定位相关的公告。  
脚本还会在可能的情况下自动追踪仓库级安全公告，方便了解具体项目中的修复细节。

## 运行前准备

- Python 3.8 或更高版本（已在 3.13 上验证）。
- 可访问 `https://api.github.com` 的网络环境。
- GitHub 访问令牌（推荐）：  
  - 建议使用具备 `security_events:read` 范围的 Fine-grained Token，或者具备 `repo` / `repository_advisories:read` 权限的经典令牌。  
  - 某些请求在匿名状态下会遭遇速率限制或返回精简数据，令牌可显著改善体验。

## 快速使用

```bash
# 通过 GHSA 标识符查询
python fetch_ghsa.py GHSA-22c2-9gwg-mj59

# 通过 CVE 编号查询（可能返回多个 GHSA 结果）
python fetch_ghsa.py CVE-2025-46725

# 同时查询多个标识符，结果之间会自动插入分隔线
python fetch_ghsa.py CVE-2025-46725 GHSA-xxxx-xxxx-xxxx
```

## 参数说明

| 参数              | 说明 |
| ----------------- | ---- |
| `identifiers`     | 必填位置参数。可为 GHSA 或 CVE 标识符，大小写不敏感。多个参数将依次处理。 |
| `--token`         | 可选。指定 GitHub 访问令牌，默认为读取 `GITHUB_TOKEN` 环境变量。 |

> 建议提前在 shell 中导出令牌，例如：  
> `set GITHUB_TOKEN=ghp_xxx`（PowerShell/cmd）或 `export GITHUB_TOKEN=ghp_xxx`（bash/zsh）。

## 输出内容解析

脚本会针对每个查询结果输出结构化文本，重点包括：

- **顶部标题**：主要 GHSA 标识符，若通过 CVE 查询，会提示匹配来源。
- **时间线**：公布时间、最近更新时间及撤回时间（若存在）。
- **概要信息**：公告摘要、严重级别、CVSS 向量/分值、公告类型。
- **别名**：与该 GHSA 相关的其它标识，例如对应的 CVE。
- **链接集合（Links）**：
  - GitHub 公告页面；
  - 仓库源代码位置（如公告提供）；
  - 仓库级公告详情页及其 API 地址。
- **描述（Description）**：原文描述，会自动换行。
- **CWE 列表**：关联的弱点类型。
- **受影响的软件包（Affected Packages）**：包括生态、包名、受影响版本范围、修复版本、相关函数（若有）。
- **仓库公告（Repository Advisory）**：若公告关联到具体仓库，输出其状态、严重级别、时间线与 CVSS 信息。
- **参考链接（References）**：官方或社区的更多阅读资料。

## 仓库公告自动补充

- 当全局公告提供 `repository_advisory_url` 字段时，脚本会发起额外请求，拉取仓库级安全公告并融合信息。
- 同一次命令运行期间，如果多个查询指向同一仓库公告，脚本会使用内存缓存避免重复请求。
- 若仓库公告不可访问（权限或不存在），会在标准错误输出警告，并继续展示已获取的全局公告内容。

## 错误与警告处理

| 提示信息                               | 原因与处理建议 |
| -------------------------------------- | -------------- |
| `Authentication failed`                | 令牌缺失或权限不足；请检查令牌作用域，或通过 `--token` 显式传入。 |
| `Repository advisory not found...`     | 仓库公告被删除、私有或需要额外权限。可以忽略警告，或在拥有访问权限的账号下重试。 |
| `rate limit exceeded` / HTTP 403       | 触发 GitHub 速率限制，等待片刻或使用权限更高的令牌。 |
| `CVE ... is not linked...`             | 指定 CVE 暂未收录到 GitHub 公告，可尝试过段时间再查或手动检索 GHSA。 |

脚本对网络抖动具有基础重试能力：遇到短暂的 `URLError` 或带有 `Retry-After` 的 403 响应时，最多重试三次，并遵循服务端建议的等待时间。

## 小贴士

- 想要在其它 Python 代码中复用逻辑，可以直接导入 `fetch_advisory_payload`、`extract_advisory_info`、`format_advisory_info` 等函数。
- 如果需要机器可读输出，可以自行修改脚本，将 `extract_advisory_info` 返回的字典序列化为 JSON。
- 若经常批量查询，建议准备具有更高限额的令牌，并关注 `Warning:` 行以排查逐条查询的异常。

