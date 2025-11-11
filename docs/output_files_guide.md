# 输出文件说明

## 📁 输出目录结构

分析完成后，会在 `output/` 目录下生成带时间戳的输出文件夹：

```
output/
└── analysis_output_CVE-XXXX-XXXX_YYYYMMDD_HHMMSS/
    ├── CVE-XXXX-XXXX_output.md              # 完整分析报告
    ├── CVE-XXXX-XXXX_all_paths_raw.json     # 所有原始路径（CodeQL输出）
    ├── CVE-XXXX-XXXX_result.json            # ✅ 最终结果（选择的路径）
    ├── path_selection_report_CVE-XXXX-XXXX.md    # 路径选择报告
    ├── path_selection_detail_CVE-XXXX-XXXX.json  # 路径选择详细数据
    └── result_YYYYMMDD_HHMMSS.sarif         # 原始SARIF文件
```

## 📄 文件用途

### 1. `CVE-XXXX-XXXX_result.json` ⭐

**这是最终结果文件！**

- 📦 **内容**: 只包含路径选择后的**3条最佳路径**
- 📊 **格式**: 标准 CodeQL dataFlowPath 格式
- 🎯 **用途**: 直接用于漏洞验证、报告生成等
- 💡 **特点**: 简洁、精准、易于解析

**示例结构**:
```json
{
  "dataFlowPath": [
    {
      "threadFlows": [
        {
          "steps": [
            { "stepNumber": 1, "location": {...}, "nodeType": "Source" },
            { "stepNumber": 2, "location": {...}, "nodeType": "Intermediate" },
            { "stepNumber": 3, "location": {...}, "nodeType": "Sink" }
          ]
        }
      ]
    },
    // ... 其他2条路径
  ]
}
```

---

### 2. `CVE-XXXX-XXXX_all_paths_raw.json`

**原始数据，供调试使用**

- 📦 **内容**: CodeQL 检测出的**所有路径**（可能有几十上百条）
- 📊 **格式**: 从 SARIF 转换的 dataFlowPath 格式
- 🎯 **用途**: 调试、对比、深度分析
- ⚠️ **注意**: 包含大量未筛选的路径，可能有噪音

---

### 3. `path_selection_report_CVE-XXXX-XXXX.md`

**可读性报告**

- 📦 **内容**: 路径选择的详细说明
- 📊 **格式**: Markdown 文档
- 🎯 **用途**: 了解为什么选择这些路径
- 💡 **包含**: 
  - 选择理由
  - 每条路径的置信度
  - Source/Sink 分析
  - LLM 推理过程

---

### 4. `path_selection_detail_CVE-XXXX-XXXX.json`

**完整元数据**

- 📦 **内容**: 路径选择的所有技术细节
- 📊 **格式**: JSON（包含验证信息、打分细节等）
- 🎯 **用途**: 系统集成、二次开发、质量评估
- 💡 **包含**:
  - 选择的路径（带完整元数据）
  - 验证摘要
  - 覆盖率分析
  - 调试信息

---

### 5. `CVE-XXXX-XXXX_output.md`

**完整分析报告**

- 📦 **内容**: CVE、Source、Sink、CodeQL 查询的完整分析
- 📊 **格式**: Markdown 文档
- 🎯 **用途**: 了解整个分析过程
- 💡 **包含**: AI 推理过程、CodeQL 查询代码、执行结果

---

### 6. `result_YYYYMMDD_HHMMSS.sarif`

**原始 CodeQL 输出**

- 📦 **内容**: CodeQL 的原始 SARIF 格式结果
- 📊 **格式**: SARIF 2.1.0
- 🎯 **用途**: 与其他 SARIF 工具集成
- 💡 **特点**: 标准化、工具兼容性好

---

## 🚀 快速使用

### 只需要最终结果？
```bash
# 查看最终选择的路径
cat output/analysis_output_*/CVE-*_result.json
```

### 想了解为什么选这些路径？
```bash
# 查看可读报告
cat output/analysis_output_*/path_selection_report_*.md
```

### 需要对比所有路径？
```bash
# 查看所有原始路径
cat output/analysis_output_*/CVE-*_all_paths_raw.json
```

### 深度分析和调试？
```bash
# 查看完整元数据
cat output/analysis_output_*/path_selection_detail_*.json
```

---

## 📊 控制台输出优化

### 1. 候选路径点读（代码上下文）

**新增功能** - 在 LLM 分析前，会显示每个候选路径的代码上下文：

```
────────────────────────────────────────────────────────────
📚 候选 0 点读上下文 (得分: 0.4150)
────────────────────────────────────────────────────────────
  流程: qanything_kernel/connector/database/mysql/mysql_client.py:181 -> ...py:185 (9 steps)
  危险API: execute_query_

  ▸ SOURCE @ src/_bentoml_impl/serde.py:193
         188 |         import httpx
         189 |
         190 |         if isinstance(obj, UploadFile):
         191 |             return obj
         192 |         async with httpx.AsyncClient() as client:
      >>>  193 |             obj = obj.strip("\"'")  # The url may be JSON encoded
         194 |             logger.debug("Request with URL, downloading file from %s", obj)
         195 |             resp = await client.get(obj)

  ▸ SINK @ qanything_kernel/connector/database/mysql/mysql_client.py:185
         182 |         kb_ids_str = ','.join([f"'{x}'" for x in kb_ids])
         183 |         query = f"SELECT DISTINCT kb_name FROM {self.table_name} WHERE kb_id IN ({kb_ids_str})"
      >>>  185 |         result = self.execute_query_(query)
         186 |         if result:
         187 |             return result[0]['kb_name']
```

### 2. LLM 分析结果显示

之前（一大坨 JSON）：
```
LLM: {"selected_paths":[{"candidate_rank":0,"llm_alignment_score":0.85,...}],...}
```

现在（美化显示）：
```
🤔 [LLM路径分析] ..........

📊 LLM分析结果:
────────────────────────────────────────────────────────────
✓ 选中路径数: 3

  路径 1 [候选#0, 得分:0.85]
  └─ 从用户输入到SQL查询拼接的路径匹配SQL注入漏洞模式

  路径 2 [候选#1, 得分:0.80]
  └─ 从用户输入到from_status_to_status函数的SQL查询拼接点

  路径 3 [候选#2, 得分:0.75]
  └─ 从用户输入到delete_files函数的SQL查询执行点

💡 总体理由: 选择的3个候选路径都展示了从用户输入到SQL查询拼接点的直接数据流...
────────────────────────────────────────────────────────────
```

### 3. 精排结论显示

**新增功能** - 显示最终选择的路径及其分析步骤：

```
============================================================
📊 LLM 精排结论
============================================================

  ✓ 路径 #0 [候选排名: 0, 置信度: 0.5455]
    原因: 从用户输入到SQL查询拼接的路径匹配SQL注入漏洞模式
    分析步骤:
      · 确认sink点位于mysql_client.py第185行的SQL查询执行点
      · 路径经过9步传输，表明用户输入可能直接传递到SQL查询
      · 代码上下文显示存在SQL查询拼接点，符合SQL注入特征
      · 未发现有效的输入过滤或参数化查询

  ✓ 路径 #1 [候选排名: 1, 置信度: 0.5305]
    原因: 从用户输入到from_status_to_status函数的SQL查询拼接点
    分析步骤:
      · sink点位于mysql_client.py第434行的SQL查询执行点
      · 路径同样经过9步传输，表明类似的漏洞模式
      · from_status_to_status函数涉及状态转换，可能包含用户可控参数
      · 未发现参数化查询的使用
```

---

## 🔄 文件更新历史

### v2.1 (当前版本)
- ✅ 重命名文件，命名更清晰
- ✅ `_result.json` 为最终简洁结果
- ✅ `_all_paths_raw.json` 为所有原始路径
- ✅ **新增"点读"功能** - 显示候选路径的代码上下文
- ✅ **新增精排结论显示** - 显示最终选择路径的分析步骤
- ✅ 优化 LLM 分析结果显示（美化 JSON 输出）
- ✅ 分离详细数据和最终结果

### v2.0
- ✅ 优化控制台输出显示
- ✅ 分离详细数据和最终结果

### v1.0 (旧版本)
- ❌ `_result_xxx.json` 包含所有路径（让人困惑）
- ❌ `CVE-XXX.json` 为简洁结果（命名不直观）
- ❌ LLM 输出直接打印大段 JSON（不美观）
- ❌ 没有代码上下文显示

