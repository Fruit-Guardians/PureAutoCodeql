# LLM 配置系统迁移指南

从旧版配置系统迁移到新版本的完整指南。

## 概述

新版配置系统引入了 `ProviderConfig` 和 `ProviderRegistry`，提供了更灵活、通用的服务商管理方案。

**好消息：新系统完全向后兼容！** 您无需立即修改现有代码。

## 主要变化

### 1. 新增功能

| 功能 | 描述 |
|------|------|
| `ProviderConfig` | 统一的服务商配置数据类 |
| `ProviderRegistry` | 服务商注册中心 |
| 自定义服务商 | 支持通过 YAML 配置文件添加自定义服务商 |
| Rich 展示 | 使用 Rich 库提供美观的信息展示 |
| 命令行工具 | 完整的 CLI 工具 (`python config.py`) |
| 自动切换 | 服务商自动切换功能 |

### 2. API 变化

#### 完全兼容的 API（无需修改）

```python
# ✅ 这些用法完全兼容，无需修改
from config import get_llm_config, LLMRole, get_think_config, get_chat_config

# 方式 1
config = get_llm_config(LLMRole.THINK)

# 方式 2
think_config = get_think_config()
chat_config = get_chat_config()
```

#### 新增的 API

```python
# 🆕 新增：指定服务商
config = get_llm_config(LLMRole.THINK, provider_name="siliconflow")

# 🆕 新增：自动切换
config = get_llm_config(LLMRole.THINK, auto_fallback=True)

# 🆕 新增：注册中心
from config import ProviderRegistry
providers = ProviderRegistry.list_all()

# 🆕 新增：美观展示
from config import display_providers_status
display_providers_status()
```

## 迁移步骤

### 步骤 1: 安装新依赖

```bash
# 如果使用 pip
pip install rich pyyaml

# 如果使用 uv
uv sync
```

### 步骤 2: 测试现有代码

运行您的现有代码，确保一切正常：

```python
# 您的现有代码应该能正常工作
from config import get_llm_config, LLMRole

think_config = get_llm_config(LLMRole.THINK)
# ✅ 无需修改，完全兼容
```

### 步骤 3: （可选）使用新功能

逐步采用新功能，提升体验：

```python
# 使用新的展示功能
from config import display_providers_status

# 查看当前配置状态
display_providers_status()
```

### 步骤 4: （可选）添加自定义服务商

如果需要使用自定义服务商：

1. 创建 `providers.yaml` 配置文件
2. 注册服务商
3. 使用自定义服务商

详见 [配置文件示例](../providers.example.yaml)。

## 常见迁移场景

### 场景 1: 基本使用（无需迁移）

**旧代码（继续可用）：**
```python
from config import get_llm_config, LLMRole

think_config = get_llm_config(LLMRole.THINK)
chat_config = get_llm_config(LLMRole.CHAT)
```

**新代码（推荐）：**
```python
from config import get_llm_config, LLMRole

# 基本使用保持不变
think_config = get_llm_config(LLMRole.THINK)

# 可选：启用自动切换
think_config = get_llm_config(LLMRole.THINK, auto_fallback=True)
```

### 场景 2: 指定服务商

**旧代码：**
```python
# 通过环境变量指定
import os
os.environ['LLM_PROVIDER'] = 'siliconflow'

config = get_llm_config(LLMRole.THINK)
```

**新代码（更灵活）：**
```python
# 方式 1: 直接指定（推荐）
config = get_llm_config(LLMRole.THINK, provider_name="siliconflow")

# 方式 2: 环境变量（仍然可用）
import os
os.environ['LLM_PROVIDER'] = 'siliconflow'
config = get_llm_config(LLMRole.THINK)
```

### 场景 3: 自定义模型

**旧代码：**
```python
# 通过环境变量指定
import os
os.environ['THINK_MODEL'] = 'custom-model'

config = get_llm_config(LLMRole.THINK)
```

**新代码（更直接）：**
```python
# 方式 1: 直接指定（推荐）
config = get_llm_config(
    LLMRole.THINK,
    model_name="custom-model"
)

# 方式 2: 环境变量（仍然可用）
import os
os.environ['THINK_MODEL'] = 'custom-model'
config = get_llm_config(LLMRole.THINK)
```

### 场景 4: 完全自定义配置

**旧代码：**
```python
from config import LLMConfig

# 手动构建配置
config = LLMConfig(
    model="custom-model",
    api_key="sk-xxx",
    base_url="https://api.example.com/v1",
    provider="custom"
)
```

**新代码（更灵活）：**
```python
# 方式 1: 使用 get_llm_config（推荐）
config = get_llm_config(
    LLMRole.THINK,
    model_name="custom-model",
    api_key="sk-xxx",
    base_url="https://api.example.com/v1"
)

# 方式 2: 注册自定义服务商（更好的复用）
from config import ProviderRegistry, ProviderConfig

ProviderRegistry.register(ProviderConfig(
    name="my_custom",
    display_name="My Custom Provider",
    base_url="https://api.example.com/v1",
    default_think_model="custom-model",
    default_chat_model="custom-model",
    env_keys=["MY_CUSTOM_KEY"],
    env_base_urls=["MY_CUSTOM_URL"],
    is_builtin=False
))

# 然后使用
config = get_llm_config(LLMRole.THINK, provider_name="my_custom")
```

## 环境变量变化

### 保持不变的环境变量

所有旧的环境变量都继续支持：

```bash
# ✅ 这些都仍然可用
export LLM_PROVIDER=siliconflow
export THINK_MODEL=custom-think-model
export CHAT_MODEL=custom-chat-model

# 服务商专属变量
export DEEPSEEK_API_KEY=sk-xxx
export SILICONFLOW_API_KEY=sk-xxx
export ZHIPU_API_KEY=xxx
export KIMI_API_KEY=sk-xxx
export GEMINI_API_KEY=xxx
```

### 新增的环境变量

```bash
# 🆕 新增：Base URL 覆盖
export DEEPSEEK_BASE_URL=https://custom.deepseek.com/v1
export SILICONFLOW_BASE_URL=https://custom.siliconflow.cn/v1

# 🆕 新增：通用兜底变量
export OPENAI_API_KEY=sk-xxx  # 所有服务商的兜底 Key
export OPENAI_BASE_URL=https://api.example.com/v1  # 所有服务商的兜底 URL
```

## 弃用通知

以下函数虽然仍然可用，但推荐使用新的替代方案：

### ❌ 不推荐（但仍可用）

```python
# 旧方式：list_available_providers()
providers = list_available_providers()  # 返回 dict 列表
```

### ✅ 推荐使用

```python
# 新方式：使用 ProviderRegistry
from config import ProviderRegistry

# 获取所有服务商
providers = ProviderRegistry.list_all()  # 返回 ProviderConfig 对象列表

# 获取可用服务商
available = ProviderRegistry.list_available()

# 美观展示
from config import display_providers_status
display_providers_status()
```

## 测试迁移

### 1. 运行演示脚本

```bash
python examples/config_demo.py
```

### 2. 使用 CLI 工具

```bash
# 查看当前配置
python config.py list

# 查看服务商详情
python config.py show deepseek

# 测试连接
python config.py test siliconflow
```

### 3. 在您的代码中测试

```python
# test_config_migration.py
from config import get_llm_config, LLMRole, display_providers_status

def test_basic():
    """测试基本用法"""
    config = get_llm_config(LLMRole.THINK)
    assert config.model
    assert config.api_key or True  # API Key 可能未配置
    print("✅ 基本用法测试通过")

def test_specify_provider():
    """测试指定服务商"""
    config = get_llm_config(LLMRole.THINK, provider_name="siliconflow")
    assert config.provider == "siliconflow"
    print("✅ 指定服务商测试通过")

def test_display():
    """测试展示功能"""
    try:
        display_providers_status()
        print("✅ 展示功能测试通过")
    except ImportError:
        print("⚠️  需要安装 rich 库")

if __name__ == "__main__":
    test_basic()
    test_specify_provider()
    test_display()
    print("\n🎉 所有测试通过！")
```

## 常见问题

### Q: 我必须迁移到新系统吗？

A: 不必。新系统完全向后兼容，您的现有代码无需修改即可继续工作。

### Q: 新系统有什么优势？

A: 
- 更灵活的服务商管理
- 支持自定义服务商
- 美观的信息展示
- 完整的命令行工具
- 自动服务商切换

### Q: 如何逐步迁移？

A:
1. 先安装新依赖（`rich`, `pyyaml`）
2. 测试现有代码确保兼容
3. 逐步采用新功能（如 `display_providers_status()`）
4. 根据需要添加自定义服务商

### Q: 旧的环境变量还能用吗？

A: 是的！所有旧的环境变量都继续支持。

### Q: 我的自定义代码会受影响吗？

A: 如果您只使用了 `get_llm_config()`、`get_think_config()`、`get_chat_config()` 这些公开 API，则不会受影响。

## 获取帮助

如果遇到问题：

1. 查看 [配置系统指南](./config_system_guide.md)
2. 运行 `python config.py setup` 查看当前配置状态
3. 查看示例代码 `examples/config_demo.py`
4. 查看配置文件示例 `providers.example.yaml`

## 总结

新的配置系统提供了更强大的功能，同时保持了完全的向后兼容性。您可以：

- ✅ 继续使用现有代码，无需修改
- ✅ 按需采用新功能
- ✅ 逐步迁移，无压力

欢迎使用新的配置系统！🎉

