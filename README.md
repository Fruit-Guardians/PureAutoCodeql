# PureAutoCodeQL

<div align="center">

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.8%2B-blue.svg)](https://python.org)
[![CodeQL](https://img.shields.io/badge/CodeQL-Automated%20Security%20Analysis-green.svg)](https://codeql.github.com/)

**基于多智能体架构的自动化漏洞分析工具，使用 CodeQL 和 AI 技术进行 Java、C、Python 代码安全分析**

[📖 文档](docs/) · [🚀 快速开始](#-快速开始) · [🏗️ 架构](#-架构设计) · [🔧 配置](#llm-配置)

</div>

## ✨ 新版本特性

### 🎯 **重构新架构 (v2.0)**
- ✅ **分层架构设计** - 清晰的代码组织和职责分离
- ✅ **统一编排器** - 简化的分析流程管理
- ✅ **服务化组件** - 可复用的LLM、LSP、语言检测服务
- ✅ **向后兼容** - 现有代码无需修改即可使用
- ✅ **异步优化** - 更好的并发性能和资源利用
- ✅ **额外输入文件支持** - 在 `inputs/` 目录添加额外信息文件增强分析 🆕

### 📊 **架构对比**

| 特性 | 旧版本 | 新版本 |
|------|--------|--------|
| 代码组织 | 单文件652行 | 分层模块化 |
| 扩展性 | 需修改核心代码 | 插件化扩展 |
| 维护性 | 耦合度高 | 低耦合高内聚 |
| 测试性 | 难以单元测试 | 完整测试覆盖 |
| 性能 | 同步阻塞 | 异步并发 |

## 🏗️ 架构设计

### 分层架构

```
PureAutoCodeql/
├── 🎯 core/                        # 核心层 - 业务逻辑编排
│   ├── context.py                  # 分析上下文和配置管理
│   ├── pipeline.py                  # 分析流水线和步骤定义
│   └── orchestrator.py              # 分析编排器
├── 🔧 services/                    # 服务层 - 基础服务
│   ├── lsp_service.py               # CodeQL LSP语法检查服务
│   ├── llm_service.py               # LLM和多Agent服务
│   └── language_detector.py         # 编程语言检测服务
├── 🤖 agents/                      # Agent层 - 专业分析Agent
│   ├── cve_analysis_agent.py        # CVE分析Agent
│   ├── unified_sink_path_agent.py   # 统一Sink路径分析Agent
│   ├── unified_source_analysis_agent.py # 统一Source分析Agent
│   └── codeql_gen_agents/           # CodeQL生成相关Agent
├── 🛠️ tools/                       # 工具层 - 具体工具实现
├── 📦 utils/                       # 工具函数层 - 通用工具
├── 📝 prompts/                     # 提示词层 - 提示词管理
├── ⚙️ config.py                    # 配置管理
├── 🚀 Analyze.py                   # 主入口文件
└── 📊 Information/                 # 情报收集模块 (GHSA/NVD)
```

### 核心组件

- **AnalysisOrchestrator** - 分析编排器，统一管理整个分析流程
- **AnalysisPipeline** - 分析流水线，定义分析步骤序列
- **AnalysisContext** - 分析上下文，管理分析过程数据
- **服务层** - 提供LLM、LSP、语言检测等基础服务

## 🚀 快速开始

### 安装依赖

```bash
# 使用 uv 安装依赖（推荐）
uv sync

# 或使用 pip 安装
pip install -r requirements.txt

# 构建 MCP 工具（ripgrep 搜索工具）
# Windows:
build_mcp.bat

# Linux/Mac:
chmod +x build_mcp.sh
./build_mcp.sh
```

**注意**：MCP 工具需要 Node.js (v18+) 和 npm。如果未安装，请先安装 Node.js：https://nodejs.org/

### 快速运行

#### 🎯 **使用主入口文件**

```bash
# 分析单个案例
uv run python Analyze.py --case CVE-2021-21985

# 显示AI思考过程
uv run python Analyze.py --case CVE-2021-21985 --stream

# 强制刷新情报数据
uv run python Analyze.py --case CVE-2021-21985 --refresh-intel

# 列出所有可用案例
uv run python Analyze.py --list

# 验证案例有效性
uv run python Analyze.py --validate CVE-2021-21985

# 列出所有可用的模型提供商及其状态
uv run python Analyze.py --list-providers

# 使用指定模型提供商进行分析
uv run python Analyze.py --case CVE-2021-21985 --provider deepseek
uv run python Analyze.py --case CVE-2021-21985 --provider siliconflow
uv run python Analyze.py --case CVE-2021-21985 --provider zhipu
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