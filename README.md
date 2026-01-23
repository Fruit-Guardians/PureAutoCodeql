# PureAuto

<div align="center">

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.13%2B-blue.svg)](https://python.org)

**基于多智能体架构的自动化漏洞分析工具，使用 AI 技术进行代码安全分析**

</div>

## ✨ 特性

- 🤖 **多智能体协作** - CVE分析、Sink分析、Source分析、Path分析四大Agent协同工作
- 🧠 **LLM驱动** - 支持多种LLM提供商（DeepSeek、SiliconFlow、智谱GLM等）
- 💻 **多语言支持** - 支持Java、C/C++、Python等多种编程语言
- 📊 **情报整合** - 自动收集和整合GHSA/NVD漏洞情报数据
- 🔍 **深度分析** - 从CVE情报到完整漏洞路径分析的全流程自动化

## 🏗️ 架构设计

```
PureAuto/
├── 🎯 core/                    # 核心层 - 业务逻辑编排
│   ├── context.py              # 上下文管理
│   ├── pipeline.py             # 分析流水线
│   └── orchestrator.py         # 分析编排器
├── 🔧 services/                # 服务层 - 基础服务
│   ├── llm_service.py          # LLM服务
│   └── language_detector.py    # 语言检测
├── 🤖 agents/                  # Agent层 - 专业分析Agent
│   ├── cve_analysis_agent.py   # CVE分析
│   ├── unified_sink_path_agent.py    # Sink分析
│   ├── unified_source_analysis_agent.py # Source分析
│   └── path_analysis_agent.py  # Path分析
├── 📦 utils/                   # 工具函数层
├── 📝 prompts/                 # 提示词管理
├── ⚙️ config/                  # 配置系统
├── 📊 Information/             # 情报收集模块
└── 🚀 Analyze.py               # 主入口
```

## 🚀 快速开始

### 安装依赖

```bash
# 使用 uv 安装依赖（推荐）
uv sync

# 或使用 pip
pip install -e .
```

### 配置LLM

```bash
# 设置API Key（选择其一）
export DEEPSEEK_API_KEY=your_api_key
export SILICONFLOW_API_KEY=your_api_key
export ZHIPU_API_KEY=your_api_key
```

### 运行分析

```bash
# 分析案例
python Analyze.py --case CVE-2021-21985

# 显示AI思考过程
python Analyze.py --case CVE-2021-21985 --stream

# 指定模型提供商
python Analyze.py --case CVE-2021-21985 --provider deepseek

# 列出所有可用案例
python Analyze.py --list

# 验证案例有效性
python Analyze.py --validate CVE-2021-21985

# 查看可用的模型提供商
python Analyze.py --list-providers
```

## 📊 分析流程

```
CVE情报收集 → CVE分析 → Sink点分析 → Source点分析 → Path分析 → 分析报告
```

### 各步骤输出

1. **CVE分析** - 漏洞信息、触发条件、影响范围
2. **Sink分析** - 危险函数识别、执行路径分析
3. **Source分析** - 用户输入入口、数据流起点
4. **Path分析** - Source到Sink的完整数据流路径

## 🔧 配置

### 支持的LLM提供商

| 提供商 | 推荐模型 | 状态 |
|--------|----------|------|
| DeepSeek | deepseek-chat | ✅ 推荐 |
| SiliconFlow | deepseek-ai/DeepSeek-V3 | ✅ 稳定 |
| 智谱GLM | glm-4 | ✅ 可用 |

### 命令行参数

```
--case CASE_ID      分析指定案例
--stream            显示AI思考过程（默认启用）
--no-stream         禁用AI思考过程
--provider NAME     指定模型提供商
--model MODEL       指定模型名称
--language LANG     指定编程语言
--refresh-intel     强制刷新情报数据
--output FILE       指定输出文件
```

## 📁 案例目录结构

```
projects/
└── CVE-2021-21985/
    ├── cve.json          # CVE元数据
    ├── *.diff / *.patch  # 补丁文件
    └── source/           # 漏洞相关源代码
```

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

---

<div align="center">

**⭐ 如果这个项目对您有帮助，请给我们一个Star！**

</div>