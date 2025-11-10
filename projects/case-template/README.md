# 分析用例模板

使用此目录作为新分析用例的起始模板。

## 设置说明

1. **重命名父文件夹**：从 `case-template` 改为您的用例 ID（例如 `CVE-2025-54802`）。
2. **复制项目源码**：放入 `source_code/` 目录。
3. **导入或创建 CodeQL 数据库**：在 `db/<语言>/` 目录下。
4. **放置 CVE JSON / diff 文件**：放入 `inputs/` 目录。
5. **（可选）添加额外信息文件**：放入 `inputs/` 目录，见下方说明。
6. **保持 `intel/` 目录为空**：运行编排器后会自动填充。

## 目录结构

```text
case-template/                    # 模板目录
├── source_code/                  # 项目源码
├── db/                          # CodeQL 数据库
│   └── <language>/             # 特定语言数据库
├── inputs/                     # CVE 文件和补丁
├── intel/                      # 生成的情报数据
└── README.md                   # 本文件
```

---

## Case Template

Use this directory as a starting point for new analysis cases.

## Setup Instructions

1. **Rename the parent folder** from `case-template` to your case ID (for example `CVE-2025-54802`).
2. **Copy project sources** into `source_code/`.
3. **Import or create CodeQL databases** under `db/<language>/`.
4. **Place CVE JSON / diff files** in `inputs/`.
5. **Leave `intel/` empty**; it will be populated automatically after running the orchestrator.

## Directory Structure

```text
case-template/                    # Template directory
├── source_code/                  # Project source code
├── db/                          # CodeQL databases
│   └── <language>/             # Language-specific databases
├── inputs/                     # CVE files and patches
│   ├── CVE-*.json              # CVE information (required)
│   ├── CVE-*.diff              # Patch file (optional)
│   └── [extra files]           # Additional context files (optional)
├── intel/                      # Generated intelligence
└── README.md                   # This file
```

## 额外输入文件支持 (New Feature!)

您现在可以在 `inputs/` 目录中添加额外的信息文件来增强分析效果！

### 简单使用

**添加任意文件，使用任意文件名：**

```bash
# 文件名随意，格式随意
echo "系统使用 Spring Boot 2.3.4" > inputs/架构说明.txt
echo "发现了注入漏洞" > inputs/分析笔记.md
echo '{"version": "2.3.4"}' > inputs/versions.json
```

**运行分析，系统自动使用这些文件：**

```bash
python Analyze.py --case YOUR-CVE-ID
```

就这么简单！无需修改代码，无需遵循特定命名规范。

### 使用示例

#### 添加背景信息

```bash
cat > inputs/背景说明.md << 'EOF'
# 系统信息
- 框架: Spring Boot 2.3.4
- 漏洞位置: API Gateway
EOF
```

#### 添加版本信息

```bash
cat > inputs/版本.txt << 'EOF'
Spring Boot: 2.3.4.RELEASE
Java: 11
EOF
```

#### 添加分析记录

```bash
cat > inputs/发现.md << 'EOF'
发现了 SpEL 注入漏洞，需要进一步验证。
EOF
```

详细文档：[额外输入文件功能说明](../../docs/extra_input_files_simple.md)
