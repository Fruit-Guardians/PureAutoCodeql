"""
额外输入文件功能 - 简单演示

展示如何使用 inputs 目录中的额外文件。
"""

from pathlib import Path
from utils.case import resolve_case, discover_cve_assets


def demo_basic():
    """基础演示"""
    print("\n" + "=" * 80)
    print("额外输入文件功能演示")
    print("=" * 80 + "\n")
    
    # 使用一个现有的案例
    case_id = "CVE-2024-7099"
    
    try:
        # 解析案例
        case_paths = resolve_case(case_id)
        cve_assets = discover_cve_assets(case_paths)
        
        print(f"✅ 案例: {case_id}")
        print(f"📁 Inputs 目录: {case_paths.inputs}\n")
        
        # 检查额外文件
        if cve_assets.has_extra_files():
            print(f"📂 发现 {len(cve_assets.extra_files)} 个额外文件:\n")
            
            # 列出所有文件
            for i, extra_file in enumerate(cve_assets.extra_files, 1):
                print(f"{i}. {extra_file.path.name}")
            
            print("\n" + "-" * 80)
            print("文件内容预览:")
            print("-" * 80 + "\n")
            
            # 显示每个文件的内容
            for extra_file in cve_assets.extra_files:
                print(f"\n📄 文件: {extra_file.path.name}")
                print("─" * 40)
                try:
                    content = extra_file.read_text()
                    # 显示前 300 个字符
                    preview = content[:300] + "..." if len(content) > 300 else content
                    print(preview)
                except Exception as e:
                    print(f"⚠️  读取错误: {e}")
            
            print("\n" + "=" * 80)
            print("获取所有内容（用于 LLM）:")
            print("=" * 80 + "\n")
            
            # 获取格式化的所有内容
            all_content = cve_assets.get_all_extra_content()
            preview = all_content[:500] + "\n\n... (内容过长，已截断)" if len(all_content) > 500 else all_content
            print(preview)
            print(f"\n总长度: {len(all_content)} 字符")
            
        else:
            print("ℹ️  此案例没有额外文件")
            print("\n💡 提示: 您可以在以下目录添加文件:")
            print(f"   {case_paths.inputs}")
            print("\n   示例:")
            print(f"   echo '系统信息' > {case_paths.inputs}/说明.txt")
        
        print("\n" + "=" * 80)
        print("✅ 演示完成")
        print("=" * 80 + "\n")
        
    except FileNotFoundError as e:
        print(f"❌ 错误: {e}\n")
        print("💡 提示: 请确保案例目录存在")
        print("   可用案例列表: python Analyze.py --list")


def create_demo_files(case_id: str = "case-template"):
    """为指定案例创建演示文件"""
    print(f"\n为案例 '{case_id}' 创建演示文件...\n")
    
    try:
        case_paths = resolve_case(case_id)
        inputs_dir = case_paths.inputs
        
        # 创建几个演示文件
        files_to_create = [
            ("系统说明.txt", "这是一个基于 Spring Boot 的微服务系统\n使用了 API Gateway 进行路由"),
            ("版本信息.md", "# 版本信息\n\n- Spring Boot: 2.3.4\n- Java: 11"),
            ("分析记录.txt", "初步分析发现了注入漏洞\n需要进一步验证权限控制"),
        ]
        
        created = []
        for filename, content in files_to_create:
            file_path = inputs_dir / filename
            if not file_path.exists():
                file_path.write_text(content, encoding='utf-8')
                created.append(filename)
                print(f"✅ 创建: {filename}")
            else:
                print(f"⚠️  已存在: {filename}")
        
        if created:
            print(f"\n✨ 成功创建 {len(created)} 个文件")
            print(f"\n现在可以运行: python examples/simple_extra_files_demo.py")
        else:
            print("\n所有文件已存在")
        
    except FileNotFoundError as e:
        print(f"❌ 错误: {e}")
        print("请确保案例目录存在")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1:
        if sys.argv[1] == "--create":
            # 创建演示文件
            case = sys.argv[2] if len(sys.argv) > 2 else "case-template"
            create_demo_files(case)
        else:
            # 查看指定案例
            demo_basic()
    else:
        # 默认演示
        demo_basic()

