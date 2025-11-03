# 如何添加成功案例到知识库

## 📂 文件组织结构

### 案例文件位置

```
QLdatabase/
└── <语言>/
    └── <语言缩写>/
        └── CVE-YYYY-XXXXX/
            ├── CVE-YYYY-XXXXX.ql      # 必需：CodeQL 查询文件
            ├── CVE-YYYY-XXXXX.json    # 可选：执行结果路径信息
            └── README.md              # 可选：案例说明
```

### 各语言的标准路径

| 语言 | 案例目录路径 |
|------|-------------|
| Python | `QLdatabase/Python/py/CVE-YYYY-XXXXX/` |
| Java | `QLdatabase/Java/java/CVE-YYYY-XXXXX/` |
| C/C++ | `QLdatabase/C/c/CVE-YYYY-XXXXX/` |

---

## ✅ 添加新案例的完整流程

### 步骤 1: 创建案例目录

```bash
# Python 案例
mkdir -p QLdatabase/Python/py/CVE-2025-12345

# Java 案例
mkdir -p QLdatabase/Java/java/CVE-2025-12345

# C/C++ 案例
mkdir -p QLdatabase/C/c/CVE-2025-12345
```

### 步骤 2: 添加 .ql 文件

将成功的 CodeQL 查询文件放入对应目录：

```
QLdatabase/Python/py/CVE-2025-12345/CVE-2025-12345.ql
```

**文件命名规范**:
- ✅ `CVE-YYYY-XXXXX.ql` (推荐)
- ✅ `CVE-YYYY-XXXXX-description.ql` (带描述)
- ❌ 不要使用空格或特殊字符

### 步骤 3: 添加执行结果（可选）

如果有 SARIF 执行结果并转换为 JSON：

```
QLdatabase/Python/py/CVE-2025-12345/CVE-2025-12345.json
```

**JSON 格式**:
```json
{
  "dataFlowPath": [
    {
      "threadFlows": [
        {
          "steps": [
            {
              "stepNumber": 1,
              "location": {
                "file": "path/to/file.py",
                "startLine": 42,
                "startColumn": 10,
                "endColumn": 20,
                "description": "ControlFlowNode for ...",
                "nodeType": "Source"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

### 步骤 4: 更新 cases.json

编辑 `QLdatabase/<语言>/knowledge_base/cases.json`，添加新案例：

```json
{
  "id": "case:cve-2025-12345",
  "cve": "CVE-2025-12345",
  "path": "QLdatabase/Python/py/CVE-2025-12345/CVE-2025-12345.ql",
  "summary": "简明描述漏洞和检测方法",
  "vulnerability_type": "漏洞类型 (CWE-XXX)",
  "status": "success",
  "key_patterns": [
    "关键模式1: 具体描述",
    "关键模式2: 具体描述",
    "关键模式3: 具体描述",
    "关键模式4: 具体描述",
    "关键模式5: 具体描述"
  ],
  "helpers": [
    "helper:callee-is-global-name",
    "helper:callee-is-attr",
    "helper:其他使用的helper"
  ],
  "tags": ["主要标签", "次要标签", "框架名", "漏洞类型", "cwe-xxx"]
}
```

### 步骤 5: 提取并添加新 Helpers（如果有）

如果案例中使用了新的、可复用的 helper 谓词：

1. 提取 helper 代码
2. 编辑 `QLdatabase/<语言>/knowledge_base/helpers.json`
3. 添加新 helper 条目：

```json
{
  "id": "helper:new-helper-name",
  "name": "newHelperName",
  "signature": "predicate newHelperName(DataFlow::Node n, ...)",
  "description": "详细描述这个 helper 的用途",
  "implementation": "完整的 QL 代码实现",
  "example": "使用示例代码",
  "source": "CVE-2025-12345",
  "tags": ["相关", "标签"]
}
```

---

## 📋 案例质量检查清单

添加案例前，确保：

### 必需项
- [ ] `.ql` 文件可以成功编译
- [ ] 查询在目标代码库上成功执行
- [ ] 找到了预期的漏洞路径
- [ ] 文件命名符合规范
- [ ] 已添加到 `cases.json`

### 推荐项
- [ ] 包含执行结果 JSON
- [ ] 在 `cases.json` 中列出关键模式
- [ ] 引用使用的 helpers
- [ ] 添加准确的标签
- [ ] 提取可复用的 helpers

### 高质量项
- [ ] 添加案例 README.md 说明
- [ ] 记录降噪策略
- [ ] 说明为什么这个方法有效
- [ ] 记录迭代过程（如果有多轮）

---

## 🎯 关键模式提取指南

好的 `key_patterns` 应该：

### ✅ 好的例子
```json
"key_patterns": [
  "RemoteFlowSource as source with file/function scoping",
  "redirect() calls as sink (HttpResponseRedirect, shortcuts.redirect)",
  "url_has_allowed_host_and_scheme as sanitizer",
  "Multiple callee matching helpers (calleeIsName, calleeIsAttr)",
  "Django framework-specific receiver checks"
]
```

### ❌ 不好的例子
```json
"key_patterns": [
  "使用了 source",
  "检测 sink",
  "有 helper"
]
```

**关键模式应该包含**:
1. 具体的技术名称（RemoteFlowSource, calleeIsGlobalName）
2. 为什么使用（with file scoping, for noise reduction）
3. 具体的函数/方法名（redirect(), open()）
4. 框架或库的名称（Django, Flask, pandas）

---

## 🏷️ 标签规范

### 标签类别

1. **漏洞类型**: `sql-injection`, `path-traversal`, `open-redirect`, `code-injection`
2. **CWE**: `cwe-89`, `cwe-22`, `cwe-601`, `cwe-94`
3. **框架**: `django`, `flask`, `pandas`, `PIL`, `langchain`
4. **技术**: `taint`, `dataflow`, `remote-source`, `parameter-source`
5. **特殊技术**: `scoping`, `sanitizer`, `additional-flow`, `noise-reduction`

### 标签示例
```json
"tags": [
  "django",           // 框架
  "open-redirect",    // 漏洞类型
  "cwe-601",         // CWE 编号
  "remote-source",   // 数据源类型
  "sanitizer",       // 使用了 sanitizer
  "noise-reduction"  // 有降噪策略
]
```

---

## 📊 案例优先级

### 高优先级（应该添加）
- ✅ 使用了新的 helper 模式
- ✅ 展示了有效的降噪策略
- ✅ 覆盖了新的漏洞类型
- ✅ 展示了框架特定的检测方法
- ✅ 使用了高级技术（isAdditionalFlowStep）

### 中优先级（可以添加）
- 🟡 现有模式的变体
- 🟡 不同框架的相似漏洞
- 🟡 参数优化的案例

### 低优先级（谨慎添加）
- 🔴 与现有案例高度重复
- 🔴 过于简单没有学习价值
- 🔴 过于复杂难以复用

---

## 🔄 更新现有案例

如果发现更好的实现方法：

1. **不要删除旧案例**，而是创建新版本：
   ```
   CVE-2024-8412/
   ├── CVE-2024-8412.ql           # 原版本
   └── CVE-2024-8412-v2.ql        # 改进版本
   ```

2. **在 cases.json 中标记**：
   ```json
   {
     "id": "case:cve-2024-8412-v2",
     "summary": "Improved version with better noise reduction",
     "supersedes": "case:cve-2024-8412"
   }
   ```

---

## 🎓 最佳实践

### DO ✅
1. **一致的命名**: 使用 `CVE-YYYY-XXXXX` 格式
2. **完整的元数据**: 在 QL 文件头部添加完整的 QLDoc
3. **清晰的注释**: 在关键 helper 处添加注释
4. **标准化结构**: 遵循模板的固定骨架
5. **详细的 key_patterns**: 列出 5+ 个关键模式

### DON'T ❌
1. **不要使用空格**: 文件名避免空格
2. **不要省略案例信息**: cases.json 中的信息尽量完整
3. **不要重复代码**: 提取可复用的 helper
4. **不要忽略标签**: 至少添加 5 个有意义的标签
5. **不要只放 .ql 文件**: 尽量包含 JSON 结果

---

## 📝 完整示例

```bash
# 1. 创建目录
mkdir -p QLdatabase/Python/py/CVE-2025-12345

# 2. 添加查询文件
cat > QLdatabase/Python/py/CVE-2025-12345/CVE-2025-12345.ql << 'EOF'
/**
 * @kind path-problem
 * @name CVE-2025-12345: Description
 * @description Detailed description
 * @id python/cve-2025-12345
 * @tags security, taint, ...
 * @problem.severity high
 * @precision high
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources

// ... 查询代码 ...
EOF

# 3. 运行并保存结果
codeql query run CVE-2025-12345.ql --database=... --output=CVE-2025-12345.sarif

# 4. 转换 SARIF 到 JSON
python utils/sarif_to_json.py CVE-2025-12345.sarif > CVE-2025-12345.json

# 5. 更新 cases.json
# 手动编辑 QLdatabase/Python/knowledge_base/cases.json

# 6. 如果有新 helper，更新 helpers.json
# 手动编辑 QLdatabase/Python/knowledge_base/helpers.json
```

---

## 🚀 快速命令

创建新 Python 案例的快速脚本：

```bash
#!/bin/bash
CVE_ID=$1  # 如 CVE-2025-12345

mkdir -p "QLdatabase/Python/py/${CVE_ID}"
touch "QLdatabase/Python/py/${CVE_ID}/${CVE_ID}.ql"
touch "QLdatabase/Python/py/${CVE_ID}/${CVE_ID}.json"
echo "Created: QLdatabase/Python/py/${CVE_ID}/"
echo "Next steps:"
echo "1. Edit ${CVE_ID}.ql"
echo "2. Run query and save result to ${CVE_ID}.json"
echo "3. Update QLdatabase/Python/knowledge_base/cases.json"
echo "4. If new helpers, update helpers.json"
```

---

## 📚 参考资源

- **现有案例**: `QLdatabase/Python/py/`
- **案例索引**: `QLdatabase/Python/knowledge_base/cases.json`
- **Helper 库**: `QLdatabase/Python/knowledge_base/helpers.json`
- **模板**: `agents/prompts/python_template_ql.md`

---

有问题？查看现有的 8 个成功案例作为参考！

