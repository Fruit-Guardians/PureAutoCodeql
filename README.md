# PureAutoCodeQL

<div align="center">

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.13%2B-blue.svg)](https://python.org)
[![CodeQL](https://img.shields.io/badge/CodeQL-Automated%20Security%20Analysis-green.svg)](https://codeql.github.com/)
[![FastAPI](https://img.shields.io/badge/FastAPI-HTTP%20API-green.svg)](https://fastapi.tiangolo.com/)

**基于多智能体架构的自动化漏洞分析工具，使用 CodeQL 和 AI 技术进行 Java、C/C++、Python 代码安全分析**

[📖 文档](#-详细文档) · [🚀 快速开始](#-快速开始) · [🏗️ 架构](#-架构设计) · [🔧 配置](#-llm-配置) · [🌐 API 服务](#-http-api-服务)

</div>

## 📚 目录

- [核心特性](#-核心特性)
- [架构设计](#-架构设计)
- [快速开始](#-快速开始)
- [LLM 配置](#-llm-配置)
- [HTTP API 服务](#-http-api-服务)
- [高级功能](#-高级功能)
- [使用场景](#-使用场景)
- [开发指南](#-开发指南)
- [详细文档](#-详细文档)

## ✨ 核心特性

### 🎯 **多智能体架构 (v2.0)**
- ✅ **分层架构设计** - 核心层、服务层、Agent层、工具层清晰分离
- ✅ **统一编排器** - `AnalysisOrchestrator` 统一管理分析流程
- ✅ **服务化组件** - 可复用的 LLM、LSP、语言检测、知识库服务
- ✅ **专业 Agent 系统** - CVE分析、Source分析、Sink分析、CodeQL生成等专业Agent
- ✅ **向后兼容** - 现有代码无需修改即可使用
- ✅ **异步优化** - 全异步架构，支持高并发处理

### 🔬 **智能分析能力**
- ✅ **自动语言检测** - 智能识别 Java、C/C++、Python 代码
- ✅ **情报收集系统** - 自动从 NVD、GHSA 获取漏洞情报
- ✅ **路径选择服务** - 基于 LLM 的智能路径筛选与验证
- ✅ **Flow Break 检测** - 自动检测数据流断点并生成补边代码
- ✅ **知识库支持** - Python CodeQL 知识库，包含模板、案例、辅助谓词
- ✅ **额外输入文件** - 支持在 `inputs/` 目录添加任意格式的补充信息

### 🛠️ **工具生态**
- ✅ **CodeQL Compose** - 自动生成、编译、执行 CodeQL 查询
- ✅ **LSP 集成** - CodeQL 语法检查和定义查找
- ✅ **MCP Ripgrep** - 高效代码搜索工具
- ✅ **多模型支持** - DeepSeek、SiliconFlow、智谱GLM、Kimi、Gemini
- ✅ **自定义提供商** - 通过 TOML 配置任意 OpenAI 兼容 API

### 🌐 **HTTP API 服务**
- ✅ **FastAPI 服务器** - RESTful API 和 SSE 流式输出
- ✅ **项目管理** - 案例创建、列表、详情查询
- ✅ **异步分析** - 后台任务执行，实时进度反馈
- ✅ **多语言支持** - API 路由自动适配不同编程语言

### 📊 **技术亮点**

| 特性 | 实现方式 | 优势 |
|------|---------|------|
| 代码组织 | 分层模块化设计 | 清晰的职责分离，易于维护和扩展 |
| 分析流程 | Pipeline + Orchestrator | 灵活的步骤组合，支持自定义流程 |
| 多模型支持 | 统一配置系统 | 零代码切换，支持自定义提供商 |
| 性能优化 | 全异步架构 | 高并发处理，资源利用率高 |
| 知识管理 | JSON 知识库 | 标签驱动检索，上下文精准 |
| 可观测性 | 统一日志 + SSE 流 | 实时进度反馈，便于调试 |

## 🏗️ 架构设计

### 分层架构

```
PureAutoCodeql/
├── 🎯 core/                         # 核心层 - 业务逻辑编排
│   ├── context.py                   # 分析上下文和配置管理
│   ├── pipeline.py                  # 分析流水线和步骤定义
│   └── orchestrator.py              # 分析编排器（统一入口）
│
├── 🤖 agents/                       # Agent层 - 专业分析Agent
│   ├── cve_analysis_agent.py        # CVE漏洞分析（提取漏洞类型、技术细节）
│   ├── unified_source_analysis_agent.py # Source点分析（识别污点源）
│   ├── unified_sink_path_agent.py   # Sink点分析（识别危险函数）
│   └── codeql_gen_agents/           # CodeQL生成Agent集合
│       ├── codeql_gen_agent.py      # 查询生成
│       ├── codeql_error_agent.py    # 错误诊断
│       └── codeql_fix_inplace_agent.py # 就地修复
│
├── 🔧 services/                     # 服务层 - 基础服务
│   ├── llm_service.py               # LLM服务（多Agent协调）
│   ├── lsp_service.py               # CodeQL LSP语法检查
│   ├── language_detector.py         # 编程语言自动检测
│   ├── codeql_execution.py          # CodeQL命令执行
│   ├── flow_break_detection.py      # 数据流断点检测
│   ├── path_selection/              # 路径选择服务
│   │   ├── selector.py              # 主选择器
│   │   ├── llm_analyzer.py          # LLM智能分析
│   │   ├── path_verifier.py         # 路径验证
│   │   └── language_adapters/       # 多语言适配器
│   └── knowledge_base/              # 知识库服务
│       ├── python.py                # Python知识库
│       └── base.py                  # 知识库基类
│
├── 🛠️ tools/                        # 工具层 - 具体工具实现
│   ├── codeql_compose.py            # CodeQL组合工具（生成+编译+执行）
│   ├── lsp_codeql.py                # LSP CodeQL工具
│   ├── lsp_lookup_tool.py           # LSP定义查找
│   └── mcp_ripgrep/                 # MCP Ripgrep搜索工具
│
├── 📦 utils/                        # 工具函数层 - 通用工具
│   ├── case.py                      # 案例解析和资产发现
│   ├── intel.py                     # 情报收集（NVD/GHSA集成）
│   ├── codeql.py                    # CodeQL辅助函数
│   ├── sarif_utils.py               # SARIF结果解析
│   └── logger.py                    # 统一日志系统
│
├── 📝 prompts/                      # 提示词层 - 提示词管理
│   ├── codeql_prompts.py            # CodeQL生成提示词
│   ├── sink_prompt_manager.py       # Sink分析提示词
│   └── source_prompt_manager.py     # Source分析提示词
│
├── 🌐 api/                          # HTTP API服务
│   ├── server.py                    # FastAPI服务器
│   ├── analysis_routes.py           # 分析任务路由
│   ├── projects_routes.py           # 项目管理路由
│   └── task_manager.py              # 异步任务管理
│
├── ⚙️ config/                       # 配置系统
│   ├── core.py                      # 核心配置逻辑
│   ├── display.py                   # Rich美化显示
│   ├── cli.py                       # 命令行工具
│   └── keys.toml                    # 配置文件
│
├── 📊 Information/                  # 情报收集模块
│   ├── nvd_info_fetch.py            # NVD数据获取
│   └── ghsa_fetch.py                # GHSA数据获取
│
├── 📚 resources/codeql/             # CodeQL资源
│   └── python/                      # Python知识库
│       ├── knowledge_base/          # JSON知识库（模板、案例、辅助谓词）
│       └── py/                      # CVE案例查询
│
├── 🚀 Analyze.py                    # 主入口文件（CLI）
└── 📄 pyproject.toml                # 项目配置和依赖
```

### 核心组件详解

#### 1. 编排层（Core）

- **AnalysisOrchestrator** - 分析编排器
  - 统一入口，协调整个分析流程
  - 管理案例解析、情报收集、语言检测
  - 创建和执行分析流水线
  
- **AnalysisPipeline** - 分析流水线
  - 定义可组合的分析步骤序列
  - 支持自定义步骤和流程
  - 内置步骤：CVE分析 → Sink分析 → Source分析 → CodeQL生成 → 执行 → 路径选择
  
- **AnalysisContext** - 分析上下文
  - 存储案例信息、语言、情报数据
  - 管理步骤间的数据传递
  - 支持事件回调（SSE流式输出）

#### 2. Agent层

- **CVEAnalysisAgent** - CVE分析Agent
  - 提取漏洞类型（SQL注入、XSS、RCE等）
  - 分析技术细节和攻击向量
  - 生成Sink/Source分析的基础上下文
  
- **UnifiedSourceAnalysisAgent** - Source分析Agent
  - 识别污点源（用户输入、HTTP请求等）
  - 支持 Java、C/C++、Python
  - 生成Source点的CodeQL谓词
  
- **UnifiedSinkPathAgent** - Sink分析Agent
  - 识别危险函数（exec、eval、system等）
  - 分析数据流汇聚点
  - 生成Sink点的CodeQL谓词
  
- **CodeQL Generation Agents** - CodeQL生成Agent组
  - `codeql_gen_agent`: 查询生成
  - `codeql_error_agent`: 编译错误诊断
  - `codeql_fix_inplace_agent`: 自动修复

#### 3. 服务层

- **LLMService** - LLM服务
  - 统一的LLM接口
  - 支持多种模型提供商
  - 连接复用和自动重试
  
- **LSPService** - LSP服务
  - CodeQL语法检查
  - 定义查找和代码补全
  - 进程管理和资源清理
  
- **PathSelectionService** - 路径选择服务
  - 基于LLM的智能路径筛选
  - 多维验证（完整性、正确性、置信度）
  - 支持路径聚类和去重
  
- **KnowledgeBaseService** - 知识库服务
  - 标签驱动的上下文检索
  - 模板、辅助谓词、案例管理
  - 支持多语言扩展

## 🚀 快速开始

### 系统要求

- **Python**: 3.13+ （推荐使用 uv 管理依赖）
- **CodeQL CLI**: 最新版本（用于查询执行）
- **Node.js**: v18+ （用于 MCP 工具）
- **操作系统**: Windows、Linux、macOS

### 安装步骤

#### 1. 克隆项目

```bash
git clone https://github.com/Fruit-Guardians/PureAutoCodeql.git
cd PureAutoCodeql
```

#### 2. 安装 Python 依赖

```bash
# 使用 uv（推荐，速度更快）
uv sync

# 或使用 pip
pip install -e .
```

#### 3. 安装 CodeQL CLI

```bash
# Linux/Mac
wget https://github.com/github/codeql-cli-binaries/releases/latest/download/codeql-linux64.zip
unzip codeql-linux64.zip
export PATH="$PATH:/path/to/codeql"

# Windows
# 下载并解压到本地，添加到 PATH 环境变量
```

#### 4. 构建 MCP 工具（可选，用于高级搜索）

```bash
# Windows
build_mcp.bat

# Linux/Mac
chmod +x build_mcp.sh
./build_mcp.sh
```

**注意**：MCP 工具需要 Node.js (v18+) 和 npm

### 第一次运行

#### 1. 配置 API Key

```bash
# 复制配置模板
cp config/keys.example.toml config/keys.toml

# 编辑配置文件，添加 API Key
# 至少配置一个模型提供商
```

```toml
# config/keys.toml
[builtin_keys]
deepseek = "sk-your-api-key"  # 推荐：性价比高
```

#### 2. 运行示例分析

```bash
# 分析单个CVE案例
python Analyze.py --case CVE-2021-21985

# 显示AI思考过程（推荐用于学习）
python Analyze.py --case CVE-2021-21985 --stream

# 使用不同的模型提供商
python Analyze.py --case CVE-2021-21985 --provider siliconflow
```

### 常用命令

#### 案例管理

```bash
# 列出所有可用案例
python Analyze.py --list

# 验证案例结构
python Analyze.py --validate CVE-2021-21985

# 强制刷新情报数据（从NVD/GHSA重新获取）
python Analyze.py --case CVE-2021-21985 --refresh-intel
```

#### 模型配置

```bash
# 查看所有可用的模型提供商
python Analyze.py --list-providers

# 使用特定模型提供商
python Analyze.py --case CVE-XXX --provider deepseek
python Analyze.py --case CVE-XXX --provider siliconflow
python Analyze.py --case CVE-XXX --provider zhipu

# 自定义模型（覆盖默认配置）
python Analyze.py --case CVE-XXX \
  --provider deepseek \
  --think-model deepseek-reasoner \
  --chat-model deepseek-chat
```

#### 从 MD 文件生成

```bash
# 从漏洞描述生成 CodeQL 查询
python Analyze.py --md-file vulnerability.md \
  --database-path /path/to/codeql-db \
  --language java

# 从 MD 文件生成 Source 点分析报告
python Analyze.py --md-file vuln.md \
  --src-path /path/to/source \
  --language python \
  --output report.md
```

### 基本使用示例

#### 新架构API (推荐)

```python
import asyncio
from core.orchestrator import AnalysisOrchestrator

async def analyze_vulnerability():
    # 创建分析编排器
    orchestrator = AnalysisOrchestrator()

    # 执行分析
    result = await orchestrator.analyze_case("CVE-2021-21985")

    # 检查结果
    if result.success:
        print(f"✅ 分析完成: {result.case_id}")
        print(f"⏱️  执行时间: {result.execution_time:.2f}秒")
        print(f"🔍 检测语言: {result.language}")
    else:
        print(f"❌ 分析失败: {result.error_message}")

asyncio.run(analyze_vulnerability())
```

#### 命令行接口

```bash
# 基本用法
python Analyze.py --case CVE-2021-21985

# 完整参数示例
python Analyze.py \
    --case CVE-2021-21985 \
    --stream \
    --refresh-intel \
    --output custom_output.md \
    --provider deepseek
```

### 批量分析

```python
import asyncio
from core.orchestrator import AnalysisOrchestrator

async def batch_analysis():
    orchestrator = AnalysisOrchestrator()
    case_ids = ["CVE-2021-21985", "CVE-2021-44228", "CVE-2021-45046"]

    for case_id in case_ids:
        print(f"\n🔍 分析案例: {case_id}")
        result = await orchestrator.analyze_case(case_id)

        if result.success:
            print(f"✅ {case_id} 分析成功")
        else:
            print(f"❌ {case_id} 分析失败")

asyncio.run(batch_analysis())
```

## 🔧 LLM 配置

本项目使用全新的 **统一配置系统** (`config.py`)，支持多种服务商、自定义服务商和美观的信息展示。

### 🎯 新配置系统亮点 (v2.0)

- ✅ **统一注册机制** - `ProviderRegistry` 管理所有服务商
- ✅ **自定义服务商** - 支持 YAML 配置文件添加自定义服务商
- ✅ **美观展示** - 使用 Rich 库提供专业的表格和面板展示
- ✅ **命令行工具** - 完整的 CLI 工具 (`python config.py`)
- ✅ **向后兼容** - 现有代码无需修改即可使用
- ✅ **自动切换** - 服务商自动故障转移功能

### 支持的服务商

| 服务商 | 推理模型 | 对话模型 | 状态 | 默认 |
|--------|----------|----------|------|------|
| **DeepSeek** | deepseek-reasoner | deepseek-chat | ✅ 推荐 | ✅ 默认 |
| **SiliconFlow** | deepseek-ai/DeepSeek-R1 | Pro/deepseek-ai/DeepSeek-V3.2-Exp | ✅ 稳定 | - |
| **智谱GLM** | glm-4.6 | glm-4.6 | ✅ 可用 | - |

### 🚀 快捷切换模型提供商

#### 方式一：命令行参数（推荐）

无需修改环境变量，直接在命令行指定提供商：

```bash
# 使用 DeepSeek（默认）
python Analyze.py --case CVE-2021-21985 --provider deepseek

# 使用 SiliconFlow
python Analyze.py --case CVE-2021-21985 --provider siliconflow

# 使用智谱GLM
python Analyze.py --case CVE-2021-21985 --provider zhipu

# 查看所有可用提供商及其状态
python Analyze.py --list-providers
```

#### 方式二：环境变量配置

```bash
# 选择服务商 (可选，默认: deepseek)
export LLM_PROVIDER=deepseek

# DeepSeek 配置
export DEEPSEEK_API_KEY=your_api_key_here

# SiliconFlow 配置
export SILICONFLOW_API_KEY=your_api_key_here

# 智谱配置
export ZHIPU_API_KEY=your_api_key_here

# 通用配置 (所有服务商都支持)
export OPENAI_API_KEY=your_fallback_key
export OPENAI_BASE_URL=your_custom_endpoint
```

**注意**：命令行参数 `--provider` 会覆盖环境变量 `LLM_PROVIDER` 的设置。

### 模型配置

```bash
# 自定义模型 (可选)
export THINK_MODEL=your_custom_reasoning_model
export CHAT_MODEL=your_custom_chat_model
```

### 🔍 配置管理工具

#### 查看服务商状态

```bash
# 查看所有服务商状态（表格形式）
python config.py list

# 仅显示可用的服务商
python config.py list --available-only

# 查看单个服务商详情
python config.py show siliconflow

# 测试服务商连接
python config.py test deepseek

# 运行配置向导
python config.py setup
```

#### 在分析时检查

```bash
# 在 Analyze.py 中查看服务商状态
python Analyze.py --list-providers
```

### 📦 自定义服务商

支持添加自定义 LLM 服务商（如本地 Ollama、自定义 OpenAI 代理等）：

1. **创建配置文件** `my_providers.yaml`:

```yaml
custom_providers:
  - name: "my_openai"
    display_name: "我的 OpenAI 代理"
    base_url: "https://my-proxy.com/v1"
    default_think_model: "gpt-4"
    default_chat_model: "gpt-3.5-turbo"
    env_keys:
      - "MY_OPENAI_KEY"
    env_base_urls:
      - "MY_OPENAI_BASE_URL"
    description: "通过代理访问 OpenAI API"
```

2. **注册服务商**:

```bash
python config.py register --file my_providers.yaml
```

3. **使用自定义服务商**:

```bash
python Analyze.py --case CVE-2021-21985 --provider my_openai
```

详见 [`providers.example.yaml`](providers.example.yaml) 和 [配置系统指南](docs/config_system_guide.md)。

### 📚 配置文档

- 📖 [配置系统完整指南](docs/config_system_guide.md) - 详细的使用文档
- 🔄 [配置系统迁移指南](docs/config_migration_guide.md) - 从旧版本迁移
- 💡 [配置示例](examples/config_demo.py) - 代码示例
- 📝 [服务商配置模板](providers.example.yaml) - YAML 配置模板

## 🧪 测试和验证

### 验证分析功能

```bash
# 运行示例分析
uv run python Analyze.py --case CVE-2021-21985 --stream

# 验证案例结构
uv run python Analyze.py --validate CVE-2021-21985

# 查看可用案例
uv run python Analyze.py --list
```

## 📁 额外输入文件支持 🆕

现在可以在案例的 `inputs/` 目录中添加**任意额外文件**来增强分析！

```bash
# 文件名随意，格式随意
echo "系统使用 Spring Boot 框架..." > projects/CVE-XXX/inputs/架构说明.md
echo '{"version": "2.3.4"}' > projects/CVE-XXX/inputs/版本.json
echo "关键发现: ..." > projects/CVE-XXX/inputs/分析笔记.txt
```

系统会自动：
- 🔍 **发现文件** - 自动扫描 inputs 目录
- 📋 **读取内容** - 作为补充上下文
- 🤖 **提供给 Agent** - 用于更精准的分析

**无需遵循特定命名规范**，任意文件名和格式都支持！

**详细文档**：[额外输入文件功能说明](docs/extra_input_files_simple.md)

## 🔁 Flow Break 检测与自动补边 🆕

当生成的 CodeQL 查询执行成功但返回空结果时，系统会在进入传统“空结果重试”逻辑之前自动执行一次**断流检测 → 补边生成 → 带补边重跑**流程：

- 🎯 **断流检测**：复用上一轮的 Source/Sink/isAdditionalFlowStep，构造固定 Source/Sink + ANY 交集查询，定位潜在断流点
- 🧠 **补边生成**：依据断流节点位置和代码上下文，生成带限额的 `isAdditionalFlowStep` 子句，自动去重并记录来源
- 🔁 **安全合并**：将新增子句合并回原查询，保留既有逻辑与注释，允许多轮迭代（默认最多 3 轮）
- 📊 **遥测日志**：记录检测轮次、候选节点数、补边子句、耗时等信息，便于调试与后续优化

可通过环境变量调整行为（均有合理默认值）：

| 变量 | 默认值 | 说明 |
|------|-------|------|
| `ENABLE_FLOW_BREAK_DETECTION` | `true` | 是否启用断流检测流程 |
| `MAX_FLOW_PATCH_ROUNDS` | `3` | 单次查询允许的补边迭代轮数 |
| `MAX_FLOW_PATCH_CLAUSES_PER_ROUND` | `5` | 每轮最多新增的补边子句 |
| `MAX_FLOW_PATCH_TOTAL_CLAUSES` | `12` | 整个流程允许新增的补边子句总数 |
| `FLOW_BREAK_SOFT_LIMIT` / `FLOW_BREAK_CANDIDATE_CAP` | `200` / `50` | 断流候选节点的软上限与截断上限 |

> 📌 **注意**：当断流检测失败或超过配置上限时，系统会自动降级回原有的空结果重试机制，保持向后兼容。

## 📖 详细文档

| 文档 | 描述 |
|------|------|
| [📋 架构文档](openspec/docs/architecture.md) | 详细的架构设计和组件说明 |
| [🔄 迁移指南](openspec/docs/migration_guide.md) | 从旧版本到新版本的迁移指南 |
| [📚 API参考](openspec/docs/api_reference.md) | 完整的API接口文档 |
| [📝 项目规范](openspec/project.md) | 项目开发规范和流程 |
| [📋 OpenSpec](openspec/README.md) | 项目规范和变更管理 |
| [📂 额外输入文件](docs/extra_input_files_simple.md) | 额外输入文件功能使用指南 🆕 |
| [🔁 Flow Break 检测](docs/flow_break_detection.md) | 断流检测与自动补边的开发/运维指南 🆕 |

## 🔄 代码质量优化

### 最新优化 (2025-01)

- ✅ **Flow Break 自动补边**（2025-11）- 在空结果重试前自动执行断流检测与补边生成，可通过环境变量灵活配置
- ✅ **模型提供商快捷切换** - 支持命令行参数快速切换模型提供商，无需修改环境变量
- ✅ **统一日志系统** - 创建了统一的日志配置模块，区分用户交互和系统日志
- ✅ **信息收集模块优化** - 修复了 NVD 信息获取中的代码重复问题
- ✅ **核心流水线优化** - 修复了格式化字符串潜在错误，清理了未使用的导入
- ✅ **工具模块优化** - 修复了硬编码语言名称问题，支持多语言动态显示
- ✅ **服务模块优化** - 修复了默认语言硬编码问题，优化了代码结构

### 代码质量特性

- 🧹 **代码清理** - 移除了未使用的导入和重复代码
- 🔧 **错误修复** - 修复了潜在的运行时错误
- 🌐 **多语言支持** - 改进了多语言场景下的处理逻辑
- 📝 **代码规范** - 统一了代码风格和最佳实践
- 🤖 **模型切换** - 支持运行时快速切换模型提供商，提升使用灵活性
- 📊 **日志系统** - 统一的日志配置，更好的调试和监控能力

## 🎯 使用场景

### 1. 漏洞研究
```python
# 研究特定CVE漏洞
orchestrator = AnalysisOrchestrator()
result = await orchestrator.analyze_case("CVE-2021-21985")
```

### 2. 代码审计
```python
# 对项目进行安全审计
case_id = "your-custom-case-id"
result = await orchestrator.analyze_case(case_id)
```

### 3. 批量分析
```python
# 批量分析多个漏洞
case_ids = load_case_ids_from_file("vulnerabilities.txt")
for case_id in case_ids:
    result = await orchestrator.analyze_case(case_id)
```

### 4. 自定义分析流水线
```python
# 创建自定义分析步骤
from core.pipeline import AnalysisPipeline, CVEAnalysisStep

pipeline = AnalysisPipeline([CVEAnalysisStep()])
result = await pipeline.execute(context)
```

## 🛠️ 开发指南

### 添加新的分析步骤

```python
from core.pipeline import AnalysisStep

class CustomAnalysisStep(AnalysisStep):
    def __init__(self):
        super().__init__("custom_analysis")

    async def execute(self, context: AnalysisContext) -> Any:
        # 自定义分析逻辑
        return result

# 添加到流水线
pipeline = AnalysisPipeline([CustomAnalysisStep()])
```

### 扩展语言支持

1. 在 `LanguageDetector` 中添加语言检测逻辑
2. 创建对应的分析Agent
3. 更新流水线配置
4. 添加测试用例

### 添加新的LLM服务商

1. 在 `config.py` 中添加服务商配置
2. 更新默认模型映射
3. 添加环境变量支持
4. 测试连接和功能

## 📊 性能特性

### 🔥 **性能优化**
- **连接复用** - LLM连接在多个Agent间复用，减少初始化开销
- **异步执行** - 所有I/O操作异步化，支持并发处理
- **智能缓存** - 情报数据和检测结果缓存，避免重复计算
- **资源管理** - 自动管理LSP服务进程，防止资源泄露

### 📈 **扩展性**
- **插件化架构** - 支持自定义分析步骤和服务
- **配置驱动** - 通过配置文件灵活调整分析流程
- **多语言支持** - 易于扩展新的编程语言支持
- **分布式就绪** - 架构支持未来的分布式扩展

## 🤝 贡献指南

### 开发环境设置

```bash
# 克隆项目
git clone https://github.com/Fruit-Guardians/PureAutoCodeql.git
cd PureAutoCodeql

# 安装依赖
uv sync

# 运行测试
python test_new_architecture.py
```

### 提交规范

- 🐛 Bug修复: `fix: 修复CVE分析的内存泄露问题`
- ✨ 新功能: `feat: 添加对Rust语言的支持`
- 📚 文档: `docs: 更新API文档`
- 🎨 代码风格: `style: 格式化代码`
- ♻️ 重构: `refactor: 重构服务层架构`

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。

## 🙏 致谢

- [CodeQL](https://codeql.github.com/) - 强大的代码分析平台
- [LangChain](https://python.langchain.com/) - LLM应用开发框架
- 所有贡献者和用户的支持

---

<div align="center">

**⭐ 如果这个项目对您有帮助，请给我们一个Star！**

[📖 文档](docs/) · [🐛 报告问题](issues) · [💡 功能建议](issues) · [🤝 贡献代码](CONTRIBUTING.md)

</div>