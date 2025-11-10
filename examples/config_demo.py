#!/usr/bin/env python3
"""
LLM 配置系统演示脚本

展示新配置系统的各种功能和用法。
"""

from config import (
    get_llm_config,
    LLMRole,
    ProviderRegistry,
    ProviderConfig,
    display_providers_status,
    display_provider_detail,
    validate_provider,
)


def demo_basic_usage():
    """演示基本用法"""
    print("\n" + "=" * 80)
    print("📚 演示 1: 基本用法")
    print("=" * 80)
    
    # 获取默认配置
    think_config = get_llm_config(LLMRole.THINK)
    print(f"\n推理模型配置:")
    print(f"  - Provider: {think_config.provider}")
    print(f"  - Model: {think_config.model}")
    print(f"  - Base URL: {think_config.base_url}")
    print(f"  - Has API Key: {bool(think_config.api_key)}")
    
    chat_config = get_llm_config(LLMRole.CHAT)
    print(f"\n对话模型配置:")
    print(f"  - Provider: {chat_config.provider}")
    print(f"  - Model: {chat_config.model}")


def demo_specify_provider():
    """演示指定服务商"""
    print("\n" + "=" * 80)
    print("📚 演示 2: 指定服务商")
    print("=" * 80)
    
    # 使用硅基流动
    config = get_llm_config(LLMRole.THINK, provider_name="siliconflow")
    print(f"\n硅基流动配置:")
    print(f"  - Model: {config.model}")
    print(f"  - Base URL: {config.base_url}")


def demo_custom_model():
    """演示自定义模型"""
    print("\n" + "=" * 80)
    print("📚 演示 3: 自定义模型")
    print("=" * 80)
    
    # 使用自定义模型
    config = get_llm_config(
        LLMRole.THINK,
        provider_name="siliconflow",
        model_name="Qwen/Qwen3-Coder-480B-A35B-Instruct"
    )
    print(f"\n自定义模型配置:")
    print(f"  - Provider: {config.provider}")
    print(f"  - Model: {config.model}")


def demo_registry():
    """演示注册中心功能"""
    print("\n" + "=" * 80)
    print("📚 演示 4: 注册中心功能")
    print("=" * 80)
    
    # 列出所有服务商
    providers = ProviderRegistry.list_all()
    print(f"\n已注册服务商数量: {len(providers)}")
    
    # 获取单个服务商信息
    provider = ProviderRegistry.get("deepseek")
    if provider:
        print(f"\nDeepSeek 信息:")
        print(f"  - 显示名称: {provider.display_name}")
        print(f"  - 推理模型: {provider.default_think_model}")
        print(f"  - 对话模型: {provider.default_chat_model}")
        print(f"  - 已配置: {provider.is_configured()}")
        print(f"  - 可达: {provider.is_reachable()}")
    
    # 列出可用服务商
    available = ProviderRegistry.list_available()
    print(f"\n可用服务商: {[p.display_name for p in available]}")


def demo_display_functions():
    """演示展示函数（需要 rich 库）"""
    print("\n" + "=" * 80)
    print("📚 演示 5: 美观信息展示")
    print("=" * 80)
    
    try:
        # 显示所有服务商状态
        print("\n显示所有服务商状态:")
        display_providers_status()
        
        # 显示单个服务商详情
        print("\n显示 SiliconFlow 详情:")
        display_provider_detail("siliconflow")
        
    except ImportError:
        print("\n⚠️  需要安装 rich 库才能使用美观展示功能")
        print("   安装命令: pip install rich")


def demo_validation():
    """演示验证功能"""
    print("\n" + "=" * 80)
    print("📚 演示 6: 验证服务商配置")
    print("=" * 80)
    
    # 验证服务商
    result = validate_provider("deepseek")
    print(f"\nDeepSeek 验证结果:")
    print(f"  - 成功: {result['success']}")
    print(f"  - 有 API Key: {result['has_api_key']}")
    print(f"  - 可达: {result['is_reachable']}")
    if 'error' in result:
        print(f"  - 错误: {result['error']}")


def demo_custom_provider():
    """演示注册自定义服务商"""
    print("\n" + "=" * 80)
    print("📚 演示 7: 注册自定义服务商")
    print("=" * 80)
    
    # 注册自定义服务商
    custom = ProviderConfig(
        name="demo_custom",
        display_name="演示自定义服务商",
        base_url="http://localhost:8000/v1",
        default_think_model="custom-think",
        default_chat_model="custom-chat",
        env_keys=["DEMO_API_KEY"],
        env_base_urls=["DEMO_BASE_URL"],
        description="这是一个演示用的自定义服务商",
        is_builtin=False,
    )
    
    ProviderRegistry.register(custom)
    print(f"\n✅ 已注册自定义服务商: {custom.display_name}")
    
    # 使用自定义服务商
    try:
        config = get_llm_config(LLMRole.THINK, provider_name="demo_custom")
        print(f"\n自定义服务商配置:")
        print(f"  - Provider: {config.provider}")
        print(f"  - Model: {config.model}")
        print(f"  - Base URL: {config.base_url}")
    except Exception as e:
        print(f"\n⚠️  使用自定义服务商时出错: {e}")


def demo_auto_fallback():
    """演示自动切换功能"""
    print("\n" + "=" * 80)
    print("📚 演示 8: 自动服务商切换")
    print("=" * 80)
    
    # 启用自动切换
    config = get_llm_config(
        LLMRole.THINK,
        auto_fallback=True  # 当首选服务商不可用时自动切换
    )
    
    print(f"\n自动切换结果:")
    print(f"  - Provider: {config.provider}")
    print(f"  - Model: {config.model}")


def main():
    """主函数"""
    print("\n🚀 LLM 配置系统演示")
    print("=" * 80)
    
    demos = [
        demo_basic_usage,
        demo_specify_provider,
        demo_custom_model,
        demo_registry,
        demo_display_functions,
        demo_validation,
        demo_custom_provider,
        demo_auto_fallback,
    ]
    
    for demo in demos:
        try:
            demo()
        except Exception as e:
            print(f"\n❌ 演示出错: {e}")
            import traceback
            traceback.print_exc()
    
    print("\n" + "=" * 80)
    print("✅ 演示完成！")
    print("=" * 80)


if __name__ == "__main__":
    main()

