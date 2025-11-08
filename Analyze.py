import argparse
import asyncio
from pathlib import Path
from typing import Optional
from core.orchestrator import AnalysisOrchestrator
from core.context import AnalysisConfig
from services.language_detector import LanguageDetector
from utils.case import resolve_case, discover_cve_assets
from utils.logger import setup_logging, get_logger, print_user_success, print_user_error, print_user_warning, print_user_info

# 初始化日志系统
setup_logging(level="INFO")
logger = get_logger(__name__)



async def run_case_analysis(
    case_id: str,
    refresh_intel: bool = False,
    stream: bool = False,
    output_file: Optional[str] = None
) -> None:
    """
    运行完整的案例分析

    Args:
        case_id: 案例ID，如 "CVE-2021-21985"
        refresh_intel: 是否强制刷新情报数据
        stream: 是否显示AI思考过程
        output_file: 自定义输出文件名
    """
    # 创建配置
    config = AnalysisConfig(
        show_thinking=stream,
        refresh_intel=refresh_intel,
        output_file=output_file  # None 表示使用时间戳目录
    )

    # 创建并运行编排器
    orchestrator = AnalysisOrchestrator(config)
    result = await orchestrator.analyze_case(case_id)

    # 显示结果摘要
    if result.success:
        print_user_success(f"\n🎉 分析成功完成！")
        print_user_info(f"📋 案例ID: {result.case_id}")
        print_user_info(f"💻 编程语言: {result.language}")
        if result.execution_time:
            print_user_info(f"⏱️  执行时间: {result.execution_time:.2f}秒")

        if result.is_complete():
            print_user_success("✅ 所有分析步骤都成功完成")
        else:
            print_user_warning("⚠️  部分分析步骤未完成")

        if config.output_file:
            print_user_info(f"📄 分析报告已保存到: {config.output_file}")
        else:
            logger.info(f"分析结果已保存到时间戳目录")
    else:
        print_user_error(f"\n❌ 分析失败: {result.error_message}")
        logger.error(f"案例分析失败: {result.error_message}")


def list_available_cases() -> None:
    """列出所有可用的案例"""
    projects_dir = Path("projects")
    if not projects_dir.exists():
        print_user_error("projects目录不存在")
        logger.error("projects目录不存在")
        return

    # 排除特殊文件夹：模板目录和镜像目录
    excluded_dirs = {"case-template", "python_kb"}
    case_dirs = [
        d for d in projects_dir.iterdir()
        if d.is_dir() and d.name not in excluded_dirs
    ]

    if not case_dirs:
        print_user_error("没有找到任何案例")
        logger.warning("没有找到任何案例")
        return

    print("📁 可用的案例:")
    for case_dir in sorted(case_dirs):
        try:
            case_paths = resolve_case(case_dir.name)
            cve_assets = discover_cve_assets(case_paths)
            print(f"  📂 {case_dir.name} -> {cve_assets.cve_id}")
        except Exception as e:
            print_user_warning(f"  📂 {case_dir.name} -> (解析失败: {e})")
            logger.warning(f"解析案例 {case_dir.name} 失败: {e}", exc_info=True)


async def validate_case(case_id: str) -> bool:
    """验证案例是否有效"""
    try:
        case_paths = resolve_case(case_id)
        cve_assets = discover_cve_assets(case_paths)

        print_user_success(f"✅ 案例 {case_id} 验证通过")
        print_user_info(f"   📁 根目录: {case_paths.root}")
        print_user_info(f"   🎯 CVE ID: {cve_assets.cve_id}")
        print_user_info(f"   📄 JSON文件: {cve_assets.json_path}")
        if cve_assets.diff_path:
            print_user_info(f"   🔄 Diff文件: {cve_assets.diff_path}")
        else:
            print_user_warning(f"   ⚠️  没有Diff文件")

        # 检测语言
        detector = LanguageDetector()
        language = detector.detect_language(case_paths)
        print_user_info(f"   💻 检测语言: {language}")
        logger.info(f"案例 {case_id} 验证通过，语言: {language}")

        return True
    except Exception as e:
        print_user_error(f"❌ 案例 {case_id} 验证失败: {e}")
        logger.error(f"案例 {case_id} 验证失败: {e}", exc_info=True)
        return False



def parse_arguments() -> argparse.Namespace:
    """解析命令行参数"""
    parser = argparse.ArgumentParser(
        description="PureAutoCodeQL - 基于AI的自动化漏洞分析工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s --case CVE-2021-21985                    # 分析单个案例
  %(prog)s --case CVE-2021-21985 --stream          # 显示AI思考过程
  %(prog)s --list                                  # 列出所有可用案例
  %(prog)s --validate CVE-2021-21985               # 验证案例有效性
        """
    )

    # 主要参数组
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--case",
        type=str,
        help="分析单个案例 (例如: CVE-2021-21985)"
    )
    group.add_argument(
        "--list",
        action="store_true",
        help="列出所有可用的案例"
    )
    group.add_argument(
        "--validate",
        type=str,
        metavar="CASE_ID",
        help="验证指定案例是否有效"
    )

    # 可选参数
    parser.add_argument(
        "--refresh-intel",
        action="store_true",
        help="强制刷新情报数据（不使用缓存）"
    )
    parser.add_argument(
        "--no-stream",
        dest="stream",
        action="store_false",
        help="禁用AI思考过程显示"
    )
    parser.add_argument(
        "--output",
        type=str,
        metavar="FILE",
        help="指定输出文件名 (默认: output.md)"
    )

    parser.set_defaults(stream=True)

    return parser.parse_args()


async def main() -> None:
    """主函数"""
    args = parse_arguments()

    try:
        if args.list:
            list_available_cases()

        elif args.validate:
            await validate_case(args.validate)

        elif args.case:
            print(f"🚀 PureAutoCodeQL 启动")
            print(f"🎯 分析案例: {args.case}")
            print(f"💭 AI思考过程: {'开启' if args.stream else '关闭'}")
            print(f"🔄 刷新情报: {'是' if args.refresh_intel else '否'}")
            print(f"📄 输出文件: {args.output or 'output.md'}")
            print("-" * 50)

            await run_case_analysis(
                case_id=args.case,
                refresh_intel=args.refresh_intel,
                stream=args.stream,
                output_file=args.output
            )

    except KeyboardInterrupt:
        print_user_warning("\n⚠️  分析被用户中断")
        logger.warning("分析被用户中断")
    except Exception as e:
        print_user_error(f"\n❌ 执行出错: {e}")
        logger.exception(f"执行出错: {e}")


if __name__ == "__main__":
    asyncio.run(main())
