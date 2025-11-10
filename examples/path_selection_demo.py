"""路径选择服务使用示例

演示如何使用PathSelectionService从CodeQL查询结果中选择最匹配CVE的路径
"""

import asyncio
from pathlib import Path

from services.path_selection import PathSelectionService
from config import get_chat_config


async def demo_basic_usage():
    """基础用法示例"""
    
    print("="*60)
    print("路径选择服务 - 基础用法示例")
    print("="*60)
    
    # 1. 准备输入路径
    output_dir = Path("output/analysis_output_20251109_131403")
    output_md = output_dir / "output.md"
    result_json = output_dir / "result_20251109_131312.json"
    source_root = Path("projects/CVE-2025-54381/source_code/BentoML-1.4.10/BentoML-1.4.10")
    
    # 检查文件是否存在
    if not output_md.exists():
        print(f"❌ output.md不存在: {output_md}")
        return
    
    if not result_json.exists():
        print(f"❌ result.json不存在: {result_json}")
        return
    
    print(f"✓ output.md: {output_md}")
    print(f"✓ result.json: {result_json}")
    print(f"✓ source_root: {source_root}")
    print()
    
    # 2. 初始化服务
    print("初始化路径选择服务...")
    llm_config = get_chat_config()
    service = PathSelectionService(llm_config, language="python")
    print("✓ 服务初始化完成")
    print()
    
    # 3. 选择最佳路径
    print("开始路径选择流程...")
    result = await service.select_best_paths(
        output_md_path=str(output_md),
        result_json_path=str(result_json),
        source_root=str(source_root),
        top_k=3,
        enable_clustering=True
    )
    print("✓ 路径选择完成")
    print()
    
    # 4. 输出结果
    print("="*60)
    print("选择结果")
    print("="*60)
    print(f"总路径数: {result.all_paths_count}")
    print(f"选中路径数: {len(result.selected_paths)}")
    print(f"语言: {result.language}")
    print()
    
    print("选择理由:")
    print(result.selection_reasoning)
    print()
    
    print("验证摘要:")
    print(f"  所有路径有效: {result.verification_summary['all_valid']}")
    print(f"  有效路径数: {result.verification_summary['valid_count']}/{result.verification_summary['total_verified']}")
    if result.verification_summary['issues']:
        print("  发现的问题:")
        for issue in result.verification_summary['issues']:
            print(f"    - {issue}")
    print()
    
    print("覆盖率分析:")
    if result.coverage_analysis.get('sink_coverage'):
        print(f"  Sink覆盖: {', '.join(result.coverage_analysis['sink_coverage'])}")
    if result.coverage_analysis.get('source_coverage'):
        print(f"  Source覆盖: {', '.join(result.coverage_analysis['source_coverage'])}")
    print()
    
    # 5. 输出详细路径信息
    print("="*60)
    print("选中的路径详情")
    print("="*60)
    for i, path in enumerate(result.selected_paths, 1):
        print(f"\n路径 {i}:")
        
        selection_info = path.get("selection_info", {})
        print(f"  置信度: {selection_info.get('confidence', 'N/A')}")
        print(f"  路径长度: {path.get('path_length', 'N/A')} 步")
        
        source_loc = path.get("source_location", {})
        print(f"  Source: {source_loc.get('file', 'N/A')}:{source_loc.get('startLine', 'N/A')}")
        
        sink_loc = path.get("sink_location", {})
        print(f"  Sink: {sink_loc.get('file', 'N/A')}:{sink_loc.get('startLine', 'N/A')}")
        
        print(f"  选择原因: {selection_info.get('reason', 'N/A')}")
        
        verification = path.get("verification", {})
        print(f"  验证状态: {'✅ 有效' if verification.get('is_valid') else '❌ 无效'}")
    
    # 6. 保存Markdown报告
    report_path = output_dir / "path_selection_report.md"
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(result.to_markdown())
    print(f"\n✓ 报告已保存: {report_path}")
    
    # 7. 保存JSON结果
    import json
    json_path = output_dir / "path_selection_result.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(result.to_dict(), f, ensure_ascii=False, indent=2)
    print(f"✓ JSON结果已保存: {json_path}")


async def demo_different_languages():
    """演示不同语言的路径选择"""
    
    print("\n" + "="*60)
    print("多语言支持示例")
    print("="*60)
    
    languages = ["python", "java", "c"]
    
    for lang in languages:
        print(f"\n{lang.upper()} 语言适配器:")
        
        llm_config = get_chat_config()
        service = PathSelectionService(llm_config, language=lang)
        
        # 获取危险API列表
        adapter = service.path_enricher.adapter
        dangerous_apis = adapter.get_dangerous_apis()
        
        print(f"  危险API数量: {len(dangerous_apis)}")
        print(f"  示例API: {', '.join(dangerous_apis[:5])}")


async def demo_without_clustering():
    """演示禁用聚类的情况"""
    
    print("\n" + "="*60)
    print("禁用聚类示例")
    print("="*60)
    
    output_dir = Path("output/analysis_output_20251109_131403")
    output_md = output_dir / "output.md"
    result_json = output_dir / "result_20251109_131312.json"
    source_root = Path("projects/CVE-2025-54381/source_code/BentoML-1.4.10/BentoML-1.4.10")
    
    if not all([output_md.exists(), result_json.exists()]):
        print("示例文件不存在，跳过")
        return
    
    llm_config = get_chat_config()
    service = PathSelectionService(llm_config, language="python")
    
    print("选择路径（禁用聚类）...")
    result = await service.select_best_paths(
        output_md_path=str(output_md),
        result_json_path=str(result_json),
        source_root=str(source_root),
        top_k=3,
        enable_clustering=False  # 禁用聚类
    )
    
    print(f"✓ 完成（处理了 {result.all_paths_count} 条路径）")


async def main():
    """主函数"""
    
    # 运行基础用法示例
    try:
        await demo_basic_usage()
    except Exception as e:
        print(f"基础用法示例失败: {e}")
        import traceback
        traceback.print_exc()
    
    # 运行多语言示例
    try:
        await demo_different_languages()
    except Exception as e:
        print(f"多语言示例失败: {e}")
    
    # 运行禁用聚类示例
    try:
        await demo_without_clustering()
    except Exception as e:
        print(f"禁用聚类示例失败: {e}")


if __name__ == "__main__":
    # 运行示例
    asyncio.run(main())

