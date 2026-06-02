# LLM 配置系统使用指南

PureAutoCodeQL 的全新 LLM 配置系统提供了灵活、通用的服务商管理方案，支持内置和自定义服务商。

## 📋 目录

- [核心概念](#核心概念)
- [快速开始](#快速开始)
- [内置服务商](#内置服务商)
- [自定义服务商](#自定义服务商)
- [命令行工具](#命令行工具)
- [编程接口](#编程接口)
- [最佳实践](#最佳实践)

## 核心概念

### ProviderConfig

服务商配置数据类，包含：
- `name`: 内部标识符
- `display_name`: 用户友好的显示名称
- `base_url`: API 端点
- `default_think_model`: 默认推理模型
- `default_chat_model`: 默认对话模型
- `env_keys`: API Key 环境变量列表
- `env_base_urls`: Base URL 环境变量列表

### ProviderRegistry

服务商注册中心，管理所有服务商（内置和自定义）。

### LLMRole

模型角色枚举：
- `THINK`: 推理模型（用于 CodeQL 等复杂任务）
- `CHAT`: 对话模型（用于一般分析任务）

## 快速开始

### 1. 查看所有服务商状态

```bash
python config.py list
```

输出示例：
```
╭─────────────────────── 🤖 LLM 服务商状态一览 ───────────────────────╮
│ 服务商                  推理模型                  对话模型                  API Key   网络   状态         类型   │
├─────────────────────────────────────────────────────────────────────────┤
│ DeepSeek                deepseek-reasoner         deepseek-chat           ✓         ✓      ✅ 可用      内置   │
│ SiliconFlow (硅基流动)   deepseek-ai/DeepSeek-R1  Pro/deepseek-ai/Deep... ✓         ✓      ✅ 可用      内置   │
╰─────────────────────────────────────────────────────────────────────────╯
```

### 2. 查看服务商详情

```bash
python config.py show siliconflow
```

### 3. 测试服务商连接

```bash
python config.py test deepseek
```

### 4. 运行配置向导

```bash
python config.py setup
```

## 内置服务商

系统内置了以下服务商（均支持 OpenAI 兼容 API）：

### DeepSeek
- **Base URL**: `https://api.deepseek.com/v1`
- **环境变量**: `DEEPSEEK_API_KEY`
- **推理模型**: `deepseek-reasoner`
- **对话模型**: `deepseek-chat`

### SiliconFlow (硅基流动)
- **Base URL**: `https://api.siliconflow.cn/v1`
- **环境变量**: `SILICONFLOW_API_KEY` 或 `SF_API_KEY`
- **推理模型**: `deepseek-ai/DeepSeek-R1`
- **对话模型**: `Pro/deepseek-ai/DeepSeek-V3.2-Exp`
- **可用模型**: 包括 MiniMax-M2, Kimi-K2, Qwen3-Coder 等

### 智谱GLM
- **Base URL**: `https://open.bigmodel.cn/api/paas/v4/`
- **环境变量**: `ZHIPU_API_KEY` 或 `GLM_API_KEY`
- **默认模型**: `glm-4.6`

### Kimi (月之暗面)
- **Base URL**: `https://api.moonshot.cn/v1`
- **环境变量**: `KIMI_API_KEY` 或 `MOONSHOT_API_KEY`
- **推理模型**: `kimi-k2-thinking`
- **对话模型**: `kimi-k2-0905-preview`

### Google Gemini
- **Base URL**: `https://generativelanguage.googleapis.com/v1beta/openai`
- **环境变量**: `GEMINI_API_KEY` 或 `GOOGLE_API_KEY`
- **默认模型**: `gemini-2.5-pro`

## 自定义服务商

### 1. 创建配置文件

创建 `my_providers.yaml`：

```yaml
custom_providers:
  - name: "my_openai"
    display_name: "我的 OpenAI 代理"
    base_url: "https://my-proxy.com/v1"
    default_think_model: "gpt-4"
    default_chat_model: "gpt-3.5-turbo"
    env_keys:
      - "MY_OPENAI_KEY"
    env_base_urls:
      - "MY_OPENAI_BASE_URL"
    description: "通过代理访问 OpenAI API"
```

### 2. 注册服务商

```bash
python config.py register --file my_providers.yaml
```

### 3. 使用自定义服务商

```python
from pure_auto_codeql.configuration import get_llm_config, LLMRole

config = get_llm_config(LLMRole.THINK, provider_name="my_openai")
```

## 命令行工具

### 列出所有服务商
```bash
python config.py list
```

### 仅显示可用的服务商
```bash
python config.py list --available-only
```

### 显示服务商详情
```bash
python config.py show <provider_name>
```

### 测试服务商连接
```bash
python config.py test <provider_name>
```

### 注册自定义服务商
```bash
python config.py register --file <yaml_file>
```

### 运行配置向导
```bash
python config.py setup
```

## 编程接口

### 基本使用

```python
from pure_auto_codeql.configuration import get_llm_config, LLMRole

# 使用默认服务商
think_config = get_llm_config(LLMRole.THINK)
chat_config = get_llm_config(LLMRole.CHAT)

# 指定服务商
config = get_llm_config(LLMRole.THINK, provider_name="siliconflow")

# 指定模型
config = get_llm_config(
    LLMRole.THINK, 
    provider_name="siliconflow",
    model_name="Qwen/Qwen3-Coder-480B-A35B-Instruct"
)

# 完全自定义
config = get_llm_config(
    LLMRole.THINK,
    provider_name="my_custom",
    model_name="custom-model",
    api_key="sk-xxx",
    base_url="https://api.example.com/v1"
)
```

### 使用注册中心

```python
from pure_auto_codeql.configuration import (
    ProviderConfig,
    ProviderRegistry,
    display_providers_status,
)

# 查看所有服务商
display_providers_status()

# 获取服务商信息
provider = ProviderRegistry.get("deepseek")
print(provider.display_name)
print(provider.default_think_model)

# 检查服务商状态
is_available = provider.is_configured() and provider.is_reachable()

# 列出所有可用服务商
available = ProviderRegistry.list_available()

# 注册自定义服务商
ProviderRegistry.register(ProviderConfig(
    name="my_llm",
    display_name="My Custom LLM",
    base_url="http://localhost:8000/v1",
    default_think_model="model-think",
    default_chat_model="model-chat",
    env_keys=["MY_LLM_KEY"],
    env_base_urls=["MY_LLM_URL"],
    is_builtin=False
))
```

### 自动切换服务商

```python
# 启用自动切换：当首选服务商不可用时，自动切换到可用的
config = get_llm_config(
    LLMRole.THINK,
    auto_fallback=True  # 启用自动切换
)
```

### 信息展示

```python
from pure_auto_codeql.configuration import (
    display_providers_status,
    display_provider_detail,
    display_all_providers,
    validate_provider,
    display_validation_result
)

# 显示所有服务商状态（表格形式）
display_providers_status()

# 显示单个服务商详情
display_provider_detail("siliconflow")

# 显示所有服务商的完整信息
display_all_providers()

# 验证服务商配置
result = validate_provider("deepseek")
print(result)  # {'success': True, 'provider': 'deepseek', ...}

# 显示验证结果（美化输出）
display_validation_result("deepseek")
```

## 最佳实践

### 1. 环境变量配置

推荐使用环境变量管理 API Key：

```bash
# 设置默认服务商
export LLM_PROVIDER=siliconflow

# 设置 API Key
export SILICONFLOW_API_KEY=sk-xxx

# 覆盖默认模型
export THINK_MODEL=deepseek-ai/DeepSeek-R1
export CHAT_MODEL=Pro/deepseek-ai/DeepSeek-V3.2-Exp
```

### 2. 项目配置文件

在项目根目录创建 `.env` 文件：

```bash
LLM_PROVIDER=siliconflow
SILICONFLOW_API_KEY=sk-xxx
THINK_MODEL=deepseek-ai/DeepSeek-R1
CHAT_MODEL=Pro/deepseek-ai/DeepSeek-V3.2-Exp
```

### 3. 多环境管理

```python
# 开发环境
dev_config = get_llm_config(
    LLMRole.THINK,
    provider_name="local_llm",  # 使用本地模型
    base_url="http://localhost:11434/v1"
)

# 生产环境
prod_config = get_llm_config(
    LLMRole.THINK,
    provider_name="siliconflow",
    auto_fallback=True  # 生产环境启用自动切换
)
```

### 4. 自定义服务商最佳实践

```yaml
custom_providers:
  - name: "prod_openai"
    display_name: "生产环境 OpenAI"
    base_url: "https://prod-proxy.example.com/v1"
    default_think_model: "gpt-4-turbo"
    default_chat_model: "gpt-3.5-turbo"
    env_keys:
      - "PROD_OPENAI_KEY"
    env_base_urls:
      - "PROD_OPENAI_URL"
    description: "生产环境专用 OpenAI 代理"
    custom_params:
      region: "us-east-1"
      tier: "premium"
```

### 5. 错误处理

```python
from pure_auto_codeql.configuration import (
    LLMRole,
    ProviderRegistry,
    get_llm_config,
)

try:
    config = get_llm_config(LLMRole.THINK, provider_name="unknown")
except ValueError as e:
    print(f"服务商不存在: {e}")
    
    # 列出可用服务商
    available = ProviderRegistry.list_all()
    print(f"可用服务商: {[p.name for p in available]}")
```

## 环境变量优先级

配置优先级（从高到低）：

1. 函数参数（`api_key`, `base_url`, `model_name`）
2. 角色特定环境变量（`THINK_MODEL`, `CHAT_MODEL`）
3. 服务商专属环境变量（如 `DEEPSEEK_API_KEY`）
4. 通用环境变量（`OPENAI_API_KEY`, `OPENAI_BASE_URL`）
5. 服务商默认值

## 常见问题

### Q: 如何切换服务商？

A: 设置环境变量 `LLM_PROVIDER` 或在代码中指定 `provider_name`。

### Q: 如何添加自定义服务商？

A: 创建 YAML 配置文件并使用 `python config.py register --file` 注册。

### Q: 如何查看当前使用的服务商？

A: 运行 `python config.py setup` 或 `python config.py list`。

### Q: 支持哪些 API 格式？

A: 所有兼容 OpenAI API 格式的服务商都支持。

### Q: 如何使用本地模型（如 Ollama）？

A: 创建自定义服务商配置，指向本地 API 端点（如 `http://localhost:11434/v1`）。

## 更新日志

### v2.0 (最新)
- ✅ 引入 `ProviderConfig` 和 `ProviderRegistry` 架构
- ✅ 支持自定义服务商（YAML 配置）
- ✅ 使用 Rich 库实现美观的信息展示
- ✅ 完整的命令行工具
- ✅ 自动服务商切换功能
- ✅ 向后兼容旧版 API

### v1.0
- 基本的服务商支持（硬编码）
- 环境变量配置
- 简单的模型选择
