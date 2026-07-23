"""
命令行工具模块

提供完整的 CLI 功能
"""

from .core import ProviderRegistry
from .display import (
    console,
    display_provider_detail,
    display_providers_status,
    display_validation_result,
)


def _cli_list_providers(args) -> None:
    """CLI: 列出所有服务商"""
    validate = getattr(args, 'validate', False)
    if validate:
        console.print("[yellow]⏳ 正在验证API Keys...（这可能需要几秒钟）[/yellow]\n")
    display_providers_status(
        show_unavailable=not args.available_only,
        validate_keys=validate
    )


def _cli_show_provider(args) -> None:
    """CLI: 显示服务商详情"""
    display_provider_detail(args.name)


def _cli_test_provider(args) -> None:
    """CLI: 测试服务商连接"""
    display_validation_result(args.name)


def _cli_setup_wizard(args) -> None:
    """CLI: 交互式配置向导"""
    console.print("[bold cyan]󰜎 LLM 配置向导[/bold cyan]\n")

    # 显示当前状态
    console.print("[bold]📊 当前服务商状态:[/bold]")
    display_providers_status(show_unavailable=False)

    # 检查是否有可用服务商
    available = ProviderRegistry.list_available()

    if available:
        console.print(f"[green]󰄬 发现 {len(available)} 个可用服务商[/green]\n")
        console.print("[bold]推荐配置:[/bold]")
        console.print(f"  • 当前使用的服务商: {available[0].display_name}")
        console.print("  • 配置文件: config/keys.toml")
    else:
        console.print("[yellow]󰀪  没有可用的服务商，请配置 API Key[/yellow]\n")
        console.print("[bold]配置步骤:[/bold]")
        console.print("1. 复制 config/keys.example.toml 为 config/keys.toml")
        console.print("2. 编辑 config/keys.toml，填入你的 API Keys")
        console.print("3. 重新运行 python config.py setup 验证配置")


def main():
    """命令行入口"""
    import argparse

    parser = argparse.ArgumentParser(
        description="PureAutoCodeQL 配置管理工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 列出所有服务商
  python -m config.cli list

  # 仅显示可用的服务商
  python -m config.cli list --available-only

  # 显示服务商详情
  python -m config.cli show siliconflow

  # 测试服务商连接
  python -m config.cli test deepseek

  # 运行配置向导
  python -m config.cli setup
        """
    )

    subparsers = parser.add_subparsers(dest="command", help="可用命令")

    # list 命令
    parser_list = subparsers.add_parser("list", help="列出所有服务商")
    parser_list.add_argument(
        "--available-only", "-a",
        action="store_true",
        help="仅显示可用的服务商"
    )
    parser_list.add_argument(
        "--validate", "-v",
        action="store_true",
        help="真实验证API Key（较慢但准确）"
    )
    parser_list.set_defaults(func=_cli_list_providers)

    # show 命令
    parser_show = subparsers.add_parser("show", help="显示服务商详情")
    parser_show.add_argument("name", help="服务商名称")
    parser_show.set_defaults(func=_cli_show_provider)

    # test 命令
    parser_test = subparsers.add_parser("test", help="测试服务商连接")
    parser_test.add_argument("name", help="服务商名称")
    parser_test.set_defaults(func=_cli_test_provider)

    # setup 命令
    parser_setup = subparsers.add_parser("setup", help="运行配置向导")
    parser_setup.set_defaults(func=_cli_setup_wizard)

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        return

    if hasattr(args, "func"):
        args.func(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

