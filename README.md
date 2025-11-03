# PureAutoCodeql

基于多智能体架构的自动化漏洞分析工具，使用 CodeQL 和 AI 技术进行 Java 、C、Python代码安全分析。

## 项目结构

```
PureAutoCodeql/
├── Analyze.py                    # 主分析器，多智能体协调器
├── agents/                       # 智能体模块
│   ├── cve_analysis_agent.py    # CVE 漏洞分析智能体
│   ├── unified_sink_path_agent.py # 统一Sink路径分析智能体（支持Java/Python/C++）
│   ├── java_source_analysis_agent.py # Java 源码分析智能体
│   ├── python_source_analysis_agent.py # Python 源码分析智能体
│   └── c_source_analysis_agent.py # C/C++ 源码分析智能体
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

## 使用示例a

### 快速运行

```bash
uv run Analyze.py
```

临时结果会输出到output.md，分析得到的sarif文件会保存到output，推荐使用sarif viewer读取

## LLM 配置

本项目使用集中化的 LLM 配置系统（`config.py`），推荐使用两种模型：

- 推理模型: 用于 CodeQL 查询生成/验证等需要强推理的场景
- 一般模型:  用于一般分析任务和普通对话

### 代码中使用

```python
from config import get_think_config, get_chat_config, LLMRole, get_llm_config

# 便捷方式
think_config = get_think_config()  # 用于 CodeQL 生成/验证
chat_config = get_chat_config()    # 用于一般分析

# 通过角色获取
config = get_llm_config(LLMRole.THINK)  # 或 LLMRole.CHAT
```

## CodeQL 执行与输出

本项目的 CodeQL 查询执行已统一为 `codeql database analyze`，并固定输出 **SARIF 2.1.0** 报告：

```bash
codeql database analyze <database> <query-or-pack> \
  --format=sarif-v2.1.0 \
  --output=/output/result_YYYYMMDD_HHMMSS.sarif
```

- 输出目录：`/output/`（若不可写则降级到当前目录下的 `./output/`）
- 文件命名：`result_YYYYMMDD_HHMMSS.sarif`（时间戳取自工具调用时间）
