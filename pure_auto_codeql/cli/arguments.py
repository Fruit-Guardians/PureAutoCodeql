"""CLI argument parsing, subcommand normalization, and provider listing."""

import argparse
import sys
from typing import Optional, Sequence

from pure_auto_codeql.configuration import list_available_providers

SUBCOMMANDS = {
    "analyze",
    "doctor",
    "import",
    "list",
    "md",
    "models",
    "providers",
    "serve",
    "validate",
}


def _normalize_cli_args(argv: Sequence[str]) -> list[str]:
    """Translate new subcommands to the existing flag-based parser."""
    args = list(argv)
    if not args or args[0] not in SUBCOMMANDS:
        return args

    command, rest = args[0], args[1:]
    if command == "analyze":
        if rest and not rest[0].startswith("-"):
            return ["--case", rest[0], *rest[1:]]
        return rest
    if command == "doctor":
        return ["--doctor", *rest]
    if command == "import":
        if rest and not rest[0].startswith("-"):
            return ["--import-project", rest[0], *rest[1:]]
        return rest
    if command == "list":
        return ["--list", *rest]
    if command == "md":
        if rest and not rest[0].startswith("-"):
            return ["--md-file", rest[0], *rest[1:]]
        return rest
    if command == "models":
        return ["--list-models", *rest]
    if command == "providers":
        return ["--list-providers", *rest]
    if command == "serve":
        return ["--serve", *rest]
    if command == "validate":
        if rest and not rest[0].startswith("-"):
            return ["--validate", rest[0], *rest[1:]]
        return rest
    return args


def parse_arguments(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    """解析命令行参数"""
    normalized_argv = _normalize_cli_args(sys.argv[1:] if argv is None else argv)
    parser = argparse.ArgumentParser(
        description="PureAutoCodeQL - 基于AI的自动化漏洞分析工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s --case CVE-2021-21985                    # 分析已导入的案例
  %(prog)s analyze --case CVE-2021-21985            # 子命令形式（兼容新CLI）
  %(prog)s --case "C:\\Targets\\java\\CVE-2023-51444"  # Java项目：自动导入+建库+分析
  %(prog)s --case CVE-2021-21985 --no-stream       # 禁用AI思考过程显示
  %(prog)s --doctor                                # 检查本机运行环境
  %(prog)s serve                                   # 启动本地 API 服务
  %(prog)s --import-project "C:\\Targets\\CVE-2023-51444" --import-language java  # 仅导入Java项目
  %(prog)s --md-file vulnerability.md              # 从MD文件直接生成CodeQL
  %(prog)s --md-file vulnerability.md --src-path /path/to/source  # 从MD文件生成source点分析报告
  %(prog)s --md-file vulnerability.md --provider deepseek  # 指定模型提供商
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
        "--md-file",
        type=str,
        metavar="FILE",
        help="从指定的MD文件直接生成CodeQL查询 (例如: vulnerability.md)"
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
    group.add_argument(
        "--list-models",
        action="store_true",
        help="列出硅基流动的所有可用模型"
    )
    group.add_argument(
        "--import-project",
        type=str,
        metavar="PATH",
        help="导入外部CVE目录 (例如: C:\\Targets\\CVE-2025-54381)"
    )
    group.add_argument(
        "--doctor",
        action="store_true",
        help="检查本机运行环境、依赖工具和模型配置"
    )
    group.add_argument(
        "--serve",
        action="store_true",
        help="启动 FastAPI 服务"
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
    parser.add_argument(
        "--provider",
        type=str,
        metavar="PROVIDER",
        help="指定模型提供商 (deepseek/siliconflow/zhipu/kimi/gemini 或 keys.toml 中定义的自定义提供商)，覆盖环境变量 LLM_PROVIDER"
    )
    parser.add_argument(
        "--model",
        type=str,
        metavar="MODEL",
        help="指定模型名称（同时用于推理和对话），覆盖默认模型和环境变量"
    )
    parser.add_argument(
        "--think-model",
        type=str,
        metavar="MODEL",
        dest="think_model",
        help="指定推理模型名称，覆盖默认模型和环境变量"
    )
    parser.add_argument(
        "--chat-model",
        type=str,
        metavar="MODEL",
        dest="chat_model",
        help="指定对话模型名称，覆盖默认模型和环境变量"
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
        help="指定Base URL，覆盖环境变量（例如: https://api.siliconflow.cn/v1）"
    )
    parser.add_argument(
        "--database-path",
        type=str,
        metavar="PATH",
        dest="database_path",
        help="指定CodeQL数据库路径（用于--md-file模式）"
    )
    parser.add_argument(
        "--language",
        type=str,
        metavar="LANG",
        dest="language",
        default=None,
        help="指定编程语言（默认: java 或自动检测）"
    )
    parser.add_argument(
        "--src-path",
        type=str,
        metavar="PATH",
        dest="src_path",
        help="指定源代码路径（用于--md-file模式生成source点分析报告）"
    )
    parser.add_argument(
        "--enable-error-tidy",
        action="store_true",
        help="启用错误整理功能（实验性）"
    )
    parser.add_argument(
        "--requirement",
        type=str,
        help="覆盖自动生成的 CodeQL 查询需求",
    )
    parser.add_argument(
        "--max-codeql-rounds",
        type=int,
        default=5,
        metavar="N",
        help="CodeQL 单次生成后的最大修复轮数（默认: 5）",
    )
    parser.add_argument(
        "--task-timeout",
        type=int,
        default=3600,
        metavar="SECONDS",
        help="完整分析任务超时秒数（默认: 3600）",
    )
    parser.add_argument(
        "--disable-cve-analysis",
        action="store_false",
        dest="enable_cve_analysis",
        help="跳过 CVE 语义分析",
    )
    parser.add_argument(
        "--disable-sink-analysis",
        action="store_false",
        dest="enable_sink_analysis",
        help="跳过 Sink 分析（同时应关闭依赖步骤）",
    )
    parser.add_argument(
        "--disable-source-analysis",
        action="store_false",
        dest="enable_source_analysis",
        help="跳过 Source 分析",
    )
    parser.add_argument(
        "--disable-path-analysis",
        action="store_false",
        dest="enable_path_analysis",
        help="跳过路径流转分析",
    )
    parser.add_argument(
        "--disable-codeql-generation",
        action="store_false",
        dest="enable_codeql_generation",
        help="跳过 CodeQL 生成与执行",
    )
    parser.add_argument(
        "--disable-path-selection",
        action="store_false",
        dest="enable_path_selection",
        help="跳过 CodeQL 结果路径筛选",
    )
    parser.add_argument(
        "--enable-breakpoint-recovery",
        action="store_true",
        help="查询为空时启用断流恢复",
    )
    parser.add_argument(
        "--enable-source-sink-fallback",
        action="store_true",
        help="启用 Source-Sink 回退查询（所有常规 CodeQL 重试失败后）",
    )
    parser.add_argument(
        "--source-sink-only",
        action="store_true",
        help="仅使用 Source-Sink 回退代理生成不含中间路径的查询（测试模式，仅用于 --md-file）",
    )
    parser.add_argument(
        "--enable-sink-source-verification",
        action="store_true",
        help="启用 Sink/Source 验证功能（实验性）",
    )
    parser.add_argument(
        "--verification-retry-max",
        type=int,
        metavar="N",
        dest="verification_retry_max",
        default=3,
        help="Sink/Source 验证失败时的最大重试次数（默认: 3）",
    )
    parser.add_argument(
        "--verification-timeout",
        type=int,
        metavar="SECONDS",
        dest="verification_timeout",
        default=30,
        help="单个验证查询的超时时间（秒，默认: 30）",
    )
    parser.add_argument(
        "--import-case-id",
        type=str,
        dest="import_case_id",
        help="搭配 --import-project 使用，自定义案例ID"
    )
    parser.add_argument(
        "--import-overwrite",
        action="store_true",
        dest="import_overwrite",
        help="搭配 --import-project 使用，若案例已存在则覆盖"
    )
    parser.add_argument(
        "--import-language",
        type=str,
        dest="import_language",
        help="搭配 --import-project 使用，指定语言：python/java/cpp（默认自动检测）"
    )
    parser.add_argument(
        "--import-skip-codeql",
        action="store_true",
        dest="import_skip_codeql",
        help="搭配 --import-project 使用，跳过 CodeQL 数据库创建"
    )
    parser.add_argument(
        "--import-build-command",
        type=str,
        dest="import_build_command",
        help="搭配 --import-project 使用，指定 C/C++ 构建命令（Java/Python项目不需要）"
    )
    parser.add_argument(
        "--import-build-script",
        type=str,
        dest="import_build_script",
        help="搭配 --import-project 使用，指定 C/C++ 构建脚本路径（Java/Python项目不需要）"
    )
    parser.add_argument(
        "--import-build-dir",
        type=str,
        dest="import_build_workdir",
        help="搭配 --import-project 使用，设置 C/C++ 构建命令工作目录（Java/Python项目不需要）"
    )

    parser.add_argument(
        "--prev-ql-file",
        type=str,
        metavar="FILE",
        dest="prev_ql_file",
        help="指定上一次生成的 CodeQL 查询文件（用于测试 Source-Sink Fallback）"
    )
    parser.add_argument(
        "--host",
        type=str,
        dest="serve_host",
        help="搭配 serve/--serve 使用，覆盖 API_HOST"
    )
    parser.add_argument(
        "--port",
        type=int,
        dest="serve_port",
        help="搭配 serve/--serve 使用，覆盖 API_PORT"
    )
    parser.add_argument(
        "--reload",
        action="store_true",
        dest="serve_reload",
        help="搭配 serve/--serve 使用，启用 uvicorn reload"
    )

    parser.set_defaults(
        stream=True,
        enable_cve_analysis=True,
        enable_sink_analysis=True,
        enable_source_analysis=True,
        enable_path_analysis=True,
        enable_codeql_generation=True,
        enable_path_selection=True,
    )

    return parser.parse_args(normalized_argv)


def list_providers() -> None:
    """列出所有可用的模型提供商"""
    providers = list_available_providers()

    print("\n" + "=" * 80)
    print("📋 可用的模型提供商:")
    print("=" * 80)

    for provider in providers:
        print(f"\n🔹 {provider['display_name']} ({provider['name']})")
        print(f"   状态: {provider['status']}")
        print(f"   推理模型: {provider['think_model']}")
        print(f"   对话模型: {provider['chat_model']}")
        print(f"   Base URL: {provider['base_url']}")
        if not provider['has_api_key']:
            print("   ⚠️  需要设置 API Key 环境变量")

    print("\n" + "=" * 80)
    print("💡 使用方式:")
    print("   python Analyze.py --case CVE-2021-21985 --provider deepseek")
    print("   export LLM_PROVIDER=deepseek  # 或通过环境变量设置")
    print("=" * 80 + "\n")


def _resolve_model_args(args: argparse.Namespace) -> tuple[Optional[str], Optional[str]]:
    """Return the effective think/chat model names for a parsed CLI command."""
    return args.think_model or args.model, args.chat_model or args.model
