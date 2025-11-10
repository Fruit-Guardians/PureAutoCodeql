# PureAutoCodeQL 配置系统

统一的 LLM 配置管理系统，支持内置和自定义服务商，零代码修改快速部署。

---

## 📁 文件结构

```
config/
├── __init__.py           # 统一导出接口
├── core.py               # 核心配置逻辑（数据类、注册表、内置提供商）
├── display.py            # Rich 美化显示功能
├── cli.py                # 命令行工具
├── legacy.py             # 向后兼容函数
├── __main__.py           # 支持 python -m config 运行
├── keys.toml             # 配置文件（用户修改，不提交到 Git）
├── keys.example.toml     # 配置模板（提交到仓库）
└── README.md             # 本文档
```

---

## 🚀 快速开始

### 步骤 1: 创建配置文件

```bash
# 复制配置模板
cp config/keys.example.toml config/keys.toml

# 编辑配置文件
vim config/keys.toml
```

### 步骤 2: 配置 API Keys

**内置服务商（5个）：**

```toml
[builtin_keys]
deepseek = "sk-your-deepseek-key"
siliconflow = "sk-your-siliconflow-key"
zhipu = "your-zhipu-key"
kimi = "sk-your-kimi-key"
gemini = "your-gemini-key"
```

**自定义服务商（可选）：**

```toml
[[custom_providers]]
name = "my_api"                      # 命令行中使用的名字
display_name = "我的 API"
api_key = "your-api-key"
base_url = "https://api.example.com/v1"
think_model = "model-for-reasoning"  # 推理模型
chat_model = "model-for-chat"        # 对话模型
description = "描述信息（可选）"
```

### 步骤 3: 使用

```bash
# 使用内置提供商
python Analyze.py --case CVE-2021-21985 --provider deepseek

# 使用自定义提供商
python Analyze.py --case CVE-2021-21985 --provider my_api

# 查看所有可用提供商
python -m config list
```

---

## 🎯 核心特性

- ✅ **零代码修改** - 只需编辑 `keys.toml` 即可配置
- ✅ **内置 5 个服务商** - DeepSeek, SiliconFlow, 智谱GLM, Kimi, Gemini
- ✅ **自定义服务商** - 支持任意 OpenAI 兼容 API
- ✅ **100% 向后兼容** - 现有代码无需修改
- ✅ **Rich 美化显示** - 清晰的状态展示
- ✅ **API 验证** - 实际调用 API 验证密钥有效性
- ✅ **配置优先级** - 命令行 > 环境变量 > keys.toml > 默认值
- ✅ **模块化架构** - 清晰的代码组织

---

## 📖 配置文件详解

### 1. 内置服务商 API Keys

```toml
[builtin_keys]
deepseek = "sk-xxxxx"       # DeepSeek API Key
siliconflow = "sk-xxxxx"    # 硅基流动 API Key
zhipu = "xxxxx.xxxxx"       # 智谱 GLM API Key
kimi = "sk-xxxxx"           # Kimi (月之暗面) API Key
gemini = "xxxxx"            # Google Gemini API Key
```

**获取 API Key：**
- DeepSeek: https://platform.deepseek.com/api_keys
- SiliconFlow: https://cloud.siliconflow.cn/account/ak
- 智谱GLM: https://open.bigmodel.cn/usercenter/apikeys
- Kimi: https://platform.moonshot.cn/console/api-keys
- Gemini: https://makersuite.google.com/app/apikey

### 2. 自定义服务商

```toml
[[custom_providers]]
name = "provider_name"           # 必填：唯一标识符（小写字母、数字、下划线）
display_name = "显示名称"        # 必填：用户友好的名称
api_key = "your-api-key"         # 必填：API Key
base_url = "https://api.com/v1"  # 必填：API 基础地址
think_model = "model-name"       # 必填：推理模型（用于 CodeQL 生成）
chat_model = "model-name"        # 必填：对话模型（用于分析任务）
description = "描述信息"         # 可选：提供商描述

# 高级参数（可选）
[custom_providers.custom_params]
max_retries = 5                  # 最大重试次数
timeout = 60                     # 请求超时时间（秒）
```

**支持多个自定义提供商：**

```toml
[[custom_providers]]
name = "local_ollama"
# ... 配置 ...

[[custom_providers]]
name = "openai_proxy"
# ... 配置 ...

[[custom_providers]]
name = "another_api"
# ... 配置 ...
```

### 3. 全局设置

```toml
[settings]
default_provider = "deepseek"           # 默认提供商
default_think_model = "model-name"      # 覆盖默认推理模型
default_chat_model = "model-name"       # 覆盖默认对话模型
```

---

## 💻 使用方法

### 在 Analyze.py 中使用

```bash
# 基础使用
python Analyze.py --case CVE-2021-21985 --provider my_api

# 覆盖模型
python Analyze.py --case CVE-2021-21985 \
  --provider my_api \
  --think-model custom-think-model \
  --chat-model custom-chat-model

# 同时使用同一个模型
python Analyze.py --case CVE-2021-21985 \
  --provider my_api \
  --model unified-model

# 临时覆盖 API Key
python Analyze.py --case CVE-2021-21985 \
  --provider my_api \
  --api-key temporary-key

# 从 MD 文件生成 CodeQL
python Analyze.py --md-file vulnerability.md --provider my_api

# 生成 Source 分析报告
python Analyze.py --md-file vuln.md --src-path /path/to/src --provider my_api
```

### 在代码中使用

```python
from config import get_llm_config, LLMRole

# 获取默认配置
chat_config = get_llm_config(LLMRole.CHAT)
think_config = get_llm_config(LLMRole.THINK)

# 指定提供商
config = get_llm_config(LLMRole.CHAT, provider_name="my_api")

# 覆盖模型
config = get_llm_config(
    LLMRole.CHAT,
    provider_name="my_api",
    model_name="custom-model"
)

# 完全自定义
config = get_llm_config(
    LLMRole.CHAT,
    provider_name="my_api",
    model_name="custom-model",
    api_key="custom-key",
    base_url="https://custom.api.com/v1"
)

print(f"Model: {config.model}")
print(f"API Key: {config.api_key}")
print(f"Base URL: {config.base_url}")
```

### 便捷函数

```python
from config import get_chat_config, get_think_config

# 直接获取对话模型配置
chat_config = get_chat_config()

# 直接获取推理模型配置
think_config = get_think_config()
```

---

## 🛠️ 命令行工具

### 查看所有提供商

```bash
python -m config list
```

输出：
```
                             🤖 LLM 服务商状态一览
┌────────────────────────┬─────────┬────────┬─────────┬──────┬────────┬────────┐
│ 服务商                 │ 推理... │ 对话..  │ API Key │ 网络 │  状态  │  类型  │
├────────────────────────┼─────────┼────────┼─────────┼──────┼────────┼────────┤
│ DeepSeek               │ deepse… │ deeps… │    ✓    │  ✓   │   ✅   │  内置  │
│ SiliconFlow (硅基流动) │ deepse… │ Pro/d… │    ✓    │  ✓   │   ✅   │  内置  │
│ 我的自定义 API         │ gpt-4   │ gpt-3… │    ✓    │  ✓   │   ✅   │ 自定义 │
└────────────────────────┴─────────┴────────┴─────────┴──────┴────────┴────────┘
```

### 验证 API Keys

```bash
python -m config list --validate
```

会实际调用 API 验证密钥是否有效。

### 查看特定提供商详情

```bash
python -m config show my_api
```

输出：
```
╭──────── 📋 我的自定义 API 详细信息 ────────╮
│ 服务商名称: 我的自定义 API                 │
│ 内部标识: my_api                           │
│ 类型: 自定义                               │
│ 状态: ✅ 已配置                            │
│                                            │
│ 配置信息:                                  │
│   • Base URL: https://api.example.com/v1   │
│   • API Key 配置: ✓ 已配置                 │
│   • 网络可达性: ✓ 可达                     │
│                                            │
│ 默认模型:                                  │
│   • 推理模型 (THINK): model-think          │
│   • 对话模型 (CHAT): model-chat            │
╰────────────────────────────────────────────╯
```

### 完整命令帮助

```bash
python -m config --help
```

---

## 🔧 配置优先级

配置按以下优先级应用（从高到低）：

1. **命令行参数** - `--provider`, `--api-key`, `--base-url`, `--model`, 等
2. **环境变量** - `LLM_PROVIDER`, `DEEPSEEK_API_KEY`, 等
3. **keys.toml [settings]** - `default_provider`, `default_think_model`, 等
4. **keys.toml API Keys** - `[builtin_keys]` 和 `[[custom_providers]]`
5. **系统默认值** - 代码中定义的默认值

**示例：**

```toml
# keys.toml
[builtin_keys]
deepseek = "key-from-file"

[settings]
default_provider = "deepseek"
```

```bash
# 环境变量
export DEEPSEEK_API_KEY="key-from-env"

# 命令行
python Analyze.py --case CVE-XXX --api-key "key-from-cli"
```

**最终使用：** `key-from-cli` （命令行优先级最高）

---

## 📚 实际示例

### 示例 1: 配置本地 Ollama

```toml
[[custom_providers]]
name = "local"
display_name = "本地 Ollama"
api_key = "not-needed"
base_url = "http://localhost:11434/v1"
think_model = "deepseek-r1:latest"
chat_model = "qwen2.5:latest"
description = "本地 Ollama 模型服务"
```

使用：
```bash
python Analyze.py --case CVE-2021-21985 --provider local
```

### 示例 2: 配置 OpenAI 代理

```toml
[[custom_providers]]
name = "openai"
display_name = "OpenAI 代理"
api_key = "sk-xxxxxxxxxx"
base_url = "https://api.openai.com/v1"
think_model = "gpt-4-turbo"
chat_model = "gpt-3.5-turbo"
description = "通过代理访问 OpenAI"
```

使用：
```bash
python Analyze.py --case CVE-2021-21985 --provider openai
```

### 示例 3: 多服务商切换

```toml
[[custom_providers]]
name = "fast"
display_name = "快速模型"
api_key = "key-1"
base_url = "https://fast-api.com/v1"
think_model = "small-model"
chat_model = "small-model"

[[custom_providers]]
name = "accurate"
display_name = "精确模型"
api_key = "key-2"
base_url = "https://premium-api.com/v1"
think_model = "large-model"
chat_model = "large-model"
```

使用：
```bash
# 快速测试
python Analyze.py --case CVE-XXX --provider fast

# 正式分析
python Analyze.py --case CVE-XXX --provider accurate
```

### 示例 4: 设置默认提供商

```toml
[[custom_providers]]
name = "my_favorite"
display_name = "常用模型"
api_key = "key"
base_url = "https://api.example.com/v1"
think_model = "model-1"
chat_model = "model-2"

[settings]
default_provider = "my_favorite"
```

使用（无需指定 --provider）：
```bash
python Analyze.py --case CVE-2021-21985
```

---

## 🔍 API 参考

### 核心类

#### `LLMRole` (枚举)

```python
class LLMRole(Enum):
    THINK = "think"  # 推理模型，用于 CodeQL 生成
    CHAT = "chat"    # 对话模型，用于分析任务
```

#### `LLMConfig` (数据类)

```python
@dataclass
class LLMConfig:
    model: str                    # 模型名称
    api_key: str                  # API Key
    base_url: str                 # API 基础地址
    temperature: float = 0        # 温度参数
    streaming: bool = True        # 是否流式输出
    max_tokens: Optional[int]     # 最大 token 数
    max_retries: int = 3          # 最大重试次数
    provider: Optional[str]       # 提供商名称
```

#### `ProviderConfig` (数据类)

```python
@dataclass
class ProviderConfig:
    name: str                           # 提供商标识
    display_name: str                   # 显示名称
    base_url: str                       # API 基础地址
    default_think_model: str            # 默认推理模型
    default_chat_model: str             # 默认对话模型
    env_keys: list[str]                 # 环境变量名列表
    env_base_urls: list[str]            # Base URL 环境变量
    available_models: Optional[list]    # 可用模型列表
    description: str = ""               # 描述
    is_builtin: bool = True             # 是否内置
    custom_params: dict = {}            # 自定义参数
```

### 核心函数

#### `get_llm_config()`

获取 LLM 配置（主要函数）。

```python
def get_llm_config(
    role: LLMRole,                      # THINK 或 CHAT
    provider_name: Optional[str] = None,  # 提供商名称
    model_name: Optional[str] = None,     # 模型名称
    api_key: Optional[str] = None,        # API Key
    base_url: Optional[str] = None,       # Base URL
    auto_fallback: bool = False           # 自动回退
) -> LLMConfig
```

**示例：**
```python
# 获取默认配置
config = get_llm_config(LLMRole.CHAT)

# 指定提供商
config = get_llm_config(LLMRole.CHAT, provider_name="deepseek")

# 完全自定义
config = get_llm_config(
    LLMRole.CHAT,
    provider_name="my_api",
    model_name="custom-model",
    api_key="custom-key"
)
```

#### `get_chat_config()` / `get_think_config()`

便捷函数。

```python
def get_chat_config() -> LLMConfig
def get_think_config() -> LLMConfig
```

**示例：**
```python
chat_config = get_chat_config()
think_config = get_think_config()
```

#### `ProviderRegistry`

提供商注册表（单例）。

```python
# 列出所有提供商
providers = ProviderRegistry.list_all()

# 获取特定提供商
provider = ProviderRegistry.get("deepseek")

# 检查是否存在
exists = ProviderRegistry.has("my_api")
```

### 显示函数

```python
from config import display_providers_status, display_provider_detail

# 显示所有提供商状态
display_providers_status(validate_keys=False)

# 显示特定提供商详情
display_provider_detail("my_api")
```

### 向后兼容函数

```python
from config import (
    list_available_providers,
    get_llm_config_by_provider,
    list_siliconflow_models
)

# 列出提供商（返回字典列表）
providers = list_available_providers()

# 按提供商获取配置
config = get_llm_config_by_provider("deepseek", LLMRole.CHAT)

# 列出硅基流动模型
list_siliconflow_models()
```

---

## ❓ 常见问题

### Q: keys.toml 在哪里？
**A:** `config/keys.toml`。如果不存在，复制 `config/keys.example.toml`。

### Q: 修改 keys.toml 后需要重启吗？
**A:** 不需要，每次运行时自动重新加载。

### Q: keys.toml 会被提交到 Git 吗？
**A:** 不会，它已经在 `.gitignore` 中，你的 API Keys 是安全的。

### Q: 自定义提供商名称有什么要求？
**A:** 使用小写字母、数字和下划线，例如：`my_ollama`, `cloud_api_1`。

### Q: 可以定义多少个自定义提供商？
**A:** 无限制，使用多个 `[[custom_providers]]` 块即可。

### Q: 如何知道配置是否成功？
**A:** 运行 `python -m config list`，你会看到所有已配置的提供商。

### Q: 如何验证 API Key 是否有效？
**A:** 运行 `python -m config list --validate`，系统会实际调用 API 验证。

### Q: 内置提供商可以自定义模型吗？
**A:** 可以，通过命令行参数覆盖：
```bash
python Analyze.py --case CVE-XXX --provider deepseek --model custom-model
```

### Q: 如何查看某个提供商支持哪些模型？
**A:** 运行 `python -m config show provider_name` 查看详情。

### Q: 环境变量如何设置？
**A:** 
```bash
# Linux/Mac
export LLM_PROVIDER="deepseek"
export DEEPSEEK_API_KEY="sk-xxxxx"

# Windows
set LLM_PROVIDER=deepseek
set DEEPSEEK_API_KEY=sk-xxxxx
```

### Q: 如何在团队中共享配置？
**A:** 
1. 每个人有自己的 `keys.toml`（不提交）
2. 共享 `keys.example.toml` 作为模板
3. 在团队文档中说明如何获取 API Keys

### Q: 遇到 429 限流错误怎么办？
**A:** 
1. 增加重试次数：在 `custom_params` 中设置 `max_retries`
2. 降低请求频率：等待几秒再重试
3. 联系 API 服务提供商提高配额

### Q: 如何临时测试不同的模型？
**A:** 使用命令行参数，无需修改配置文件：
```bash
python Analyze.py --case CVE-XXX \
  --provider my_api \
  --think-model experimental-model \
  --chat-model stable-model
```

---

## 🔐 安全提示

- ✅ `keys.toml` 已在 `.gitignore` 中，不会被提交
- ✅ 不要在公开场合分享你的 API Keys
- ✅ 使用环境变量可以提供额外的安全层
- ✅ 定期轮换 API Keys
- ✅ 为不同环境使用不同的 API Keys（开发/测试/生产）

---

## 📝 更新日志

### v2.0 (当前版本)
- ✅ 模块化架构重构
- ✅ 支持 keys.toml 配置文件
- ✅ 支持自定义服务商
- ✅ Rich 美化显示
- ✅ 实际 API 验证
- ✅ 完整的命令行工具
- ✅ 100% 向后兼容

### v1.0
- 基础配置系统
- 硬编码提供商
- 环境变量支持

---

## 🤝 贡献

如需添加新的内置提供商或改进配置系统，请修改 `config/core.py`。

---

**需要帮助？**
- 运行 `python -m config --help` 查看命令帮助
- 查看 `keys.example.toml` 了解配置示例
- 运行 `python examples/custom_provider_demo.py` 查看完整演示
