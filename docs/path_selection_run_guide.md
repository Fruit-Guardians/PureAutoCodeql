# Path Selection 运行说明

本指南介绍如何在 PureAutoCodeQL 中运行整条分析流水线，以及如何单独调用 `PathSelectionService` 执行“第 6 模块 —— 路径选择与验证”。默认面向已经能够完成 CodeQL 查询的使用者。

---

## 1. 环境准备

1. **基础依赖**
   - Python 3.10+
   - [uv](https://github.com/astral-sh/uv)（推荐）或 pip
   - CodeQL CLI（用于前序查询步骤）
2. **安装项目依赖**
   ```bash
   uv sync
   # 若未安装 uv，可使用：
   # pip install -r requirements.txt
   ```
3. **配置 LLM/Provider**
   - 将各模型的 `api_key`、`base_url` 写入 `config/keys.toml`
   - 或在终端设置 `PUREAUTO_LLM_PROVIDER` 等环境变量（见 `config/config.py` 及 README）

---

## 2. 运行整条流水线（推荐）

1. 准备好案例输入（CVE JSON、补丁 diff、源码路径等），并放入 `projects/<CASE>/...`。
2. 执行分析：
   ```bash
   uv run python Analyze.py --case CVE-2024-XXXX
   # 常用参数：
   #   --stream           # 实时查看 LLM 思考过程
   #   --refresh-intel    # 强制刷新情报摘要
   #   --provider deepseek
   ```
3. 成功后会在 `output/<CASE>/<TIMESTAMP>/` 下生成：
   ```
   summary.md                # 各模块总结
   sarif/codeql-run.sarif    # CodeQL 原始 SARIF
   codeql/all-paths-raw.json # CodeQL dataFlowPath 原始结果
   path-selection/...        # LLM 精排输出
   ```
4. 第 6 模块会自动读取上述 `summary.md` 与 `codeql/all-paths-raw.json`，并在同目录生成：
   ```
   path-selection/report.md     # 人类可读报告
   path-selection/selection.json# 结构化结果
   path-selection/dataflow.json # 简洁 dataFlowPath 结果
   ```
   如果仅需查看最终路径，可直接打开这两个文件。

---

## 3. 单独运行 `PathSelectionService`

有时只想复用路径选择能力（例如复盘某次 CodeQL 执行结果），可以在具备以下三份文件后独立调用：

- `summary.md`：包含 CVE Analysis / Sink / Source 模块的输出
- `codeql/all-paths-raw.json`：CodeQL `dataFlowPath` 原始结果
- `source_root`：目标仓库的源码根目录（相对路径即可）

### 3.1 快速示例
项目提供了 `examples/path_selection_demo.py`，修改文件顶部的路径后执行：
```bash
uv run python examples/path_selection_demo.py
```
脚本会：
1. 初始化 `PathSelectionService`
2. 调用 `select_best_paths(...)`
3. 在控制台打印验证结果，并写入：
   - `path_selection_report.md`
   - `path_selection_result.json`

### 3.2 在自定义脚本中调用
```python
import asyncio
from services.path_selection import PathSelectionService
from config import get_chat_config

async def run():
    service = PathSelectionService(get_chat_config(), language="python")
    result = await service.select_best_paths(
        output_md_path="output/<CASE>/<TIME>/summary.md",
        result_json_path="output/<CASE>/<TIME>/codeql/all-paths-raw.json",
        source_root="projects/<CASE>/source_code",
        top_k=3,
    )
    print(result.to_markdown())

asyncio.run(run())
```
`top_k`、语言（python/java/c）等参数可根据需要调整。

---

## 4. 结果解读与常见问题

| 文件 | 说明 |
| --- | --- |
| `path-selection/report.md` | Markdown 报告，包含选中路径、LLM 理由、验证结果与覆盖分析 |
| `path-selection/selection.json` | 机器可读的完整结果，可与其他系统对接 |
| `path-selection/dataflow.json` | 精简版 dataFlowPath，便于第三方系统消费 |
| `*.log`（可选） | 控制台或日志文件，便于排查问题 |

**常见问题**
1. **找不到 `output.md` / `result.json`**  
   确认前序流水线是否跑通，或手动指定已有的 CodeQL 输出。
2. **LLM 调用失败**  
   `PathSelectionService` 会自动回退到确定性打分结果；检查 `config/keys.toml`、网络或模型配额。
3. **无路径通过验证**  
   查看 `verification.summary.issues`，通常是源码路径不匹配或危险 API 未命中，可适当放宽 CVE 分析描述并重新运行。

---

## 5. 进一步定制

- 可通过 `services/path_selection/path_feature_extractor.py`、`path_ranker.py` 调整特征或打分权重。
- 若要扩展更多语言，只需新增 `language_adapters/<lang>_adapter.py` 并注册到 `get_language_adapter`。
- 需要集成到其他流水线时，直接复用 `PathSelectionService` 即可，输入/输出完全走文件路径与标准字典。

如需更多实现细节，请参考 `docs/path_selection_solution.md` 或源码中的注释。祝使用顺利！
