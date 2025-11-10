# 额外输入文件 - 快速入门

## 30 秒快速上手

### 1. 添加文件到 inputs 目录

```bash
cd projects/YOUR-CVE-ID/inputs/

# 添加系统架构信息
cat > context_architecture.md << 'EOF'
# 系统架构
- 框架: Spring Boot 2.3.4
- 认证: OAuth2
- 漏洞位置: API Gateway
EOF

# 添加版本配置
cat > config_versions.json << 'EOF'
{
  "spring_boot": "2.3.4.RELEASE",
  "java": "11"
}
EOF
```

### 2. 运行分析 - 无需任何代码修改！

```bash
python Analyze.py --case YOUR-CVE-ID
```

系统会自动发现并使用这些文件！

## 命名规范速查表

| 前缀 | 用途 | 示例 |
|------|------|------|
| `context_` | 📋 背景上下文 | `context_architecture.md` |
| `doc_` | 📚 文档说明 | `doc_api_reference.md` |
| `config_` | ⚙️ 配置信息 | `config_dependencies.json` |
| `note_` | 📝 分析笔记 | `note_findings.md` |
| `exploit_` | 💥 漏洞利用 | `exploit_poc.py` |
| `patch_` | 🔧 补丁信息 | `patch_details.md` |

## 实用示例

### 示例 1：添加框架版本信息

```bash
cat > inputs/config_dependencies.json << 'EOF'
{
  "framework": "Spring Boot",
  "version": "2.3.4.RELEASE",
  "vulnerable_module": "spring-cloud-gateway",
  "module_version": "2.2.5.RELEASE"
}
EOF
```

### 示例 2：添加系统架构说明

```bash
cat > inputs/context_architecture.md << 'EOF'
# 系统架构说明

## 整体架构
这是一个基于微服务的系统，使用 Spring Cloud Gateway 作为 API 网关。

## 关键组件
- API Gateway: 处理所有入站请求
- 认证服务: OAuth2 + JWT
- 业务服务: 多个 Spring Boot 微服务

## 漏洞影响范围
漏洞位于 Gateway 的路由验证模块，可能导致认证绕过。
EOF
```

### 示例 3：添加分析笔记

```bash
cat > inputs/note_initial_findings.md << 'EOF'
# 初步分析发现

## 漏洞触发点
1. RoutePredicateFactory 未正确验证 SpEL 表达式
2. 可通过 HTTP Header 注入恶意表达式
3. 可能导致 RCE

## 需要关注的代码区域
- RoutePredicateFactory 实现类
- SpEL 表达式解析器
- 权限验证逻辑

## 待验证
- [ ] 输入过滤机制
- [ ] 权限检查是否可绕过
- [ ] 影响版本范围
EOF
```

## 在代码中使用

### 基础访问

```python
from utils.case import resolve_case, discover_cve_assets

case_paths = resolve_case("CVE-2025-XXXXX")
cve_assets = discover_cve_assets(case_paths)

# 检查是否有额外文件
if cve_assets.has_extra_files():
    print(f"发现 {len(cve_assets.extra_files)} 个额外文件")
```

### 按类别筛选

```python
# 获取所有上下文文件
context_files = cve_assets.get_extra_files_by_category('context')
for f in context_files:
    content = f.read_text()
    print(content)

# 获取所有配置文件
config_files = cve_assets.get_extra_files_by_category('config')
for f in config_files:
    if f.file_type == 'json':
        data = f.read_json()
        print(data)
```

### 在 Agent 中使用

```python
# 在分析步骤中使用额外文件
class MyAnalysisStep(AnalysisStep):
    async def execute(self, context: AnalysisContext) -> Any:
        # 获取所有额外文件内容
        extra_content = context.cve_assets.get_all_extra_content()
        
        # 构建 LLM 提示词
        prompt = f"""
        请分析以下漏洞:
        CVE ID: {context.cve_assets.cve_id}
        
        {extra_content}
        
        请提供详细的分析结果。
        """
        
        # 调用 LLM...
        return result
```

## 常见问题

### Q: 需要修改代码吗？
A: **不需要！** 只需在 `inputs/` 目录添加文件即可。

### Q: 支持哪些文件格式？
A: Markdown (`.md`)、Text (`.txt`)、JSON (`.json`)、YAML (`.yaml`, `.yml`)、XML、CSV 等。

### Q: 文件会自动使用吗？
A: 系统会自动发现并分类，但需要在 Agent 代码中显式调用才会使用。默认的 Agent 还未集成，但您可以通过 `context.cve_assets.extra_files` 访问。

### Q: 有大小限制吗？
A: 建议每个文件不超过 1MB，总文件数不超过 20 个。

### Q: 如何命名文件？
A: 使用有意义的前缀（见上方命名规范表）和描述性名称。例如：`context_spring_framework.md`

## 更多资源

- 📖 [完整文档](extra_input_files.md) - 详细使用指南
- 💻 [代码示例](../examples/use_extra_input_files.py) - 基础使用示例
- 🤖 [Agent 集成示例](../examples/agent_with_extra_files.py) - 在 Agent 中使用
- 📋 [案例模板](../projects/case-template/README.md) - 模板说明

## 下一步

1. ✅ 在您的案例 `inputs/` 目录添加额外文件
2. ✅ 使用合适的命名前缀
3. ✅ 运行 `python Analyze.py --case YOUR-CASE-ID`
4. ✅ 查看示例了解如何在自定义 Agent 中使用

祝您分析顺利！🚀

