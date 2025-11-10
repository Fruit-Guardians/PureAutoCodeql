"""
Rich 美观信息展示模块

包含所有使用 Rich 库的展示函数
"""

import sys
from typing import Any

from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich.tree import Tree
from rich import box

from .core import ProviderRegistry

# Windows 兼容性：设置 UTF-8 编码
if sys.platform == "win32":
    import io
    if sys.stdout.encoding != 'utf-8':
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    if sys.stderr.encoding != 'utf-8':
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

console = Console(force_terminal=True, legacy_windows=False)


def display_providers_status(show_unavailable: bool = True, validate_keys: bool = False) -> None:
    """使用 Rich 表格展示所有服务商状态
    
    Args:
        show_unavailable: 是否显示不可用的服务商
        validate_keys: 是否真实验证API Key（较慢但准确）
    """
    providers = ProviderRegistry.list_all()
    
    if not providers:
        console.print("[yellow]⚠️  没有已注册的服务商[/yellow]")
        return
    
    # 创建表格
    table = Table(
        title="🤖 LLM 服务商状态一览",
        box=box.ROUNDED,
        show_header=True,
        header_style="bold cyan",
        title_style="bold magenta",
        expand=False,
    )
    
    # 添加列
    table.add_column("服务商", style="cyan", no_wrap=True)
    table.add_column("推理模型", style="blue")
    table.add_column("对话模型", style="blue")
    table.add_column("API Key", justify="center")
    table.add_column("网络", justify="center")
    table.add_column("状态", justify="center")
    table.add_column("类型", justify="center")
    
    # 添加数据行
    for provider in providers:
        has_key = provider.is_configured()
        reachable = provider.is_reachable()
        emoji, status_text = provider.get_status(validate_key=validate_keys)
        
        if not show_unavailable and not (has_key and reachable):
            continue
        
        status_color = "green" if emoji == "✅" else ("yellow" if emoji == "⚠️" else "red")
        
        table.add_row(
            provider.display_name,
            provider.default_think_model,
            provider.default_chat_model,
            "✓" if has_key else "✗",
            "✓" if reachable else "✗",
            f"[{status_color}]{emoji} {status_text}[/{status_color}]",
            "内置" if provider.is_builtin else "自定义",
        )
    
    console.print(table)
    console.print()


def display_provider_detail(provider_name: str) -> None:
    """展示单个服务商的详细信息"""
    provider = ProviderRegistry.get(provider_name)
    
    if not provider:
        console.print(f"[red]❌ 服务商 '{provider_name}' 不存在[/red]")
        return
    
    # 获取状态
    has_key = provider.is_configured()
    reachable = provider.is_reachable()
    emoji, status_text = provider.get_status()
    status_color = "green" if emoji == "✅" else ("yellow" if emoji == "⚠️" else "red")
    
    # 创建信息面板
    info_text = f"""
[bold cyan]服务商名称:[/bold cyan] {provider.display_name}
[bold cyan]内部标识:[/bold cyan] {provider.name}
[bold cyan]类型:[/bold cyan] {"内置" if provider.is_builtin else "自定义"}
[bold cyan]状态:[/bold cyan] [{status_color}]{emoji} {status_text}[/{status_color}]

[bold cyan]配置信息:[/bold cyan]
  • Base URL: {provider.get_base_url()}
  • API Key 配置: {"✓ 已配置" if has_key else "✗ 未配置"}
  • 网络可达性: {"✓ 可达" if reachable else "✗ 不可达"}

[bold cyan]默认模型:[/bold cyan]
  • 推理模型 (THINK): {provider.default_think_model}
  • 对话模型 (CHAT): {provider.default_chat_model}

[bold cyan]环境变量配置:[/bold cyan]
  • API Key: {", ".join(provider.env_keys) if provider.env_keys else "无"}
  • Base URL: {", ".join(provider.env_base_urls) if provider.env_base_urls else "无"}
"""
    
    if provider.description:
        info_text += f"\n[bold cyan]描述:[/bold cyan]\n  {provider.description}\n"
    
    panel = Panel(
        info_text.strip(),
        title=f"📋 {provider.display_name} 详细信息",
        border_style="cyan",
        expand=False,
    )
    
    console.print(panel)
    
    # 显示可用模型列表
    if provider.available_models:
        console.print("\n[bold cyan]📚 可用模型列表:[/bold cyan]")
        tree = Tree("🌳 模型树", guide_style="dim")
        for model in provider.available_models:
            is_default = model in [provider.default_think_model, provider.default_chat_model]
            marker = "⭐" if is_default else "📦"
            tree.add(f"{marker} {model}")
        console.print(tree)
    
    console.print()


def display_all_providers() -> None:
    """展示所有服务商的完整信息"""
    providers = ProviderRegistry.list_all()
    
    if not providers:
        console.print("[yellow]⚠️  没有已注册的服务商[/yellow]")
        return
    
    console.print("[bold magenta]🌐 所有已注册的服务商[/bold magenta]\n")
    
    for provider in providers:
        display_provider_detail(provider.name)


def validate_provider(provider_name: str) -> dict[str, Any]:
    """验证服务商配置是否正确"""
    provider = ProviderRegistry.get(provider_name)
    
    if not provider:
        return {
            "success": False,
            "error": f"服务商 '{provider_name}' 不存在",
        }
    
    has_key = provider.is_configured()
    reachable = provider.is_reachable()
    
    result = {
        "success": has_key and reachable,
        "provider": provider.name,
        "display_name": provider.display_name,
        "has_api_key": has_key,
        "is_reachable": reachable,
        "base_url": provider.get_base_url(),
        "api_key_configured": has_key,
    }
    
    if not has_key:
        result["error"] = "API Key 未配置"
    elif not reachable:
        result["error"] = "服务不可达"
    
    return result


def display_validation_result(provider_name: str) -> None:
    """展示服务商验证结果"""
    result = validate_provider(provider_name)
    
    if "error" in result and not result["success"]:
        console.print(f"[red]❌ 验证失败: {result.get('error', '未知错误')}[/red]")
        return
    
    if result["success"]:
        console.print(f"[green]✅ 服务商 '{result['display_name']}' 配置正确且可用[/green]")
    else:
        console.print(f"[yellow]⚠️  服务商 '{result['display_name']}' 配置有问题: {result.get('error')}[/yellow]")

