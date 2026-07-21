"""
额外输入文件功能使用示例

演示如何使用 inputs 目录中的额外文件来增强漏洞分析。
"""

from pathlib import Path
from pure_auto_codeql.utils.case import resolve_case, discover_cve_assets


def example_basic_usage():
    """基础使用示例"""
    print("\n=== 示例 1: 基础使用 ===\n")
    
    case_id = "CVE-2024-7099"
    
    # 解析案例
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    # 检查额外文件
    if cve_assets.has_extra_files():
        print(f"✅ 发现 {len(cve_assets.extra_files)} 个额外文件:")
        for extra_file in cve_assets.extra_files:
            print(f"  - {extra_file.path.name}")
            print(f"    类型: {extra_file.file_type}, 类别: {extra_file.category}")
    else:
        print("ℹ️  没有发现额外文件")


def example_filter_by_type():
    """按类型筛选示例"""
    print("\n=== 示例 2: 按类型筛选文件 ===\n")
    
    case_id = "CVE-2024-7099"
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    # 获取所有 Markdown 文件
    markdown_files = cve_assets.get_extra_files_by_type('markdown')
    if markdown_files:
        print(f"📝 找到 {len(markdown_files)} 个 Markdown 文件:")
        for md_file in markdown_files:
            print(f"\n  文件: {md_file.path.name}")
            content = md_file.read_text()
            # 显示前 200 个字符
            preview = content[:200] + "..." if len(content) > 200 else content
            print(f"  预览: {preview}")
    
    # 获取所有 JSON 文件（非 CVE JSON）
    json_files = cve_assets.get_extra_files_by_type('json')
    if json_files:
        print(f"\n📦 找到 {len(json_files)} 个 JSON 文件:")
        for json_file in json_files:
            print(f"\n  文件: {json_file.path.name}")
            try:
                data = json_file.read_json()
                print(f"  数据: {data}")
            except Exception as e:
                print(f"  读取错误: {e}")


def example_filter_by_category():
    """按类别筛选示例"""
    print("\n=== 示例 3: 按类别筛选文件 ===\n")
    
    case_id = "CVE-2024-7099"
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    categories = ['context', 'doc', 'config', 'note', 'exploit', 'patch', 'other']
    
    for category in categories:
        files = cve_assets.get_extra_files_by_category(category)
        if files:
            print(f"\n📂 类别 '{category}' ({len(files)} 个文件):")
            for f in files:
                print(f"  - {f.path.name}")


def example_search_by_name():
    """按名称搜索示例"""
    print("\n=== 示例 4: 按名称搜索文件 ===\n")
    
    case_id = "CVE-2024-7099"
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    # 搜索包含特定关键词的文件
    keywords = ['context', 'framework', 'config', 'poc']
    
    for keyword in keywords:
        result = cve_assets.get_extra_file_by_name(keyword)
        if result:
            print(f"🔍 搜索 '{keyword}': 找到 {result.path.name}")
        else:
            print(f"🔍 搜索 '{keyword}': 未找到")


def example_get_all_content():
    """获取所有内容示例（用于 LLM）"""
    print("\n=== 示例 5: 获取所有内容（LLM 上下文） ===\n")
    
    case_id = "CVE-2024-7099"
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    # 获取格式化的所有内容
    all_content = cve_assets.get_all_extra_content()
    
    if all_content:
        print("📄 所有额外文件的格式化内容:")
        print("-" * 80)
        # 限制输出长度
        preview = all_content[:500] + "\n\n... (内容过长，已截断)" if len(all_content) > 500 else all_content
        print(preview)
        print("-" * 80)
        print(f"\n总长度: {len(all_content)} 字符")
    else:
        print("ℹ️  没有额外文件内容")


def example_practical_use_case():
    """实际使用场景示例"""
    print("\n=== 示例 6: 实际使用场景 - 构建增强的分析上下文 ===\n")
    
    case_id = "CVE-2024-7099"
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    # 构建分析提示词
    prompt_parts = []
    prompt_parts.append("# CVE 漏洞分析")
    prompt_parts.append(f"\nCVE ID: {cve_assets.cve_id}")
    
    # 添加上下文信息
    context_files = cve_assets.get_extra_files_by_category('context')
    if context_files:
        prompt_parts.append("\n## 系统上下文信息")
        for ctx_file in context_files:
            prompt_parts.append(f"\n### {ctx_file.path.stem.replace('context_', '').replace('_', ' ').title()}")
            prompt_parts.append(ctx_file.read_text())
    
    # 添加配置信息
    config_files = cve_assets.get_extra_files_by_category('config')
    if config_files:
        prompt_parts.append("\n## 配置信息")
        for cfg_file in config_files:
            prompt_parts.append(f"\n### {cfg_file.path.stem.replace('config_', '').replace('_', ' ').title()}")
            if cfg_file.file_type == 'json':
                try:
                    import json
                    data = cfg_file.read_json()
                    prompt_parts.append(json.dumps(data, indent=2, ensure_ascii=False))
                except:
                    prompt_parts.append(cfg_file.read_text())
            else:
                prompt_parts.append(cfg_file.read_text())
    
    # 添加笔记
    note_files = cve_assets.get_extra_files_by_category('note')
    if note_files:
        prompt_parts.append("\n## 分析笔记")
        for note_file in note_files:
            prompt_parts.append(f"\n### {note_file.path.stem.replace('note_', '').replace('_', ' ').title()}")
            prompt_parts.append(note_file.read_text())
    
    final_prompt = "\n".join(prompt_parts)
    
    print("🤖 生成的增强分析提示词:")
    print("=" * 80)
    preview = final_prompt[:600] + "\n\n... (内容过长，已截断)" if len(final_prompt) > 600 else final_prompt
    print(preview)
    print("=" * 80)
    print(f"\n总长度: {len(final_prompt)} 字符")
    
    return final_prompt


def create_example_files(case_id: str = "case-template"):
    """为指定案例创建示例额外文件"""
    print(f"\n=== 创建示例文件到 projects/{case_id}/inputs/ ===\n")
    
    case_paths = resolve_case(case_id)
    inputs_dir = case_paths.inputs
    
    # 创建示例文件
    examples = [
        ("context_framework.md", """# 框架信息

## 使用的技术栈
- Spring Boot 2.3.4.RELEASE
- Spring Cloud Gateway 2.2.5.RELEASE
- PostgreSQL 12.3

## 架构说明
这是一个基于微服务的 API 网关系统。
"""),
        ("config_versions.json", """{
  "spring_boot": "2.3.4.RELEASE",
  "spring_cloud": "2.2.5.RELEASE",
  "java": "11"
}"""),
        ("note_analysis.md", """# 分析笔记

## 初步发现
1. 漏洞可能位于路由验证模块
2. 需要关注 SpEL 表达式处理
3. 考虑认证绕过场景

## 待验证点
- [ ] 检查输入验证
- [ ] 测试边界条件
- [ ] 验证权限控制
"""),
        ("doc_api.md", """# API 文档

## 端点说明
- `/api/route` - 路由配置接口
- `/api/auth` - 认证接口

## 认证方式
使用 JWT Token 进行认证。
"""),
    ]
    
    created_files = []
    for filename, content in examples:
        file_path = inputs_dir / filename
        if not file_path.exists():
            file_path.write_text(content, encoding='utf-8')
            created_files.append(filename)
            print(f"✅ 创建: {filename}")
        else:
            print(f"⚠️  已存在: {filename}")
    
    if created_files:
        print(f"\n✨ 成功创建 {len(created_files)} 个示例文件")
        print("\n现在可以运行其他示例函数来查看效果！")
    else:
        print("\n所有示例文件已存在")


def main():
    """运行所有示例"""
    print("=" * 80)
    print("额外输入文件功能使用示例")
    print("=" * 80)
    
    # 可选：创建示例文件（取消注释以创建）
    # create_example_files("case-template")
    
    try:
        # 运行各种示例
        example_basic_usage()
        example_filter_by_type()
        example_filter_by_category()
        example_search_by_name()
        example_get_all_content()
        example_practical_use_case()
        
        print("\n" + "=" * 80)
        print("✅ 所有示例运行完成！")
        print("=" * 80)
        
    except FileNotFoundError as e:
        print(f"\n❌ 错误: {e}")
        print("\n提示: 请确保案例目录存在，或使用 create_example_files() 创建示例文件")
    except Exception as e:
        print(f"\n❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()

