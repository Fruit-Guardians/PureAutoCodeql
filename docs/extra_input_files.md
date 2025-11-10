# 额外输入文件功能说明

## 概述

PureAutoCodeQL 现在支持在 `inputs/` 目录中添加额外的信息文件，这些文件会被自动发现、分类，并在整个分析流程中可供使用。

## 文件类型自动识别

系统根据文件扩展名自动识别以下文件类型：

- **markdown** - `.md` 文件
- **text** - `.txt` 文件
- **json** - `.json` 文件（非 CVE JSON）
- **yaml** - `.yaml`, `.yml` 文件
- **xml** - `.xml` 文件
- **csv** - `.csv` 文件
- **other** - 其他类型文件

## 文件类别自动分类

系统根据文件名前缀自动分类：

| 前缀 | 类别 | 说明 | 示例 |
|------|------|------|------|
| `context_` | context | 背景上下文信息 | `context_architecture.md` |
| `background_` | context | 背景信息 | `background_system.txt` |
| `info_` | context | 补充信息 | `info_dependencies.json` |
| `doc_` | doc | 文档说明 | `doc_api_reference.md` |
| `readme` | doc | README 文档 | `readme_project.md` |
| `guide_` | doc | 指南文档 | `guide_setup.md` |
| `config_` | config | 配置信息 | `config_environment.yaml` |
| `settings_` | config | 设置文件 | `settings_app.json` |
| `note_` | note | 笔记备注 | `note_analysis_thoughts.md` |
| `notes_` | note | 笔记 | `notes_findings.txt` |
| `memo_` | note | 备忘录 | `memo_important.md` |
| `exploit_` | exploit | 漏洞利用信息 | `exploit_poc.py` |
| `poc_` | exploit | POC 代码 | `poc_demo.py` |
| `payload_` | exploit | 攻击载荷 | `payload_example.txt` |
| `patch_` | patch | 补丁信息 | `patch_additional.diff` |
| `fix_` | patch | 修复说明 | `fix_details.md` |
| 其他 | other | 未分类文件 | `analysis.md` |

## 使用方式

### 1. 在 inputs 目录添加文件

```bash
# 进入案例目录
cd projects/CVE-2025-XXXXX/inputs/

# 添加各种额外文件
echo "系统使用了 Spring Boot 框架" > context_framework.txt
echo "# API 文档" > doc_api.md
echo '{"version": "1.2.3"}' > config_version.json
```

### 2. 在代码中访问额外文件

#### 基础访问

```python
from utils.case import resolve_case, discover_cve_assets

# 解析案例
case_paths = resolve_case("CVE-2025-XXXXX")
cve_assets = discover_cve_assets(case_paths)

# 检查是否有额外文件
if cve_assets.has_extra_files():
    print(f"发现 {len(cve_assets.extra_files)} 个额外文件")
    
    # 列出所有额外文件
    for extra_file in cve_assets.extra_files:
        print(f"- {extra_file.path.name}")
        print(f"  类型: {extra_file.file_type}")
        print(f"  类别: {extra_file.category}")
```

#### 按类型筛选

```python
# 获取所有 Markdown 文件
markdown_files = cve_assets.get_extra_files_by_type('markdown')
for md_file in markdown_files:
    content = md_file.read_text()
    print(f"Markdown 内容: {content}")

# 获取所有 JSON 文件
json_files = cve_assets.get_extra_files_by_type('json')
for json_file in json_files:
    data = json_file.read_json()
    print(f"JSON 数据: {data}")
```

#### 按类别筛选

```python
# 获取所有上下文文件
context_files = cve_assets.get_extra_files_by_category('context')
for ctx_file in context_files:
    print(f"上下文信息: {ctx_file.read_text()}")

# 获取所有文档文件
doc_files = cve_assets.get_extra_files_by_category('doc')

# 获取所有配置文件
config_files = cve_assets.get_extra_files_by_category('config')
```

#### 按名称查找

```python
# 查找包含 "framework" 的文件（不区分大小写）
framework_file = cve_assets.get_extra_file_by_name('framework')
if framework_file:
    print(f"找到文件: {framework_file.path.name}")
    print(f"内容: {framework_file.read_text()}")
```

#### 获取所有内容（用于 LLM）

```python
# 获取所有额外文件的格式化内容，适合作为 LLM 的上下文
all_content = cve_assets.get_all_extra_content()
print(all_content)
```

输出示例：
```
=== 额外输入文件 (3 个) ===

--- 文件: context_framework.txt (类型: text, 类别: context) ---
系统使用了 Spring Boot 框架

--- 文件: doc_api.md (类型: markdown, 类别: doc) ---
# API 文档
...

--- 文件: config_version.json (类型: json, 类别: config) ---
{"version": "1.2.3"}
```

### 3. 在 Agent 中使用

额外文件可以在分析的任何阶段使用，特别是在 Agent 的 prompt 中：

```python
# 在 CVE 分析 Agent 中
def build_prompt(context: AnalysisContext) -> str:
    prompt_parts = [
        "请分析以下漏洞...",
        # 添加额外文件内容
    ]
    
    # 如果有额外的上下文文件
    if context.cve_assets.has_extra_files():
        context_files = context.cve_assets.get_extra_files_by_category('context')
        if context_files:
            prompt_parts.append("\n## 额外上下文信息\n")
            for ctx_file in context_files:
                prompt_parts.append(f"### {ctx_file.path.name}\n")
                prompt_parts.append(ctx_file.read_text())
    
    return "\n".join(prompt_parts)
```

## 实际应用场景

### 场景 1: 添加系统架构信息

```bash
# 创建架构说明文件
cat > projects/CVE-2025-XXXXX/inputs/context_architecture.md << 'EOF'
# 系统架构

这是一个基于微服务架构的系统：
- API Gateway: Spring Cloud Gateway
- 认证服务: OAuth2 + JWT
- 业务服务: Spring Boot
- 数据库: PostgreSQL

漏洞位于 API Gateway 的路由验证模块。
EOF
```

Agent 在分析时会自动考虑这些架构信息。

### 场景 2: 添加依赖版本信息

```bash
# 创建依赖配置文件
cat > projects/CVE-2025-XXXXX/inputs/config_dependencies.json << 'EOF'
{
  "spring_boot": "2.3.4.RELEASE",
  "spring_cloud_gateway": "2.2.5.RELEASE",
  "jackson": "2.11.0"
}
EOF
```

### 场景 3: 添加分析笔记

```bash
# 添加分析过程中的笔记
cat > projects/CVE-2025-XXXXX/inputs/note_findings.md << 'EOF'
# 分析发现

1. 漏洞触发点在 RoutePredicateFactory
2. 需要关注 SpEL 表达式注入
3. 可能的攻击向量：通过 HTTP Header 传递恶意表达式
EOF
```

### 场景 4: 添加 POC 代码

```bash
# 添加概念验证代码
cat > projects/CVE-2025-XXXXX/inputs/poc_exploit.py << 'EOF'
import requests

# POC: SpEL 注入示例
payload = {
    "route": "#{T(java.lang.Runtime).getRuntime().exec('calc')}"
}

response = requests.post(
    "http://target/api/route",
    json=payload
)
EOF
```

## 最佳实践

### 1. 文件命名规范

使用有意义的前缀和描述性名称：
- ✅ `context_spring_framework_details.md`
- ✅ `doc_authentication_flow.md`
- ✅ `config_database_settings.json`
- ❌ `file1.txt`
- ❌ `temp.md`

### 2. 文件内容结构化

使用清晰的格式，便于 LLM 理解：

```markdown
# 标题

## 关键信息
- 框架版本: Spring Boot 2.3.4
- 漏洞模块: Gateway Route Handler

## 详细说明
...
```

### 3. 避免敏感信息

不要在额外文件中包含：
- 真实的 API Key
- 生产环境密码
- 个人身份信息

### 4. 保持文件简洁

每个文件专注于一个主题，避免过大的文件影响处理效率。

## 技术实现细节

### 数据结构

```python
@dataclass(frozen=True)
class ExtraFile:
    path: Path          # 文件路径
    file_type: str      # 文件类型
    category: str       # 文件类别
    
    def read_text() -> str      # 读取文本内容
    def read_json() -> dict     # 读取 JSON 内容

@dataclass(frozen=True)
class CveAssets:
    cve_id: str
    json_path: Path
    diff_path: Optional[Path]
    extra_files: tuple[ExtraFile, ...]  # 额外文件元组
    
    # 便捷访问方法
    def get_extra_files_by_type(file_type: str) -> list[ExtraFile]
    def get_extra_files_by_category(category: str) -> list[ExtraFile]
    def get_extra_file_by_name(name: str) -> Optional[ExtraFile]
    def has_extra_files() -> bool
    def get_all_extra_content() -> str
```

### 自动排除规则

系统会自动排除以下文件：
- 标准 CVE 文件：`CVE-*.json`, `CVE-*.diff`
- 隐藏文件：以 `.` 开头的文件
- 临时文件：以 `~` 结尾的文件
- 目录：只处理文件，不处理子目录

## 常见问题

### Q: 额外文件会影响性能吗？
A: 文件只在需要时才读取内容，不会影响基础流程性能。建议每个文件不超过 1MB。

### Q: 支持什么编码？
A: 默认使用 UTF-8 编码读取所有文本文件。

### Q: 如何在现有 Agent 中使用？
A: 通过 `context.cve_assets.extra_files` 访问，所有 Agent 都可以使用。

### Q: 文件数量有限制吗？
A: 没有硬性限制，但建议不超过 20 个文件，保持信息精简。

## 未来扩展

计划支持的功能：
- [ ] 支持从 URL 自动下载额外文件
- [ ] 支持二进制文件（如图片）的处理
- [ ] 支持文件内容的缓存和索引
- [ ] 支持自定义文件类别定义

## 相关资源

- [案例模板说明](../projects/case-template/README.md)
- [工具函数文档](../utils/case.py)
- [分析上下文说明](../core/context.py)

