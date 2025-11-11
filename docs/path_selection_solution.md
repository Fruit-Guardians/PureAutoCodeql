# 路径选择与验证 - 最终智能方案

## 📋 问题背景

针对真实CVE漏洞，CodeQL查询可能产生大量路径（几十甚至上百条），但我们只需要选出**最能证明CVE存在的3条最合适路径**，同时要对路径的**正确性和完整性**进行检测。

### 关键约束

1. ✅ 针对真实CVE，不是僵硬评级
2. ✅ 前面Pipeline已完成CVE分析、Sink分析、Source分析
3. ✅ 需要语义理解，而非简单规则
4. ✅ 支持Java、C、Python三种语言
5. ✅ 需要验证路径的正确性和完整性

---

## 🎯 最终方案架构

### 核心思想

**利用LLM的语义理解能力 + 前面Pipeline收集的所有信息 + 多维度验证 = 智能路径选择**

### 系统架构

```
┌──────────────────────────────────────────────────────────┐
│                输入                                       │
│  ├─ output.md (CVE分析、Sink分析、Source分析)            │
│  ├─ result.json (CodeQL所有路径)                        │
│  ├─ source_root (源代码目录)                            │
│  └─ language (java/c/python)                            │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│            PathSelectionService 主服务                    │
└──────────────────────────────────────────────────────────┘
                        ↓
        ┌───────────────┼───────────────┐
        ↓               ↓               ↓
┌──────────────┐ ┌────────────┐ ┌──────────────┐
│ 步骤1        │ │ 步骤2      │ │ 步骤3        │
│ CVE上下文    │ │ 路径增强   │ │ 路径聚类     │
│ 提取         │ │           │ │ (可选)       │
└──────────────┘ └────────────┘ └──────────────┘
        │               │               │
        └───────────────┼───────────────┘
                        ↓
        ┌───────────────┼───────────────┐
        ↓                               ↓
┌──────────────┐               ┌──────────────┐
│ 步骤4        │               │ 步骤5        │
│ LLM智能分析  │               │ 多维度验证   │
│              │               │              │
└──────────────┘               └──────────────┘
        │                               │
        └───────────────┬───────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│                    输出                                   │
│  ├─ Top-3 路径 (带可解释性理由)                          │
│  ├─ 验证报告 (完整性、正确性、置信度)                    │
│  ├─ 覆盖率分析 (Sink覆盖、Source覆盖)                    │
│  └─ Markdown报告 + JSON结果                             │
└──────────────────────────────────────────────────────────┘
```

---

## 🔧 核心模块详解

### 1. CVE上下文提取器 (context_extractor.py)

**作用**: 从`output.md`提取前面Pipeline收集的所有信息

**提取内容**:
- CVE ID和漏洞类型
- 技术细节
- **预期的Sink点** (CVE分析中的危险操作)
- **预期的Source点** (CVE分析中的用户输入)
- **详细的Sink分析** (Sink Agent的完整分析)
- **详细的Source分析** (Source Agent的完整分析)
- 情报摘要 (NVD/GHSA信息)

**关键点**: 这是核心！充分利用前面步骤已经收集的信息，而不是重新分析。

---

### 2. 路径增强器 (path_enricher.py)

**作用**: 为每条路径添加语义信息和代码上下文

**增强内容**:
- 读取Source和Sink的实际代码（前后3行）
- 使用**语言适配器**识别关键信息:
  - Source类型 (http_request/parameter/user_input等)
  - Sink类型 (command_execution/sql_execution/ssrf等)
  - 涉及的危险API
  - 变量名/方法名
- 生成易于LLM理解的流程摘要

**语言适配器**:

| 语言 | 适配器 | 识别能力 |
|------|--------|----------|
| **Python** | `PythonAdapter` | request/param/input → exec/eval/httpx.get/execute |
| **Java** | `JavaAdapter` | HttpServletRequest/@RequestParam → Runtime.exec/JNDI/SQL |
| **C/C++** | `CAdapter` | argv/stdin/gets → system/strcpy/sprintf |

---

### 3. 路径聚类器 (path_clusterer.py)

**作用**: 对相似路径聚类，减少冗余

**聚类策略**:
1. 按Sink点聚类 (文件+行号)
2. 每个Sink簇内再按Source点聚类
3. 从每个子簇选择最佳代表

**评分标准**:
- 路径长度适中 (4-8步最佳)
- 有完整代码上下文
- Source/Sink描述清晰

**何时启用**: 路径数量 > 2×top_k 时自动启用

---

### 4. LLM智能分析器 (llm_analyzer.py) ⭐核心

**作用**: 使用LLM理解CVE语义，评估路径匹配度

**输入给LLM的提示词结构**:

```
# CVE漏洞背景
- CVE ID: CVE-2025-54381
- 漏洞类型: SSRF
- 技术细节: ...
- 预期Sink点: httpx.AsyncClient().get()
- 预期Source点: 用户提供的URL参数
- 详细Sink分析: (完整的Sink Agent输出)
- 详细Source分析: (完整的Source Agent输出)

# CodeQL查询结果
## 路径 0
- 步骤数: 4
- 流程摘要: request → body → url → client.get()
- Source点:
  - 位置: serde.py:164
  - 代码: (实际代码上下文)
- Sink点:
  - 位置: serde.py:169
  - 代码: (实际代码上下文)

## 路径 1
...

# 任务
从上述路径中选择3条最能证明该CVE漏洞存在的路径。

## 选择标准
1. Source匹配度 (40分)
2. Sink匹配度 (40分)
3. 数据流完整性 (10分)
4. 路径多样性 (10分)

## 输出格式
返回JSON (包含selected_paths、reasoning、coverage_analysis)
```

**LLM评估维度**:

| 维度 | 权重 | 说明 |
|------|------|------|
| **Source匹配** | 40% | 路径起点是否与预期Source一致 |
| **Sink匹配** | 40% | 路径终点是否与预期Sink一致 |
| **数据流完整性** | 10% | 数据流是否清晰合理 |
| **路径多样性** | 10% | 覆盖不同漏洞点/攻击向量 |

**降级方案**: LLM失败时自动切换到简单规则排序

---

### 5. 路径验证器 (path_verifier.py)

**作用**: 对选中的路径进行三重验证

#### 5.1 完整性检查

- ✅ Source和Sink信息完整
- ✅ 文件路径、行号存在
- ✅ 路径长度合理 (2-50步)
- ✅ 步骤编号连续

#### 5.2 正确性检查

- ✅ Sink是否在预期的文件/函数中
- ✅ Source是否是用户可控输入
- ✅ 数据流是否符合漏洞模式
- ✅ Source和Sink不在同一位置

#### 5.3 置信度检查

- ✅ LLM给出的置信度 ≥ 0.5
- ✅ 有实际代码上下文
- ✅ 匹配分析清晰

**验证结果**: 每条路径都有详细的验证报告，包括issues和warnings

---

## 🌍 跨语言支持

### 语言适配器设计

```python
class LanguageAdapter(ABC):
    @abstractmethod
    def analyze_source_point(location, code) -> Dict
    
    @abstractmethod
    def analyze_sink_point(location, code) -> Dict
    
    @abstractmethod
    def get_dangerous_apis() -> List[str]
```

### Python适配器

**Source识别**:
- `request`, `body`, `input` → `http_request`
- `param`, `arg` → `parameter`
- `stdin`, `gets` → `user_input`

**Sink识别**:
- `exec`, `eval`, `system` → `command_execution`
- `httpx.get`, `requests.post` → `http_request` (SSRF)
- `execute`, `cursor.execute` → `sql_execution`
- `pickle.loads` → `deserialization`

### Java适配器

**Source识别**:
- `HttpServletRequest` → `http_request`
- `getParameter`, `@RequestParam` → `request_parameter`
- `getHeader` → `http_header`

**Sink识别**:
- `Runtime.exec`, `ProcessBuilder` → `command_execution`
- `InitialContext.lookup` → `jndi_injection`
- `Statement.execute` → `sql_execution`
- `ObjectInputStream.readObject` → `deserialization`

### C/C++适配器

**Source识别**:
- `argv`, `argc` → `command_line_argument`
- `stdin`, `gets`, `scanf` → `user_input`
- `getenv` → `environment_variable`

**Sink识别**:
- `system`, `exec` → `command_execution`
- `strcpy`, `sprintf` → `buffer_overflow`
- `printf`, `fprintf` → `format_string`

---

## 📊 输出格式

### PathSelectionResult

```json
{
  "selected_paths": [
    {
      "index": 0,
      "path_length": 4,
      "source_location": {"file": "...", "startLine": 164},
      "source_code": ">>> 164 | body = await request.body()",
      "source_analysis": {
        "type": "http_request",
        "variable": "request"
      },
      "sink_location": {"file": "...", "startLine": 169},
      "sink_code": ">>> 169 | resp = await client.get(url)",
      "sink_analysis": {
        "type": "http_request",
        "dangerous_apis": ["httpx.get"]
      },
      "selection_info": {
        "confidence": 0.95,
        "reason": "该路径展示了从request对象到httpx.get(url)的完整SSRF流程...",
        "match_analysis": {
          "source_match": true,
          "sink_match": true,
          "flow_complete": true
        }
      },
      "verification": {
        "is_valid": true,
        "completeness": {"valid": true, "issues": []},
        "correctness": {"valid": true, "warnings": []},
        "confidence": {"confidence_score": 0.95}
      }
    }
  ],
  "selection_reasoning": "从6条路径中选择这3条，因为...",
  "verification_summary": {
    "all_valid": true,
    "valid_count": 3,
    "total_verified": 3,
    "issues": []
  },
  "coverage_analysis": {
    "sink_coverage": ["JSONSerde.parse_request (169行)", "MultipartSerde.ensure_file (195行)"],
    "source_coverage": ["request对象", "obj参数"]
  },
  "all_paths_count": 6,
  "language": "python"
}
```

### Markdown报告

自动生成包含以下内容的报告:
- 📊 概览 (总路径数、选中数、语言)
- 🎯 选择理由
- 📝 选中路径详情 (Source/Sink/置信度/理由)
- ✅ 验证摘要
- 📈 覆盖率分析

---

## 🚀 使用方法

### 基础用法

```python
from services.path_selection import PathSelectionService
from config import get_chat_config

# 初始化
llm_config = get_chat_config()
service = PathSelectionService(llm_config, language="python")

# 选择路径
result = await service.select_best_paths(
    output_md_path="output/xxx/output.md",
    result_json_path="output/xxx/result.json",
    source_root="projects/CVE-XXX/source_code",
    top_k=3
)

# 输出报告
print(result.to_markdown())
result_json.write_text(json.dumps(result.to_dict(), indent=2))
```

### 集成到Pipeline

```python
# 在 core/pipeline.py 中添加步骤

class PathSelectionStep(AnalysisStep):
    def __init__(self):
        super().__init__("path_selection")
    
    async def execute(self, context: AnalysisContext):
        service = PathSelectionService(llm_client, context.language)
        return await service.select_best_paths(...)

# 添加到默认Pipeline
pipeline.steps.append(PathSelectionStep())
```

---

## 💡 核心优势

### 1. ✅ 充分利用已有信息
- 不重复收集，直接使用Pipeline前面步骤的结果
- CVE分析、Sink分析、Source分析都已就绪
- 节省时间和LLM调用成本

### 2. ✅ 语义理解而非僵硬规则
- LLM理解CVE的真实含义
- 基于上下文评估路径匹配度
- 不是简单的关键词匹配或规则评分

### 3. ✅ 跨语言统一架构
- Java、C、Python统一接口
- 语言特定逻辑封装在适配器中
- 易于扩展新语言

### 4. ✅ 多维度验证
- 完整性、正确性、置信度三重检查
- 提供详细的验证报告
- 识别潜在问题和警告

### 5. ✅ 高可解释性
- 清晰的选择理由
- 详细的匹配分析
- 覆盖率统计

### 6. ✅ 鲁棒性
- LLM失败时自动降级
- 路径过多时自动聚类
- 异常处理完善

---

## 📈 性能考虑

| 场景 | 处理方式 | 性能 |
|------|----------|------|
| 路径 < 10条 | 直接LLM分析 | 快速 (1次LLM调用) |
| 10-50条 | 启用聚类 → LLM分析 | 中等 (聚类+1次LLM) |
| 50+条 | 聚类去重 → LLM分析 | 较快 (大幅减少待分析路径) |
| LLM失败 | 降级到规则排序 | 快速 (无LLM调用) |

**优化点**:
- 异步并发读取代码文件
- 重复文件缓存
- 批量分析所有路径（单次LLM调用）

---

## 🔮 扩展性

### 添加新语言

1. 创建适配器: `services/path_selection/language_adapters/golang_adapter.py`
2. 实现接口: `analyze_source_point`, `analyze_sink_point`
3. 定义危险API列表
4. 注册: 在`__init__.py`中添加映射

### 自定义验证逻辑

继承并覆盖验证方法:

```python
class CustomVerifier(PathVerifier):
    def _check_correctness(self, path, cve_context):
        # 自定义逻辑
        pass
```

### 自定义LLM提示词

修改`llm_analyzer.py`中的`_build_prompt`方法

---

## 📚 文档和示例

- **完整文档**: `services/path_selection/README.md`
- **使用示例**: `examples/path_selection_demo.py`
- **集成指南**: 见上方"集成到Pipeline"部分

---

## 🎉 总结

这是一个**基于LLM语义理解的智能路径选择系统**，专为真实CVE漏洞分析设计:

1. ✅ 充分利用前面Pipeline收集的所有CVE信息
2. ✅ LLM理解漏洞语义，而非僵硬规则
3. ✅ 支持Java、C、Python三种语言
4. ✅ 多维度验证（完整性、正确性、置信度）
5. ✅ 高可解释性（清晰的选择理由）
6. ✅ 鲁棒性（降级方案、聚类优化）

**这就是最终的智能解决方案！** 🚀

