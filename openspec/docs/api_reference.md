# API 参考文档

## 📋 概述

PureAutoCodeQL 提供了分层的API接口，从简单的函数调用到复杂的自定义流水线。本文档详细描述了所有可用的API。

## 🚀 快速开始API

### AnalysisOrchestrator - 主要API

这是推荐使用的主要API，提供了完整的分析功能。

```python
from core.orchestrator import AnalysisOrchestrator

# 创建编排器
orchestrator = AnalysisOrchestrator()

# 执行分析
result = await orchestrator.analyze_case("CVE-2021-21985")

# 检查结果
if result.success:
    print(f"分析成功: {result.case_id}")
else:
    print(f"分析失败: {result.error_message}")
```

#### 类定义

```python
class AnalysisOrchestrator:
    def __init__(self, config: Optional[AnalysisConfig] = None)
    async def analyze_case(self, case_id: str) -> AnalysisResult
    @classmethod
    def create_from_args(cls, args) -> "AnalysisOrchestrator"
```

#### 参数说明

- `config` (可选): 分析配置对象
- `case_id`: 案例ID，如 "CVE-2021-21985"

#### 返回值

返回 `AnalysisResult` 对象，包含分析结果。

---

## 🎯 核心层API

### AnalysisConfig - 配置管理

```python
from core.context import AnalysisConfig

config = AnalysisConfig(
    show_thinking=True,        # 显示AI思考过程
    refresh_intel=False,       # 不刷新情报缓存
    output_file="output.md"    # 输出文件名
)
```

#### 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `llm_config` | Optional[Any] | None | LLM配置对象 |
| `lsp_enabled` | bool | True | 是否启用LSP服务 |
| `lsp_pack_root` | Optional[str] | None | LSP包根目录 |
| `output_file` | str | "output.md" | 输出文件名 |
| `show_thinking` | bool | False | 是否显示AI思考过程 |
| `refresh_intel` | bool | False | 是否强制刷新情报数据 |

### AnalysisContext - 分析上下文

```python
from core.context import AnalysisContext, CasePaths, CveAssets

context = AnalysisContext(
    case_id="CVE-2021-21985",
    case_paths=case_paths,
    cve_assets=cve_assets,
    language="java",
    intel_bundle=intel_bundle,
    show_thinking=True
)

# 添加和获取结果
context.add_result("cve_analysis", cve_result)
cve_result = context.get_result("cve_analysis")
```

#### 方法

- `add_result(step_name: str, result: Any) -> None`: 添加分析步骤结果
- `get_result(step_name: str) -> Any`: 获取指定步骤结果
- `has_result(step_name: str) -> bool`: 检查是否有指定步骤结果

### AnalysisResult - 分析结果

```python
from core.context import AnalysisResult

result = AnalysisResult(
    case_id="CVE-2021-21985",
    language="java",
    success=True,
    execution_time=120.5
)

# 检查完整性
if result.is_complete():
    print("所有分析步骤都成功完成")

# 获取摘要
summary = result.get_summary()
print(summary)
```

#### 属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `case_id` | str | 案例ID |
| `language` | str | 编程语言 |
| `cve_result` | Optional[AgentResult] | CVE分析结果 |
| `sink_result` | Optional[AgentResult] | Sink分析结果 |
| `source_result` | Optional[AgentResult] | Source分析结果 |
| `codeql_result` | Optional[AgentResult] | CodeQL生成结果 |
| `success` | bool | 分析是否成功 |
| `error_message` | Optional[str] | 错误信息 |
| `execution_time` | Optional[float] | 执行时间(秒) |

#### 方法

- `is_complete() -> bool`: 检查分析是否完整完成
- `get_summary() -> str`: 获取分析结果摘要

### AnalysisPipeline - 分析流水线

```python
from core.pipeline import AnalysisPipeline, CVEAnalysisStep

# 使用默认流水线
pipeline = AnalysisPipeline.create_default_pipeline()

# 或创建自定义流水线
custom_pipeline = AnalysisPipeline([
    CVEAnalysisStep(),
    # 可以添加其他步骤
])

# 执行流水线
result = await pipeline.execute(context)
```

#### 方法

- `create_default_pipeline() -> AnalysisPipeline`: 创建默认分析流水线
- `execute(context: AnalysisContext) -> AnalysisResult`: 执行分析流水线

---

## 🔧 服务层API

### MultiAgentAnalyzer - 多Agent分析器

```python
from services.llm_service import MultiAgentAnalyzer, AgentResult

# 创建分析器
analyzer = MultiAgentAnalyzer()

# 初始化 (自动调用，也可以手动调用)
await analyzer.initialize()

# 运行Agent
result = await analyzer.run_agent(
    prompt="分析这个CVE漏洞",
    show_thinking=True
)

if result.success:
    print(f"Agent回复: {result.content}")
else:
    print(f"Agent执行失败: {result.error}")
```

#### 类定义

```python
class MultiAgentAnalyzer:
    def __init__(self, config: LLMConfig = None)
    async def initialize() -> None
    async def run_agent(self, prompt: str, show_thinking: bool = True) -> AgentResult
```

### AgentResult - Agent执行结果

```python
@dataclass
class AgentResult:
    content: str           # Agent返回的内容
    success: bool          # 执行是否成功
    error: Optional[str]   # 错误信息
```

### CodeQLLSPService - LSP语法检查服务

```python
from services.lsp_service import CodeQLLSPService

# 创建LSP服务
lsp_service = CodeQLLSPService(pack_root="/path/to/packs")

# 启动服务
if lsp_service.start():
    print("LSP服务启动成功")

    # 检查语法
    codeql_code = """
    import java
    import semmle.code.java.dataflow.TaintTracking

    predicate isSource(DataFlow::Node source) {
        // CodeQL查询代码
    }
    """

    result = lsp_service.check_syntax(codeql_code)
    if "error" not in result:
        print("语法检查通过")
    else:
        print(f"语法错误: {result['error']}")

    # 停止服务
    lsp_service.stop()
else:
    print("LSP服务启动失败")
```

#### 方法

- `start() -> bool`: 启动LSP服务
- `check_syntax(codeql_code: str) -> Dict[str, Any]`: 检查CodeQL代码语法
- `stop()`: 停止LSP服务

### LanguageDetector - 语言检测器

```python
from services.language_detector import LanguageDetector
from utils.case import CasePaths

# 创建检测器
detector = LanguageDetector()

# 检测语言
try:
    language = detector.detect_language(case_paths)
    print(f"检测到语言: {language}")
except ValueError as e:
    print(f"语言检测失败: {e}")

# 获取支持的语言
supported = detector.get_supported_languages()
print(f"支持的语言: {supported}")

# 检查是否支持特定语言
if detector.is_supported_language("python"):
    print("支持Python语言分析")
```

#### 方法

- `detect_language(case_paths: CasePaths) -> str`: 检测编程语言
- `get_supported_languages() -> List[str]`: 获取支持的编程语言列表
- `is_supported_language(language: str) -> bool`: 检查是否支持指定语言

---

## 🤖 Agent层API

### CVEAnalysisAgent - CVE分析Agent

```python
from agents.cve_analysis_agent import CVEAnalysisAgent
from services.llm_service import MultiAgentAnalyzer

analyzer = MultiAgentAnalyzer()
await analyzer.initialize()

agent = CVEAnalysisAgent(analyzer)

# 分析CVE JSON文件
result = await agent.analyze_cve(
    json_path=Path("path/to/cve.json"),
    intel_prompt="相关的情报信息",
    show_thinking=True
)
```

#### 方法

- `analyze_cve(json_path: Path, intel_prompt: Optional[str] = None, show_thinking: bool = True) -> AgentResult`

### UnifiedSinkPathAgent - 统一Sink路径分析Agent

```python
from agents.unified_sink_path_agent import UnifiedSinkPathAgent

agent = UnifiedSinkPathAgent(analyzer, source_root="/path/to/source")

# 分析Sink路径
result = await agent.analyze_paths(
    language="java",
    cve_analysis="CVE分析结果",
    diff_path="path/to/diff",
    show_thinking=True
)
```

#### 方法

- `analyze_paths(language: str, cve_analysis: str, diff_path: str = "", show_thinking: bool = True) -> AgentResult`

### UnifiedSourceAnalysisAgent - 统一Source分析Agent

```python
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent

agent = UnifiedSourceAnalysisAgent(analyzer, source_root="/path/to/source", db_path="/path/to/db")

# 分析Source
result = await agent.analyze_sources(
    language="java",
    sink_analysis="Sink分析结果",
    show_thinking=True
)
```

#### 方法

- `analyze_sources(language: str, sink_analysis: str, show_thinking: bool = True) -> AgentResult`

---

## 📦 工具层API

### CodeQLComposeTool - CodeQL查询组合工具

```python
from tools.codeql_compose import CodeQLComposeTool
from services.llm_service import MultiAgentAnalyzer

analyzer = MultiAgentAnalyzer()
await analyzer.initialize()

tool = CodeQLComposeTool(
    analyzer=analyzer,
    database_path="/path/to/db",
    language="java"
)

# 生成CodeQL查询
requirement = "基于分析结果生成检测SQL注入的CodeQL查询"
result = await tool._arun(requirement, show_thinking=True)
print(result)
```

---

## 🔄 兼容性API

### Analyze_new - 向后兼容的入口文件

```python
# 完全兼容原有API
import asyncio
from Analyze_new import run_case_analysis, detect_language

async def main():
    # 运行案例分析 (完全兼容)
    await run_case_analysis(
        case_id="CVE-2021-21985",
        cve_id=None,
        refresh_intel=False,
        stream=True
    )

    # 语言检测 (完全兼容)
    from utils.case import resolve_case
    case_paths = resolve_case("CVE-2021-21985")
    language = detect_language(case_paths)

if __name__ == "__main__":
    asyncio.run(main())
```

#### 可用函数

- `run_case_analysis(case_id: str, cve_id: Optional[str] = None, refresh_intel: bool = False, stream: bool = False) -> None`
- `run_multi_agent_analysis(json_path: str = "", diff_path: str = "", source_root: str = "", db_path: str = "", intel_bundle: Optional[IntelBundle] = None, stream: bool = False, language: str = "java") -> None`
- `detect_language(case_paths: CasePaths) -> str`
- `run_java_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False) -> None`
- `run_python_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False) -> None`
- `run_c_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False) -> None`

---

## 🔍 错误处理

### 常见异常类型

```python
try:
    orchestrator = AnalysisOrchestrator()
    result = await orchestrator.analyze_case("INVALID_CASE_ID")
except ValueError as e:
    print(f"参数错误: {e}")
except FileNotFoundError as e:
    print(f"文件未找到: {e}")
except RuntimeError as e:
    print(f"运行时错误: {e}")
except Exception as e:
    print(f"未知错误: {e}")
```

### 检查结果状态

```python
# 检查分析结果
if result.success:
    print("分析成功")

    # 检查各个步骤结果
    if result.cve_result and result.cve_result.success:
        print("CVE分析成功")

    if result.is_complete():
        print("所有步骤都成功完成")
else:
    print(f"分析失败: {result.error_message}")
```

---

## 📝 使用示例

### 示例1: 简单案例分析

```python
import asyncio
from core.orchestrator import AnalysisOrchestrator

async def simple_analysis():
    orchestrator = AnalysisOrchestrator()
    result = await orchestrator.analyze_case("CVE-2021-21985")

    if result.success:
        print(f"✅ 分析完成: {result.case_id}")
        print(f"⏱️  执行时间: {result.execution_time:.2f}秒")
    else:
        print(f"❌ 分析失败: {result.error_message}")

asyncio.run(simple_analysis())
```

### 示例2: 自定义配置分析

```python
import asyncio
from core.orchestrator import AnalysisOrchestrator
from core.context import AnalysisConfig

async def custom_analysis():
    config = AnalysisConfig(
        show_thinking=True,
        refresh_intel=True,
        output_file="custom_analysis.md"
    )

    orchestrator = AnalysisOrchestrator(config)
    result = await orchestrator.analyze_case("CVE-2021-21985")

    # 详细处理结果
    if result.success:
        print(f"案例: {result.case_id}")
        print(f"语言: {result.language}")
        print(f"CVE分析: {'✅' if result.cve_result else '❌'}")
        print(f"Sink分析: {'✅' if result.sink_result else '❌'}")
        print(f"Source分析: {'✅' if result.source_result else '❌'}")
        print(f"CodeQL生成: {'✅' if result.codeql_result else '❌'}")

asyncio.run(custom_analysis())
```

### 示例3: 批量分析

```python
import asyncio
from core.orchestrator import AnalysisOrchestrator
from typing import List

async def batch_analysis(case_ids: List[str]):
    orchestrator = AnalysisOrchestrator()

    for case_id in case_ids:
        print(f"\n🔍 开始分析: {case_id}")
        try:
            result = await orchestrator.analyze_case(case_id)

            if result.success:
                print(f"✅ {case_id} 分析成功")
                print(f"⏱️  耗时: {result.execution_time:.2f}秒")
            else:
                print(f"❌ {case_id} 分析失败: {result.error_message}")

        except Exception as e:
            print(f"❌ {case_id} 执行异常: {e}")

# 使用示例
case_ids = ["CVE-2021-21985", "CVE-2021-44228", "CVE-2021-45046"]
asyncio.run(batch_analysis(case_ids))
```

---

*API文档持续更新中，如有疑问请查看源代码或提交Issue。*