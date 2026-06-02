"""
演示：如何使用 keys.toml 中定义的自定义提供商

这个脚本展示了：
1. 如何从 keys.toml 读取自定义提供商
2. 如何在代码中使用自定义提供商
3. 如何验证自定义提供商的配置
"""

import sys
from pathlib import Path

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from pure_auto_codeql.configuration import (
    ProviderRegistry,
    get_llm_config,
    LLMRole,
    display_providers_status,
    display_provider_detail,
)


def demo_list_all_providers():
    """演示：列出所有提供商（包括自定义的）"""
    print("\n" + "=" * 80)
    print("演示 1: 列出所有提供商")
    print("=" * 80)
    
    all_providers = ProviderRegistry.list_all()
    
    for provider in all_providers:
        provider_type = "内置" if provider.is_builtin else "自定义"
        print(f"\n[{provider_type}] {provider.display_name} ({provider.name})")
        print(f"  推理模型: {provider.default_think_model}")
        print(f"  对话模型: {provider.default_chat_model}")
        print(f"  Base URL: {provider.get_base_url()}")
        print(f"  已配置: {'✅' if provider.is_configured() else '❌'}")


def demo_use_custom_provider():
    """演示：使用自定义提供商获取配置"""
    print("\n" + "=" * 80)
    print("演示 2: 使用自定义提供商")
    print("=" * 80)
    
    # 检查是否有自定义提供商
    custom_providers = [p for p in ProviderRegistry.list_all() if not p.is_builtin]
    
    if not custom_providers:
        print("\n⚠️  当前没有定义自定义提供商")
        print("请在 config/keys.toml 中添加自定义提供商，例如：")
        print("""
[[custom_providers]]
name = "my_ollama"
display_name = "本地 Ollama"
api_key = "ollama"
base_url = "http://localhost:11434/v1"
think_model = "deepseek-r1:latest"
chat_model = "qwen2.5:latest"
description = "本地 Ollama 模型"
""")
        return
    
    # 使用第一个自定义提供商
    custom_provider = custom_providers[0]
    print(f"\n使用自定义提供商: {custom_provider.display_name} ({custom_provider.name})")
    
    # 获取配置
    try:
        chat_config = get_llm_config(
            LLMRole.CHAT,
            provider_name=custom_provider.name
        )
        
        print(f"\n✅ 成功获取配置:")
        print(f"  模型: {chat_config.model}")
        print(f"  API Key: {chat_config.api_key[:10]}..." if len(chat_config.api_key) > 10 else f"  API Key: {chat_config.api_key}")
        print(f"  Base URL: {chat_config.base_url}")
        print(f"  提供商: {chat_config.provider}")
        
    except Exception as e:
        print(f"\n❌ 获取配置失败: {e}")


def demo_override_with_custom_model():
    """演示：使用自定义提供商，但覆盖模型名称"""
    print("\n" + "=" * 80)
    print("演示 3: 覆盖自定义提供商的模型")
    print("=" * 80)
    
    custom_providers = [p for p in ProviderRegistry.list_all() if not p.is_builtin]
    
    if not custom_providers:
        print("\n⚠️  需要先定义自定义提供商")
        return
    
    custom_provider = custom_providers[0]
    
    # 使用自定义提供商，但指定不同的模型
    try:
        config = get_llm_config(
            LLMRole.CHAT,
            provider_name=custom_provider.name,
            model_name="my-custom-model-v2"  # 覆盖默认模型
        )
        
        print(f"\n✅ 使用提供商 '{custom_provider.name}'")
        print(f"  默认模型: {custom_provider.default_chat_model}")
        print(f"  覆盖后模型: {config.model}")
        
    except Exception as e:
        print(f"\n❌ 失败: {e}")


def demo_rich_display():
    """演示：使用 Rich 美化显示所有提供商"""
    print("\n" + "=" * 80)
    print("演示 4: Rich 美化显示")
    print("=" * 80)
    
    print("\n正在显示所有提供商的状态（包括自定义的）...\n")
    
    # 显示所有提供商的状态
    display_providers_status(validate_keys=False)
    
    # 如果有自定义提供商，显示详情
    custom_providers = [p for p in ProviderRegistry.list_all() if not p.is_builtin]
    if custom_providers:
        print("\n")
        custom_provider = custom_providers[0]
        display_provider_detail(custom_provider.name)


def demo_command_line_usage():
    """演示：命令行使用方法"""
    print("\n" + "=" * 80)
    print("演示 5: 命令行使用方法")
    print("=" * 80)
    
    custom_providers = [p for p in ProviderRegistry.list_all() if not p.is_builtin]
    
    if not custom_providers:
        print("\n⚠️  需要先在 keys.toml 中定义自定义提供商")
        return
    
    custom_provider = custom_providers[0]
    
    print(f"\n假设你定义了自定义提供商: {custom_provider.name}")
    print("\n在 Analyze.py 中使用的命令示例：\n")
    
    print("1. 使用自定义提供商分析案例:")
    print(f"   python Analyze.py --case CVE-2021-21985 --provider {custom_provider.name}")
    
    print("\n2. 使用自定义提供商，但覆盖模型:")
    print(f"   python Analyze.py --case CVE-2021-21985 --provider {custom_provider.name} --model custom-model-v2")
    
    print("\n3. 从 MD 文件生成 CodeQL:")
    print(f"   python Analyze.py --md-file vuln.md --provider {custom_provider.name}")
    
    print("\n4. 查看所有提供商（包括自定义的）:")
    print("   python Analyze.py --list-providers")
    print("   或")
    print("   python -m config list")


def main():
    """主函数"""
    print("\n" + "=" * 80)
    print("自定义提供商使用演示")
    print("=" * 80)
    
    print("\n这个演示展示了如何在 keys.toml 中定义自定义提供商，")
    print("以及如何在 Analyze.py 和代码中使用它们。\n")
    
    # 运行所有演示
    demo_list_all_providers()
    demo_use_custom_provider()
    demo_override_with_custom_model()
    demo_rich_display()
    demo_command_line_usage()
    
    print("\n" + "=" * 80)
    print("演示完成！")
    print("=" * 80)
    
    print("\n📚 更多信息:")
    print("  - 配置文件: config/keys.toml")
    print("  - 配置模板: config/keys.example.toml")
    print("  - 使用指南: config/使用指南_自定义模型.md")
    print("  - 系统文档: config/README.md")
    print()


if __name__ == "__main__":
    main()

