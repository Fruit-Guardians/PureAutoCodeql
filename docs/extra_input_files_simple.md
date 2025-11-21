# 额外输入文件支持

## 功能说明

现在您可以在案例的 `inputs/` 目录中添加**任意额外文件**，这些文件的内容会在分析时作为补充上下文信息提供给 LLM。

## 使用方法

### 1. 添加文件

在您的案例 `inputs/` 目录中添加任意文件：

```bash
cd projects/YOUR-CVE-ID/inputs/

# 添加任意文件，文件名随意
echo "这个系统使用了 Spring Boot 2.3.4" > 架构说明.txt
echo "发现了 SpEL 注入漏洞" > 分析笔记.md
echo '{"version": "2.3.4"}' > versions.json
```

**没有任何命名限制**，您可以使用任何文件名和格式。

### 2. 运行分析

正常运行分析即可，系统会自动发现并使用这些文件：

```bash
python Analyze.py --case YOUR-CVE-ID
```

### 3. 查看日志

运行时会看到类似输出：

```
📂 [额外文件] 发现 3 个额外输入文件:
   - 架构说明.txt
   - 分析笔记.md
   - versions.json
```

## 在代码中使用

如果您需要在自定义 Agent 中访问这些文件：

```python
from utils.case import resolve_case, discover_cve_assets

# 获取案例资源
case_paths = resolve_case("YOUR-CVE-ID")
cve_assets = discover_cve_assets(case_paths)

# 检查是否有额外文件
if cve_assets.has_extra_files():
    print(f"找到 {len(cve_assets.extra_files)} 个额外文件")
    
    # 遍历所有额外文件
    for extra_file in cve_assets.extra_files:
        filename = extra_file.path.name
        content = extra_file.read_text()
        print(f"{filename}: {content}")
    
    # 或者一次性获取所有文件内容（格式化后，适合作为 LLM 上下文）
    all_content = cve_assets.get_all_extra_content()
    print(all_content)
```

## 自动排除的文件

系统会自动排除以下文件，不作为额外文件处理：
- `CVE-*.json` - CVE 信息文件
- `CVE-*.patch/.diff` - 补丁文件
- `.` 开头的隐藏文件
- `~` 结尾的临时文件

## 实际示例

### 示例 1：添加背景信息

```bash
cat > inputs/系统背景.md << 'EOF'
# 系统信息

这是一个使用 Spring Boot 的微服务系统。
主要组件：
- API Gateway (Spring Cloud Gateway 2.2.5)
- 认证服务 (OAuth2)
- 业务服务

漏洞出现在 Gateway 的路由验证部分。
EOF
```

### 示例 2：添加版本信息

```bash
cat > inputs/依赖版本.txt << 'EOF'
Spring Boot: 2.3.4.RELEASE
Spring Cloud Gateway: 2.2.5.RELEASE
Java: 11
EOF
```

### 示例 3：添加分析记录

```bash
cat > inputs/我的发现.md << 'EOF'
# 初步分析

发现了 SpEL 表达式注入漏洞。
触发点在 RoutePredicateFactory。
可能通过 HTTP Header 注入恶意表达式。
EOF
```

## 常见问题

**Q: 支持什么文件格式？**
A: 所有文本格式都支持（.txt, .md, .json, .yaml, .xml, .csv 等）

**Q: 文件名有要求吗？**
A: 没有！可以使用任意文件名，中文也可以

**Q: 文件数量有限制吗？**
A: 没有硬性限制，但建议不要太多，保持信息精简

**Q: 需要修改代码吗？**
A: 不需要！只需添加文件即可

**Q: 如何让 Agent 使用这些文件？**
A: 在 Agent 中通过 `context.cve_assets.get_all_extra_content()` 获取所有额外文件内容，添加到 LLM 的 prompt 中即可

## 简单示例代码

在 Agent 中使用额外文件的完整示例：

```python
async def my_analysis_step(context: AnalysisContext):
    # 构建基础 prompt
    prompt = f"请分析 {context.cve_assets.cve_id} 漏洞\n\n"
    
    # 添加额外文件内容
    if context.cve_assets.has_extra_files():
        extra_content = context.cve_assets.get_all_extra_content()
        prompt += f"\n补充信息：\n{extra_content}\n"
    
    # 调用 LLM
    result = await llm_service.analyze(prompt)
    return result
```

就这么简单！🎉

