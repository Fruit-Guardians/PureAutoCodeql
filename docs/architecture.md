# PureAutoCodeQL 架构文档

## 📋 概述

PureAutoCodeQL 是一个基于多Agent的自动化漏洞分析工具，使用 CodeQL 进行安全漏洞检测。本文档描述了重构后的项目架构设计。

## 🏗️ 整体架构

### 分层架构设计

```
PureAutoCodeql/
├── core/                        # 🎯 核心层 - 业务逻辑编排
│   ├── context.py              # 分析上下文和配置管理
│   ├── pipeline.py              # 分析流水线和步骤定义
│   └── orchestrator.py          # 分析编排器
├── services/                    # 🔧 服务层 - 基础服务
│   ├── lsp_service.py           # CodeQL LSP语法检查服务
│   ├── llm_service.py           # LLM和多Agent服务
│   └── language_detector.py     # 编程语言检测服务
├── agents/                      # 🤖 Agent层 - 专业分析Agent
│   ├── cve_analysis_agent.py    # CVE分析Agent
│   ├── unified_sink_path_agent.py # Sink路径分析Agent
│   ├── unified_source_analysis_agent.py # Source分析Agent
│   └── codeql_gen_agents/       # CodeQL生成相关Agent
├── tools/                       # 🛠️ 工具层 - 具体工具实现
│   ├── codeql_compose.py        # CodeQL查询组合工具
│   └── lsp_codeql.py            # LSP服务器实现
├── utils/                       # 📦 工具函数层 - 通用工具
│   ├── case.py                  # 案例管理工具
│   ├── intel.py                 # 情报收集工具
│   ├── io.py                    # 输入输出工具
│   └── codeql.py                # CodeQL相关工具
├── prompts/                     # 📝 提示词层 - 提示词管理
├── config.py                    # ⚙️ 配置管理
├── Analyze.py                   # 🚀 原始入口文件（已备份）
├── Analyze_new.py               # 🚀 新架构入口文件
└── test_new_architecture.py     # 🧪 架构测试脚本
```

### 架构原则

1. **单一职责原则** - 每个模块只负责一个特定功能
2. **依赖倒置原则** - 高层模块不依赖低层模块，都依赖抽象
3. **开闭原则** - 对扩展开放，对修改关闭
4. **接口隔离原则** - 客户端不应依赖它不需要的接口
5. **向后兼容原则** - 保持原有API的兼容性

## 🎯 核心层 (Core Layer)

### AnalysisOrchestrator - 分析编排器

**职责**: 协调整个分析流程，管理各个组件的执行

```python
class AnalysisOrchestrator:
    async def analyze_case(self, case_id: str) -> AnalysisResult
```

**特点**:
- 统一的分析入口点
- 自动化的流程编排
- 错误处理和状态管理
- 结果汇总和报告生成

### AnalysisPipeline - 分析流水线

**职责**: 定义和执行分析步骤序列

```python
class AnalysisPipeline:
    def create_default_pipeline() -> "AnalysisPipeline"
    async def execute(self, context: AnalysisContext) -> AnalysisResult
```

**默认分析步骤**:
1. **CVE分析** - 解析CVE信息，提取漏洞细节
2. **Sink路径分析** - 识别潜在的危险函数调用点
3. **Source分析** - 追踪数据流的源头
4. **CodeQL生成** - 生成CodeQL查询并验证语法

### AnalysisContext - 分析上下文

**职责**: 管理分析过程中的所有数据和状态

```python
@dataclass
class AnalysisContext:
    case_id: str
    case_paths: CasePaths
    cve_assets: CveAssets
    language: str
    intel_bundle: Optional[IntelBundle] = None
    show_thinking: bool = False
```

## 🔧 服务层 (Services Layer)

### CodeQLLSPService - LSP语法检查服务

**职责**: 提供CodeQL语言服务器协议的封装

```python
class CodeQLLSPService:
    def start() -> bool
    def check_syntax(codeql_code: str) -> Dict[str, Any]
    def stop()
```

**特点**:
- 自动启动和停止LSP服务
- 语法检查和错误报告
- 超时处理和进程管理

### MultiAgentAnalyzer - 多Agent分析器

**职责**: 管理LLM连接和Agent执行

```python
class MultiAgentAnalyzer:
    async def initialize() -> None
    async def run_agent(prompt: str, show_thinking: bool = True) -> AgentResult
```

**特点**:
- LLM连接复用
- Agent执行状态跟踪
- 实时思考过程显示

### LanguageDetector - 语言检测器

**职责**: 自动检测项目使用的编程语言

```python
class LanguageDetector:
    def detect_language(case_paths: CasePaths) -> str
    def get_supported_languages() -> List[str]
    def is_supported_language(language: str) -> bool
```

**检测策略**:
1. 数据库目录中的语言子目录
2. 源码文件扩展名统计
3. 支持Java、Python、C/C++

## 🤖 Agent层 (Agents Layer)

### CVEAnalysisAgent - CVE分析Agent

**职责**: 解析CVE JSON文件，提取漏洞信息

### UnifiedSinkPathAgent - 统一Sink路径分析Agent

**职责**: 识别和分析潜在的漏洞sink点

### UnifiedSourceAnalysisAgent - 统一Source分析Agent

**职责**: 追踪和分析数据流源头

## 📊 数据流图

```
┌─────────────────┐
│   案例输入       │
│ (case_id, args) │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│ AnalysisOrchestrator │
│   (创建上下文)    │
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│ AnalysisPipeline │
│   (执行步骤)     │
└─────────┬───────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
┌─────────┐ ┌─────────┐
│ Agent 1 │ │ Agent 2 │ → ... → │ Agent N │
└─────┬───┘ └─────┬───┘          └─────┬───┘
      │           │                    │
      ▼           ▼                    ▼
┌─────────────────────────────────────────┐
│           AnalysisResult                │
│         (结果汇总和输出)                  │
└─────────────────────────────────────────┘
```

## 🔄 执行流程

1. **初始化阶段**
   - 解析命令行参数
   - 创建AnalysisOrchestrator
   - 初始化服务和配置

2. **准备阶段**
   - 解析案例路径 (CasePaths)
   - 发现CVE资产 (CveAssets)
   - 收集情报数据 (IntelBundle)
   - 检测编程语言

3. **分析阶段**
   - 创建AnalysisContext
   - 创建AnalysisPipeline
   - 按顺序执行各个AnalysisStep

4. **完成阶段**
   - 生成AnalysisResult
   - 保存分析报告
   - 显示执行摘要

## 🧪 测试架构

项目包含完整的测试脚本:

```bash
python test_new_architecture.py
```

测试内容:
- 模块导入测试
- 服务创建测试
- 编排器功能测试
- 异步组件测试

## 📈 性能优化

### 连接复用
- LLM连接在多个Agent间复用
- MCP客户端统一管理
- 避免重复初始化

### 异步执行
- 所有I/O操作异步化
- 并行处理独立任务
- 非阻塞的用户界面

### 缓存机制
- 情报数据缓存
- 语言检测结果缓存
- 配置缓存

## 🔧 扩展指南

### 添加新的分析步骤

```python
class CustomAnalysisStep(AnalysisStep):
    def __init__(self):
        super().__init__("custom_analysis")

    async def execute(self, context: AnalysisContext) -> Any:
        # 自定义分析逻辑
        return result

# 添加到流水线
pipeline = AnalysisPipeline([CustomAnalysisStep()])
```

### 添加新的服务

```python
class CustomService:
    def __init__(self, config):
        self.config = config

    async def process(self, data):
        # 服务逻辑
        return processed_data

# 在编排器中使用
orchestrator.custom_service = CustomService(config)
```

### 支持新的编程语言

1. 在`LanguageDetector`中添加语言检测逻辑
2. 创建对应的分析Agent
3. 更新流水线配置
4. 添加测试用例

## 🚀 未来规划

### 短期目标
- [ ] 完善错误处理和日志记录
- [ ] 添加配置文件支持
- [ ] 优化性能和内存使用
- [ ] 增加更多测试用例

### 长期目标
- [ ] 支持Web API接口
- [ ] 分布式分析支持
- [ ] 可视化分析结果
- [ ] 机器学习优化

---

*本文档随项目更新而更新，最后更新时间: 2025-11-06*