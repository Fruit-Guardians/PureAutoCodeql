"""
PureAuto - 纯粹的AI漏洞分析工具

基于多智能体架构的自动化漏洞分析工具，使用 AI 技术进行代码安全分析。
"""

import argparse
import asyncio
from pathlib import Path
from typing import Optional
from core.orchestrator import AnalysisOrchestrator
from core.context import AnalysisConfig
from services.language_detector import LanguageDetector
from utils.case import resolve_case, discover_cve_assets
from utils.logger import setup_logging, get_logger, print_user_success, print_user_error, print_user_warning, print_user_info
from config import list_available_providers, LLMRole, get_llm_config

# 初始化日志系统
setup_logging(level="INFO")
logger = get_logger(__name__)


async def run_case_analysis(
    case_id: str,
    refresh_intel: bool = False,
    stream: bool = False,
    output_file: Optional[str] = None,
    provider: Optional[str] = None,
    think_model: Optional[str] = None,
    chat_model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    language: Optional[str] = None,
) -> None:
    """
    运行完整的案例分析

    Args:
        case_id: 案例ID，如 "CVE-2021-21985"
        refresh_intel: 是否强制刷新情报数据
        stream: 是否显示AI思考过程
        output_file: 自定义输出文件名
        provider: 模型提供商名称（deepseek/siliconflow/zhipu/kimi/gemini）
        think_model: 推理模型名称（覆盖默认模型）
        chat_model: 对话模型名称（覆盖默认模型）
        api_key: API Key（覆盖环境变量）
        base_url: Base URL（覆盖环境变量）
        language: 强制指定编程语言（跳过自动检测）
    """
    # 创建配置
    config = AnalysisConfig(
        show_thinking=stream,
        refresh_intel=refresh_intel,
        output_file=output_file,
        llm_provider=provider,
        think_model=think_model,
        chat_model=chat_model,
        api_key=api_key,
        base_url=base_url,
    )

    # 显示模型提供商信息
    try:
        chat_config = get_llm_config(
            LLMRole.CHAT,
            provider_name=provider,
            model_name=chat_model,
            api_key=api_key,
            base_url=base_url
        )
        think_config = get_llm_config(
            LLMRole.THINK,
            provider_name=provider,
            model_name=think_model,
            api_key=api_key,
            base_url=base_url
        )

        provider_display = provider or chat_config.provider or "环境变量配置"
        print_user_info(f"🤖 使用模型提供商: {provider_display}")
        print_user_info(f"   💭 推理模型: {think_config.model}")
        print_user_info(f"   💬 对话模型: {chat_config.model}")
        if base_url:
            print_user_info(f"   🔗 Base URL: {base_url}")
        logger.info(f"使用模型提供商: {provider_display}, 推理模型: {think_config.model}, 对话模型: {chat_config.model}")
    except Exception as e:
        print_user_error(f"❌ 无法配置模型: {e}")
        logger.error(f"模型配置失败: {e}", exc_info=True)
        return

    # 创建并运行编排器
    orchestrator = AnalysisOrchestrator(config)
    result = await orchestrator.analyze_case(case_id, language=language)

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

        if result.output_directory:
            print_user_info(f"📁 分析报告已保存到: {result.output_directory}")
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

    # 排除特殊文件夹
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
            file_type = "Diff" if cve_assets.diff_path.suffix == ".diff" else "Patch"
            print_user_info(f"   🔄 {file_type}文件: {cve_assets.diff_path}")
        else:
            print_user_warning(f"   ⚠️  没有Diff/Patch文件")

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
        description="PureAuto - 基于AI的自动化漏洞分析工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s --case CVE-2021-21985                    # 分析案例
  %(prog)s --case CVE-2021-21985 --stream           # 显示AI思考过程
  %(prog)s --case CVE-2021-21985 --provider deepseek  # 指定模型提供商
  %(prog)s --list                                   # 列出所有可用案例
  %(prog)s --validate CVE-2021-21985                # 验证案例有效性
  %(prog)s --list-providers                         # 列出可用的模型提供商
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
    group.add_argument(
        "--list-providers",
        action="store_true",
        help="列出所有可用的模型提供商及其状态"
    )

    # 可选参数
    parser.add_argument(
        "--refresh-intel",
        action="store_true",
        help="强制刷新情报数据（不使用缓存）"
    )
    parser.add_argument(
        "--stream",
        action="store_true",
        default=True,
        help="显示AI思考过程（默认启用）"
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
        help="指定输出文件名"
    )
    parser.add_argument(
        "--provider",
        type=str,
        metavar="PROVIDER",
        help="指定模型提供商 (deepseek/siliconflow/zhipu/kimi/gemini)"
    )
    parser.add_argument(
        "--model",
        type=str,
        metavar="MODEL",
        help="指定模型名称（同时用于推理和对话）"
    )
    parser.add_argument(
        "--think-model",
        type=str,
        metavar="MODEL",
        dest="think_model",
        help="指定推理模型名称"
    )
    parser.add_argument(
        "--chat-model",
        type=str,
        metavar="MODEL",
        dest="chat_model",
        help="指定对话模型名称"
    )
    parser.add_argument(
        "--api-key",
        type=str,
        metavar="KEY",
        dest="api_key",
        help="指定API Key，覆盖环境变量"
    )
    parser.add_argument(
        "--base-url",
        type=str,
        metavar="URL",
        dest="base_url",
        help="指定Base URL，覆盖环境变量"
    )
    parser.add_argument(
        "--language",
        type=str,
        metavar="LANG",
        dest="language",
        default=None,
        help="指定编程语言（默认自动检测）"
    )

    return parser.parse_args()


def main() -> None:
    """主函数"""
    args = parse_arguments()

    # 处理 --model 参数
    think_model = args.think_model
    chat_model = args.chat_model
    if hasattr(args, 'model') and args.model:
        if not think_model:
            think_model = args.model
        if not chat_model:
            chat_model = args.model

    if args.list:
        list_available_cases()
    elif args.validate:
        asyncio.run(validate_case(args.validate))
    elif args.list_providers:
        list_available_providers()
    elif args.case:
        asyncio.run(run_case_analysis(
            case_id=args.case,
            refresh_intel=args.refresh_intel,
            stream=args.stream,
            output_file=args.output,
            provider=args.provider,
            think_model=think_model,
            chat_model=chat_model,
            api_key=args.api_key,
            base_url=args.base_url,
            language=args.language,
        ))


if __name__ == "__main__":
    main()
