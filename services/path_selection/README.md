# 路径选择与验证服务

## 概述

从CodeQL查询结果中智能选择最匹配CVE漏洞的路径。支持Java、C、Python三种语言。

## 核心特性

✅ **语义理解** - 基于LLM理解CVE的真实含义，而不是僵硬规则  
✅ **上下文感知** - 充分利用前面Pipeline步骤（CVE分析、Sink分析、Source分析）的结果  
✅ **多语言支持** - Java、C、Python统一接口，语言特定适配器  
✅ **智能聚类** - 自动识别和去除重复路径模式  
✅ **多维验证** - 完整性、正确性、置信度三重验证  
✅ **可解释性** - 提供清晰的路径选择理由和分析报告  

## 架构

```
┌─────────────────────────────────────────┐
│  PathSelectionService (主服务)          │
└─────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
    ▼             ▼             ▼
┌─────────┐ ┌──────────┐ ┌──────────┐
│CVE上下文│ │路径增强器│ │路径聚类器│
│ 提取器  │ │          │ │          │
└─────────┘ └──────────┘ └──────────┘
                  │
        ┌─────────┼─────────┐
        │                   │
        ▼                   ▼
┌──────────────┐    ┌──────────┐
│LLM智能分析器 │    │路径验证器│
└──────────────┘    └──────────┘
        │
        ▼
┌──────────────────┐
│  语言适配器       │
│  ├─ Python       │
│  ├─ Java         │
│  └─ C/C++        │
└──────────────────┘
```

## 使用方法

### 基础用法

```python
from services.path_selection import PathSelectionService
from services.llm_service import get_llm_client

# 初始化服务
llm_client = get_llm_client()
service = PathSelectionService(llm_client, language="python")

# 选择最佳路径
result = await service.select_best_paths(
    output_md_path="output/analysis_output_xxx/output.md",
    result_json_path="output/analysis_output_xxx/result.json",
    source_root="projects/CVE-2025-54381/source_code",
    top_k=3
)

# 输出结果
print(result.to_markdown())
```

### 集成到Pipeline

```python
from core.pipeline import AnalysisPipeline, AnalysisStep
from services.path_selection import PathSelectionService

class PathSelectionStep(AnalysisStep):
    """路径选择步骤"""
    
    def __init__(self):
        super().__init__("path_selection", agent_name="Path Selection")
    
    async def execute(self, context: AnalysisContext) -> Any:
        # 获取CodeQL执行结果
        codeql_result = context.get_result("codeql_execution")
        
        # 初始化路径选择服务
        llm_config = get_llm_config()
        service = PathSelectionService(llm_config, language=context.language)
        
        # 选择最佳路径
        selection_result = await service.select_best_paths(
            output_md_path=context.output_md_path,
            result_json_path=codeql_result.json_path,
            source_root=context.case_paths.source,
            top_k=3
        )
        
        return selection_result

# 添加到Pipeline
pipeline = AnalysisPipeline.create_default_pipeline()
pipeline.steps.append(PathSelectionStep())
```

## 工作流程

### 步骤1: CVE上下文提取

从`output.md`提取：
- CVE ID和漏洞类型
- 技术细节
- 预期的Sink点和Source点
- 详细的Sink分析和Source分析

### 步骤2: 路径增强

为每条路径添加：
- 实际代码上下文（前后3行）
- Source点分析（类型、变量名）
- Sink点分析（类型、危险API）
- 数据流摘要

### 步骤3: 路径聚类（可选）

- 按Sink点聚类
- 按Source点子聚类
- 从每个簇选择最佳代表

### 步骤4: LLM智能分析

LLM评估每条路径：
- Source匹配度（40分）
- Sink匹配度（40分）
- 数据流完整性（10分）
- 路径多样性（10分）

### 步骤5: 多维验证

验证选中的路径：
- **完整性检查**: Source/Sink存在性、位置信息、步骤连续性
- **正确性检查**: Sink匹配预期、Source合理性、数据流合理性
- **置信度检查**: LLM置信度、代码上下文、匹配分析

## 输出格式

### PathSelectionResult

```python
@dataclass
class PathSelectionResult:
    selected_paths: List[Dict]      # 选中的路径（Top-3）
    selection_reasoning: str        # 选择理由
    verification_summary: Dict      # 验证摘要
    coverage_analysis: Dict         # 覆盖率分析
    all_paths_count: int            # 总路径数
    language: str                   # 编程语言
```

### 示例输出

```json
{
  "selected_paths": [
    {
      "index": 0,
      "path_length": 4,
      "source_location": {
        "file": "src/_bentoml_impl/serde.py",
        "startLine": 164
      },
      "sink_location": {
        "file": "src/_bentoml_impl/serde.py",
        "startLine": 169
      },
      "selection_info": {
        "confidence": 0.95,
        "reason": "该路径展示了从request对象到httpx.get(url)的完整SSRF流程"
      },
      "verification": {
        "is_valid": true,
        "completeness": {"valid": true},
        "correctness": {"valid": true},
        "confidence": {"confidence_score": 0.95}
      }
    }
  ],
  "selection_reasoning": "从6条路径中选择这3条，覆盖了两个主要漏洞点...",
  "coverage_analysis": {
    "sink_coverage": ["JSONSerde.parse_request", "MultipartSerde.ensure_file"],
    "source_coverage": ["request对象", "obj参数"]
  }
}
```

## 语言适配器

### Python适配器

识别：
- **Source**: request, param, input, body, query
- **Sink**: exec, eval, system, open, httpx.get, execute, pickle.loads

### Java适配器

识别：
- **Source**: HttpServletRequest, @RequestParam, getParameter
- **Sink**: Runtime.exec, ProcessBuilder, JNDI lookup, SQL execute

### C/C++适配器

识别：
- **Source**: argv, stdin, gets, recv, getenv
- **Sink**: system, strcpy, sprintf, fopen, memcpy

## 配置选项

```python
service.select_best_paths(
    output_md_path="...",
    result_json_path="...",
    source_root="...",
    top_k=3,                    # 选择路径数量
    enable_clustering=True      # 是否启用聚类
)
```

## 降级方案

当LLM不可用或失败时，自动降级到规则选择：
- 基于路径长度评分
- 基于代码完整性评分
- 简单排序选择Top-K

## 扩展

### 添加新语言

1. 创建语言适配器继承`LanguageAdapter`
2. 实现`analyze_source_point()`和`analyze_sink_point()`
3. 定义危险API列表
4. 在`get_language_adapter()`中注册

### 自定义验证逻辑

继承`PathVerifier`并覆盖验证方法：

```python
class CustomPathVerifier(PathVerifier):
    def _check_correctness(self, path, cve_context):
        # 自定义正确性检查
        pass
```

## 性能考虑

- **路径聚类**: 当路径 > 2*top_k 时自动启用
- **代码读取**: 异步并发读取，缓存重复文件
- **LLM调用**: 单次调用，批量分析所有路径
- **降级机制**: LLM失败时自动切换到规则方案

## 测试

```bash
# 运行测试
pytest services/path_selection/tests/

# 测试单个组件
pytest services/path_selection/tests/test_llm_analyzer.py
```

## 常见问题

### Q: LLM分析失败怎么办？

A: 系统会自动降级到规则方案，基于路径长度和代码完整性进行简单排序。

### Q: 如何处理大量路径（100+条）？

A: 启用聚类功能（`enable_clustering=True`），系统会自动去重并选择代表性路径。

### Q: 如何调整选择标准？

A: 修改`llm_analyzer.py`中的提示词，调整各维度的权重。

### Q: 支持其他语言吗？

A: 目前支持Python、Java、C/C++。可以通过实现新的语言适配器来扩展。

## License

与主项目保持一致

