# 模型切换指南

本文档介绍如何在 PureAutoCodeQL 中切换和使用不同的 LLM 模型提供商。

## 📋 支持的模型提供商

| 提供商 | 推理模型 | 对话模型 | Base URL | 状态 |
|--------|----------|----------|----------|------|
| **DeepSeek** | deepseek-reasoner | deepseek-chat | https://api.deepseek.com/v1 | ✅ 推荐（默认） |
| **SiliconFlow (硅基流动)** | deepseek-ai/DeepSeek-R1 | Pro/deepseek-ai/DeepSeek-V3.2-Exp | https://api.siliconflow.cn/v1 | ✅ 稳定 |
| **Kimi (月之暗面)** | kimi-k2-thinking | kimi-k2-0905-preview | https://api.moonshot.cn/v1 | ✅ 可用 |
| **智谱GLM** | glm-4.6 | glm-4.6 | https://open.bigmodel.cn/api/paas/v4/ | ✅ 可用 |

## 🚀 快速开始

### 方式一：命令行参数（推荐）

无需修改环境变量，直接在命令行指定提供商和模型：

```bash
# 使用 DeepSeek（默认）
python Analyze.py --case CVE-2021-21985 --provider deepseek

# 使用 SiliconFlow
python Analyze.py --case CVE-2021-21985 --provider siliconflow

# 使用 Kimi
python Analyze.py --case CVE-2021-21985 --provider kimi

# 使用智谱GLM
python Analyze.py --case CVE-2021-21985 --provider zhipu
```

### 方式二：环境变量配置

```bash
# PowerShell
$env:LLM_PROVIDER="siliconflow"
$env:SILICONFLOW_API_KEY="your_api_key_here"

# Linux/Mac
export LLM_PROVIDER=siliconflow
export SILICONFLOW_API_KEY=your_api_key_here
```

## 🎯 指定特定模型

### 使用默认模型

每个提供商都有预设的推理模型和对话模型，直接指定提供商即可：

```bash
python Analyze.py --case CVE-2021-21985 --provider siliconflow
```

### 指定自定义模型

#### 同时指定推理和对话模型

```bash
# 使用 --think-model 和 --chat-model 分别指定
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --think-model MiniMaxAI/MiniMax-M2 \
  --chat-model moonshotai/Kimi-K2-Instruct-0905
```

#### 使用同一模型（简化方式）

```bash
# 使用 --model 同时应用到推理和对话
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model MiniMaxAI/MiniMax-M2
```

## 🔑 API Key 和 Base URL 配置

### 通过命令行指定（完全不需要环境变量）

```bash
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model MiniMaxAI/MiniMax-M2 \
  --api-key your_api_key_here \
  --base-url https://api.siliconflow.cn/v1
```

### 通过环境变量配置

#### DeepSeek
```bash
export DEEPSEEK_API_KEY=your_api_key_here
export DEEPSEEK_BASE_URL=https://api.deepseek.com/v1  # 可选
```

#### SiliconFlow (硅基流动)
```bash
export SILICONFLOW_API_KEY=your_api_key_here
# 或
export SF_API_KEY=your_api_key_here
export SILICONFLOW_BASE_URL=https://api.siliconflow.cn/v1  # 可选
```

#### Kimi (月之暗面)
```bash
export KIMI_API_KEY=your_api_key_here
# 或
export MOONSHOT_API_KEY=your_api_key_here
export KIMI_BASE_URL=https://api.moonshot.cn/v1  # 可选
```

#### 智谱GLM
```bash
export ZHIPU_API_KEY=your_api_key_here
# 或
export GLM_API_KEY=your_api_key_here
export ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4/  # 可选
```

### 通用配置（所有提供商都支持）

```bash
export OPENAI_API_KEY=your_fallback_key
export OPENAI_BASE_URL=your_custom_endpoint
```

## 📚 SiliconFlow 可用模型列表

SiliconFlow 支持多个模型，可以通过以下命令查看：

```bash
python Analyze.py --list-models
```

当前支持的模型包括：
- ⭐ `deepseek-ai/DeepSeek-R1` (默认推理模型)
- ⭐ `Pro/deepseek-ai/DeepSeek-V3.2-Exp` (默认对话模型)
- `MiniMaxAI/MiniMax-M2`
- `moonshotai/Kimi-K2-Instruct-0905`
- `Qwen/Qwen3-Coder-480B-A35B-Instruct`

### 使用示例

```bash
# 使用 MiniMax 模型
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model MiniMaxAI/MiniMax-M2

# 使用 Kimi 模型（通过 SiliconFlow）
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model moonshotai/Kimi-K2-Instruct-0905

# 使用 Qwen 模型
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model Qwen/Qwen3-Coder-480B-A35B-Instruct
```

## 🔍 查看提供商状态

查看所有可用的模型提供商及其状态：

```bash
python Analyze.py --list-providers
```

输出示例：
```
📋 可用的模型提供商:
🔹 DeepSeek (deepseek)
   状态: ✅ 可用
   推理模型: deepseek-reasoner
   对话模型: deepseek-chat
   Base URL: https://api.deepseek.com/v1

🔹 SiliconFlow (硅基流动) (siliconflow)
   状态: ✅ 可用
   推理模型: deepseek-ai/DeepSeek-R1
   对话模型: Pro/deepseek-ai/DeepSeek-V3.2-Exp
   Base URL: https://api.siliconflow.cn/v1
   可用模型:
     1. deepseek-ai/DeepSeek-R1
     2. Pro/deepseek-ai/DeepSeek-V3.2-Exp
     3. MiniMaxAI/MiniMax-M2
     4. moonshotai/Kimi-K2-Instruct-0905
     5. Qwen/Qwen3-Coder-480B-A35B-Instruct

🔹 Kimi (月之暗面) (kimi)
   状态: ✅ 可用
   推理模型: kimi-k2-thinking
   对话模型: kimi-k2-0905-preview
   Base URL: https://api.moonshot.cn/v1
```

## ⚙️ 参数优先级

配置参数的优先级从高到低：

1. **命令行参数** (`--provider`, `--model`, `--think-model`, `--chat-model`, `--api-key`, `--base-url`)
2. **环境变量** (`LLM_PROVIDER`, `THINK_MODEL`, `CHAT_MODEL`, `*_API_KEY`, `*_BASE_URL`)
3. **默认值** (各提供商的默认模型)

### 示例说明

```bash
# 场景1：命令行参数覆盖环境变量
export LLM_PROVIDER=deepseek
export THINK_MODEL=deepseek-reasoner
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --think-model MiniMaxAI/MiniMax-M2
# 结果：使用 siliconflow 和 MiniMaxAI/MiniMax-M2

# 场景2：环境变量覆盖默认值
export LLM_PROVIDER=siliconflow
export THINK_MODEL=MiniMaxAI/MiniMax-M2
python Analyze.py --case CVE-2021-21985
# 结果：使用 siliconflow 和 MiniMaxAI/MiniMax-M2

# 场景3：使用默认配置
python Analyze.py --case CVE-2021-21985
# 结果：使用 deepseek 和默认模型
```

## 💡 使用场景示例

### 场景1：快速切换提供商

```bash
# 测试不同提供商的效果
python Analyze.py --case CVE-2021-21985 --provider deepseek
python Analyze.py --case CVE-2021-21985 --provider siliconflow
python Analyze.py --case CVE-2021-21985 --provider kimi
```

### 场景2：使用特定模型进行代码分析

```bash
# 使用代码专用模型
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model Qwen/Qwen3-Coder-480B-A35B-Instruct
```

### 场景3：完全通过命令行配置（适合 CI/CD）

```bash
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --think-model deepseek-ai/DeepSeek-R1 \
  --chat-model Pro/deepseek-ai/DeepSeek-V3.2-Exp \
  --api-key $CI_API_KEY \
  --base-url https://api.siliconflow.cn/v1
```

### 场景4：分别指定推理和对话模型

```bash
# 推理任务使用推理模型，对话任务使用对话模型
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --think-model deepseek-ai/DeepSeek-R1 \
  --chat-model MiniMaxAI/MiniMax-M2
```

## 🛠️ 故障排除

### 问题1：API Key 未设置

**错误信息**：`❌ 无法配置模型: API Key缺失`

**解决方案**：
```bash
# 方式1：通过命令行指定
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --api-key your_api_key_here

# 方式2：设置环境变量
export SILICONFLOW_API_KEY=your_api_key_here
```

### 问题2：Base URL 不可达

**错误信息**：`❌ 不可达`

**解决方案**：
```bash
# 检查网络连接
ping api.siliconflow.cn

# 或指定正确的 Base URL
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --base-url https://api.siliconflow.cn/v1
```

### 问题3：模型名称错误

**错误信息**：模型调用失败

**解决方案**：
```bash
# 查看可用模型
python Analyze.py --list-models

# 使用正确的模型名称
python Analyze.py --case CVE-2021-21985 \
  --provider siliconflow \
  --model MiniMaxAI/MiniMax-M2  # 注意大小写和格式
```

### 问题4：提供商不支持

**错误信息**：`不支持的提供商: xxx`

**解决方案**：
```bash
# 查看支持的提供商
python Analyze.py --list-providers

# 使用正确的提供商名称（小写）
python Analyze.py --case CVE-2021-21985 --provider siliconflow
```

## 📝 完整命令行参数参考

```bash
python Analyze.py --case CVE-2021-21985 \
  --provider <deepseek|siliconflow|zhipu|kimi> \
  [--model MODEL_NAME] \
  [--think-model MODEL_NAME] \
  [--chat-model MODEL_NAME] \
  [--api-key API_KEY] \
  [--base-url BASE_URL] \
  [--stream] \
  [--no-stream] \
  [--refresh-intel] \
  [--output OUTPUT_FILE]
```

### 参数说明：

- `--case`: 必需，指定要分析的案例ID
- `--provider`: 可选，指定模型提供商
- `--model`: 可选，指定模型名称（同时用于推理和对话）
- `--think-model`: 可选，指定推理模型名称
- `--chat-model`: 可选，指定对话模型名称
- `--api-key`: 可选，指定API Key
- `--base-url`: 可选，指定Base URL
- `--stream`: 可选，显示AI思考过程（默认开启）
- `--no-stream`: 可选，禁用AI思考过程显示
- `--refresh-intel`: 可选，强制刷新情报数据
- `--output`: 可选，指定输出文件名

## 🔗 相关命令

```bash
# 列出所有可用案例
python Analyze.py --list

# 列出所有提供商
python Analyze.py --list-providers

# 列出 SiliconFlow 可用模型
python Analyze.py --list-models

# 验证案例有效性
python Analyze.py --validate CVE-2021-21985
```

## 📖 更多信息

- 所有提供商均使用 OpenAI 兼容的 API 接口
- 推理模型（think）用于 CodeQL 相关任务
- 对话模型（chat）用于一般分析任务
- 命令行参数会覆盖环境变量设置
- 支持自动切换：如果首选提供商不可达，会自动尝试其他提供商

---

**提示**：建议在首次使用前运行 `python Analyze.py --list-providers` 检查所有提供商的配置状态。

