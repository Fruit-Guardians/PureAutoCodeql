"""Java项目导入和自动建库示例

演示如何导入Java CVE项目并自动创建CodeQL数据库。

Java项目使用 --build-mode=none，不需要编译追踪。
"""

from pathlib import Path

from utils.project_importer import import_project


def demo_java_import():
    """演示Java项目导入"""
    
    # 示例1: 最简单的Java项目导入
    # 只需要指定源目录和语言，系统会自动：
    # 1. 推断CVE ID
    # 2. 整理目录结构
    # 3. 使用 --build-mode=none 创建CodeQL数据库
    
    print("=" * 80)
    print("示例 1: 导入Java项目（自动建库）")
    print("=" * 80)
    
    source_path = r"C:\Users\bxx\Desktop\qwb_targets1\targets\java\CVE-2023-51444"
    
    print(f"\n源目录: {source_path}")
    print("语言: java")
    print("构建模式: --build-mode=none (自动)")
    print("\n开始导入...\n")
    
    try:
        result = import_project(
            source_path=source_path,
            language="java",
            overwrite=True,  # 如果已存在则覆盖
            create_codeql_db=True,  # 自动创建数据库
        )
        
        print("\n✅ 导入成功!")
        print(f"   案例ID: {result.case_id}")
        print(f"   目标路径: {result.target_path}")
        print(f"   语言: {result.language}")
        print(f"   元数据文件: {', '.join(result.metadata_files)}")
        print(f"   CodeQL数据库: {'已创建' if result.codeql_created else '创建失败'}")
        
        if result.codeql_error:
            print(f"   错误: {result.codeql_error}")
            
        print(f"\n数据库位置: {result.target_path}/db/{result.language}")
        print(f"日志文件: {result.target_path}/db/codeql.log")
        
    except Exception as e:
        print(f"\n❌ 导入失败: {e}")
    
    print("\n" + "=" * 80)
    print("完成!")
    print("=" * 80)


def demo_java_import_without_codeql():
    """演示仅导入Java项目，跳过CodeQL数据库创建"""
    
    print("\n" + "=" * 80)
    print("示例 2: 仅导入Java项目（跳过建库）")
    print("=" * 80)
    
    source_path = r"C:\Users\bxx\Desktop\qwb_targets1\targets\java\CVE-2023-51444"
    
    print(f"\n源目录: {source_path}")
    print("跳过建库: True")
    print("\n开始导入...\n")
    
    try:
        result = import_project(
            source_path=source_path,
            case_id="CVE-2023-51444",  # 显式指定ID
            language="java",
            overwrite=True,
            create_codeql_db=False,  # 跳过数据库创建
        )
        
        print("\n✅ 导入成功!")
        print(f"   案例ID: {result.case_id}")
        print(f"   目标路径: {result.target_path}")
        print(f"   语言: {result.language}")
        print(f"   CodeQL数据库: 已跳过")
        
    except Exception as e:
        print(f"\n❌ 导入失败: {e}")
    
    print("\n" + "=" * 80)
    print("完成!")
    print("=" * 80)


def show_command_examples():
    """显示命令行使用示例"""
    
    print("\n" + "=" * 80)
    print("命令行使用示例")
    print("=" * 80)
    
    print("\n🚀 推荐方式（最简单）：")
    print("   一条命令完成：自动导入 -> 建库 -> AI分析")
    print("   python Analyze.py --case \"C:\\Targets\\java\\CVE-2023-51444\"")
    print("   ")
    print("   ✅ 无需任何额外参数！")
    print("   ✅ 自动使用 --build-mode=none")
    print("   ✅ 建库成功后立即启动AI分析")
    
    print("\n" + "-" * 80)
    print("\n高级用法：")
    
    print("\n1. 仅导入Java项目（自动建库，不分析）:")
    print("   python Analyze.py --import-project \"C:\\Targets\\java\\CVE-2023-51444\" \\")
    print("       --import-language java \\")
    print("       --import-overwrite")
    
    print("\n2. 仅导入Java项目（跳过建库）:")
    print("   python Analyze.py --import-project \"C:\\Targets\\java\\CVE-2023-51444\" \\")
    print("       --import-language java \\")
    print("       --import-skip-codeql \\")
    print("       --import-overwrite")
    
    print("\n3. 分析已导入的项目:")
    print("   python Analyze.py --case CVE-2023-51444")
    
    print("\n" + "=" * 80)


if __name__ == "__main__":
    # 显示命令行示例
    show_command_examples()
    
    # 如果要运行实际导入，取消下面的注释
    # demo_java_import()
    # demo_java_import_without_codeql()

