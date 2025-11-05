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
├── 🚀 Analyze_new.py               # 新架构入口文件 (推荐)
├── 🚀 Analyze.py                   # 原始入口文件 (已备份)
└── 🧪 test_new_architecture.py     # 架构测试脚本
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
```

### 快速运行

#### 🎯 **推荐方式：使用新架构**

```bash
# 使用新架构入口文件
uv run python Analyze_new.py --case CVE-2021-21985
```

#### 🔄 **兼容方式：使用原有接口**

```bash
# 仍然可以使用原有命令
uv run python Analyze.py --case CVE-2021-21985
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

#### 兼容API (无需修改现有代码)

```python
import asyncio
from Analyze_new import run_case_analysis  # 完全兼容原有接口

async def main():
    await run_case_analysis(
        case_id="CVE-2021-21985",
        refresh_intel=False,
        stream=True  # 显示AI思考过程
    )

asyncio.run(main())
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

本项目使用集中化的 LLM 配置系统（`config.py`），支持多种服务商：

### 支持的服务商

| 服务商 | 推理模型 | 对话模型 | 状态 |
|--------|----------|----------|------|
| **DeepSeek** | deepseek-reasoner | deepseek-chat | ✅ 推荐 |
| **SiliconFlow** | deepseek-ai/DeepSeek-R1 | Pro/deepseek-ai/DeepSeek-V3.2-Exp | ✅ 稳定 |
| **智谱GLM** | glm-4.6 | glm-4.6 | ✅ 可用 |

### 环境变量配置

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

### 模型配置

```bash
# 自定义模型 (可选)
export THINK_MODEL=your_custom_reasoning_model
export CHAT_MODEL=your_custom_chat_model
```

## 🧪 测试和验证

### 运行架构测试

```bash
# 测试新架构功能
python test_new_architecture.py
```

测试内容：
- ✅ 模块导入测试
- ✅ 服务创建测试
- ✅ 编排器功能测试
- ✅ 异步组件测试

### 验证分析功能

```bash
# 运行示例分析
uv run python Analyze_new.py --case CVE-2021-21985 --stream
```

## 📖 详细文档

| 文档 | 描述 |
|------|------|
| [📋 架构文档](docs/architecture.md) | 详细的架构设计和组件说明 |
| [🔄 迁移指南](docs/migration_guide.md) | 从旧版本到新版本的迁移指南 |
| [📚 API参考](docs/api_reference.md) | 完整的API接口文档 |
| [⚙️ 配置指南](docs/configuration.md) | 详细的配置说明 |

## 🔄 迁移到新架构

### 无需修改现有代码

现有代码可以直接使用新的入口文件：

```python
# 只需要改变导入
- from Analyze import run_case_analysis
+ from Analyze_new import run_case_analysis

# 其他代码保持不变
await run_case_analysis("CVE-2021-21985")
```

### 渐进式升级

1. **第一阶段**: 使用 `Analyze_new.py` 替代 `Analyze.py`
2. **第二阶段**: 逐步使用新的服务类
3. **第三阶段**: 使用 `AnalysisOrchestrator` 重构业务逻辑

详细迁移指南请参考 [🔄 迁移指南](docs/migration_guide.md)

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