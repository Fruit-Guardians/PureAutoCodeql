# PureAutoCodeql

基于多智能体架构的自动化漏洞分析工具，使用 CodeQL 和 AI 技术进行 Java 代码安全分析。

## 项目结构

```
PureAutoCodeql/
├── Analyze.py                    # 主分析器，多智能体协调器
├── agents/                       # 智能体模块
│   ├── cve_analysis_agent.py    # CVE 漏洞分析智能体
│   ├── java_sink_path_agent.py  # Java Sink 路径分析智能体
│   └── java_source_analysis_agent.py # Java 源码分析智能体
├── utils/                        # 工具模块
│   ├── io.py                    # 输入输出工具
│   └── java.py                  # Java 代码处理工具
├── h5-vsan-service.jar_Decompiler.com/  # 示例漏洞代码（CVE-2021-21985）
├── CVE-2021-21985.json          # 漏洞信息配置文件
├── CVE-2021-21985.diff          # 漏洞补丁文件
├── output.md                    # 分析结果输出
└── pyproject.toml              # 项目依赖配置
```

## 安装依赖

```bash
# 使用 uv 安装依赖（推荐）
uv sync

# 或使用 pip 安装
pip install -r requirements.txt
```

## 使用示例

### 快速运行

```bash
uv run Analyze.py
```

结果会输出到output.md方便查看

建议使用openspec辅助vibe coding



## CodeQL 执行与输出

本项目的 CodeQL 查询执行已统一为 `codeql database analyze`，并固定输出 **SARIF 2.1.0** 报告：

```bash
codeql database analyze <database> <query-or-pack> \
  --format=sarif-v2.1.0 \
  --output=/output/result_YYYYMMDD_HHMMSS.sarif
```

- 输出目录：`/output/`（若不可写则降级到当前目录下的 `./output/`）
- 文件命名：`result_YYYYMMDD_HHMMSS.sarif`（时间戳取自工具调用时间）

