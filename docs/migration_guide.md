# 迁移指南：从旧架构到新架构

## 📋 概述

本指南帮助您从旧的项目架构平滑迁移到新的分层架构。新架构提供了更好的可维护性、可扩展性和代码组织。

## 🎯 迁移策略

### 渐进式迁移
- ✅ **保持向后兼容** - 原有代码继续工作
- ✅ **逐步替换** - 可以逐个模块迁移
- ✅ **零风险** - 随时可以回滚到旧版本

### 推荐迁移路径
```
阶段1: 使用新入口文件 (无风险)
├── 验证新架构功能
└── 确认兼容性

阶段2: 逐步迁移代码 (低风险)
├── 使用新的服务类
└── 保持原有接口

阶段3: 完全迁移 (中等风险)
├── 使用新的核心API
└── 重构业务逻辑
```

## 🔄 具体迁移步骤

### 阶段1: 使用新入口文件 (推荐立即开始)

#### 1.1 更新导入语句

**旧代码**:
```python
# 原来的导入方式
from Analyze import run_case_analysis, detect_language
```

**新代码**:
```python
# 使用新入口文件 (完全兼容)
from Analyze_new import run_case_analysis, detect_language

# 或者直接使用新架构 (推荐)
from core.orchestrator import AnalysisOrchestrator
```

#### 1.2 验证基本功能

```python
# 测试脚本 - test_migration.py
import asyncio
from Analyze_new import run_case_analysis

async def test_migration():
    try:
        # 使用新入口文件运行分析
        await run_case_analysis("CVE-2021-21985", stream=False)
        print("✅ 新架构工作正常")
    except Exception as e:
        print(f"❌ 迁移测试失败: {e}")

if __name__ == "__main__":
    asyncio.run(test_migration())
```

#### 1.3 运行兼容性测试

```bash
# 运行架构测试
python test_new_architecture.py

# 运行迁移测试
python test_migration.py
```

### 阶段2: 逐步迁移服务类

#### 2.1 替换 LSP 服务

**旧代码**:
```python
# 在 Analyze.py 中直接使用
lsp_service = CodeQLLSPService()
```

**新代码**:
```python
# 从服务模块导入
from services.lsp_service import CodeQLLSPService

lsp_service = CodeQLLSPService()
```

#### 2.2 替换 Agent 分析器

**旧代码**:
```python
from Analyze import MultiAgentAnalyzer, AgentResult
```

**新代码**:
```python
from services.llm_service import MultiAgentAnalyzer, AgentResult
```

#### 2.3 替换语言检测

**旧代码**:
```python
from Analyze import detect_language
language = detect_language(case_paths)
```

**新代码**:
```python
from services.language_detector import LanguageDetector

detector = LanguageDetector()
language = detector.detect_language(case_paths)
```

### 阶段3: 迁移到核心API (高级用户)

#### 3.1 使用 AnalysisOrchestrator

**旧代码**:
```python
async def analyze_case_old(case_id: str):
    # 复杂的初始化和协调逻辑
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    intel_bundle = collect_intel(case_paths, cve_assets)
    # ... 更多复杂逻辑

    await run_multi_agent_analysis(...)
```

**新代码**:
```python
from core.orchestrator import AnalysisOrchestrator

async def analyze_case_new(case_id: str):
    orchestrator = AnalysisOrchestrator()
    result = await orchestrator.analyze_case(case_id)
    return result
```

#### 3.2 自定义分析流水线

```python
from core.pipeline import AnalysisPipeline, CVEAnalysisStep, SinkAnalysisStep
from core.orchestrator import AnalysisOrchestrator
from core.context import AnalysisConfig

# 创建自定义配置
config = AnalysisConfig(
    show_thinking=True,
    output_file="custom_output.md"
)

# 创建自定义流水线
custom_pipeline = AnalysisPipeline([
    CVEAnalysisStep(),
    # 只运行CVE分析，跳过其他步骤
])

# 使用自定义编排器
orchestrator = AnalysisOrchestrator(config)
orchestrator.pipeline = custom_pipeline  # 如果支持的话
```

#### 3.3 使用分析上下文

```python
from core.context import AnalysisContext, AnalysisResult

def process_analysis_result(result: AnalysisResult):
    """处理分析结果"""
    if result.success:
        print(f"分析成功完成: {result.case_id}")
        print(f"语言: {result.language}")
        print(f"执行时间: {result.execution_time:.2f}秒")

        if result.cve_result:
            print("CVE分析结果可用")
        if result.sink_result:
            print("Sink分析结果可用")
    else:
        print(f"分析失败: {result.error_message}")
```

## 📊 迁移对照表

| 功能 | 旧方式 | 新方式 | 迁移难度 |
|------|--------|--------|----------|
| 运行分析 | `from Analyze import run_case_analysis` | `from Analyze_new import run_case_analysis` | ⭐ 无需修改 |
| LSP服务 | `Analyze.CodeQLLSPService` | `services.lsp_service.CodeQLLSPService` | ⭐ 仅改导入 |
| Agent分析器 | `Analyze.MultiAgentAnalyzer` | `services.llm_service.MultiAgentAnalyzer` | ⭐ 仅改导入 |
| 语言检测 | `Analyze.detect_language()` | `services.language_detector.LanguageDetector()` | ⭐⭐ 需要适配 |
| 完整分析 | 复杂的手动协调 | `AnalysisOrchestrator.analyze_case()` | ⭐⭐⭐ 需要重构 |

## 🔧 常见迁移场景

### 场景1: 简单脚本迁移

**迁移前**:
```python
# simple_analysis.py
import asyncio
from Analyze import run_case_analysis

async def main():
    await run_case_analysis("CVE-2021-21985", stream=True)

if __name__ == "__main__":
    asyncio.run(main())
```

**迁移后**:
```python
# simple_analysis_new.py
import asyncio
from Analyze_new import run_case_analysis  # 只需要改这一行

async def main():
    await run_case_analysis("CVE-2021-21985", stream=True)

if __name__ == "__main__":
    asyncio.run(main())
```

### 场景2: 批量分析脚本迁移

**迁移前**:
```python
# batch_analysis.py
import asyncio
from Analyze import run_case_analysis, detect_language
from utils.case import resolve_case

async def analyze_multiple_cases(case_ids):
    for case_id in case_ids:
        print(f"分析案例: {case_id}")
        await run_case_analysis(case_id, stream=False)
```

**迁移后**:
```python
# batch_analysis_new.py
import asyncio
from core.orchestrator import AnalysisOrchestrator

async def analyze_multiple_cases(case_ids):
    orchestrator = AnalysisOrchestrator()
    for case_id in case_ids:
        print(f"分析案例: {case_id}")
        result = await orchestrator.analyze_case(case_id)
        if result.success:
            print(f"✅ {case_id} 分析完成")
        else:
            print(f"❌ {case_id} 分析失败: {result.error_message}")
```

### 场景3: 自定义分析工具迁移

**迁移前**:
```python
# custom_tool.py
from Analyze import MultiAgentAnalyzer, AgentResult
from agents.cve_analysis_agent import CVEAnalysisAgent

async def custom_cve_analysis(json_path):
    analyzer = MultiAgentAnalyzer()
    await analyzer.initialize()

    agent = CVEAnalysisAgent(analyzer)
    result = await agent.analyze_cve(Path(json_path))
    return result
```

**迁移后**:
```python
# custom_tool_new.py
from services.llm_service import MultiAgentAnalyzer
from core.pipeline import CVEAnalysisStep
from core.context import AnalysisContext

async def custom_cve_analysis(json_path):
    # 方式1: 直接使用服务
    analyzer = MultiAgentAnalyzer()
    await analyzer.initialize()

    # 方式2: 使用分析步骤 (推荐)
    step = CVEAnalysisStep()
    # 创建模拟上下文
    context = create_mock_context(json_path)
    result = await step.execute(context)
    return result
```

## ⚠️ 注意事项和最佳实践

### 1. 测试先行
- ✅ 在迁移前运行完整测试
- ✅ 保留原文件备份 (`Analyze.py.backup`)
- ✅ 逐步验证每个迁移步骤

### 2. 错误处理
```python
# 添加适当的错误处理
try:
    from core.orchestrator import AnalysisOrchestrator
    orchestrator = AnalysisOrchestrator()
    result = await orchestrator.analyze_case(case_id)
except ImportError as e:
    print(f"新架构导入失败，回退到旧版本: {e}")
    from Analyze_new import run_case_analysis
    await run_case_analysis(case_id)
except Exception as e:
    print(f"分析执行失败: {e}")
```

### 3. 配置管理
```python
# 使用统一的配置管理
from core.context import AnalysisConfig

config = AnalysisConfig(
    show_thinking=args.stream,
    refresh_intel=args.refresh_intel,
    output_file=args.output or "output.md"
)
```

### 4. 日志记录
```python
import logging

# 在关键步骤添加日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

logger.info(f"开始分析案例: {case_id}")
result = await orchestrator.analyze_case(case_id)
logger.info(f"分析完成，状态: {'成功' if result.success else '失败'}")
```

## 🔄 回滚计划

如果迁移过程中遇到问题，可以快速回滚：

### 1. 恢复原始文件
```bash
# 备份当前新版本
cp Analyze.py Analyze_new_backup.py

# 恢复原始版本
cp Analyze.py.backup Analyze.py
```

### 2. 使用条件导入
```python
try:
    # 尝试使用新架构
    from core.orchestrator import AnalysisOrchestrator
    use_new_architecture = True
except ImportError:
    # 回退到旧架构
    from Analyze import run_case_analysis
    use_new_architecture = False
```

## 📞 支持和反馈

如果在迁移过程中遇到问题：

1. **检查文档** - 首先查看 [架构文档](architecture.md)
2. **运行测试** - 执行 `python test_new_architecture.py`
3. **查看日志** - 检查详细的错误信息
4. **渐进迁移** - 从最简单的步骤开始

---

*本迁移指南帮助您安全、平滑地升级到新架构。如有疑问，请参考项目文档或提交Issue。*