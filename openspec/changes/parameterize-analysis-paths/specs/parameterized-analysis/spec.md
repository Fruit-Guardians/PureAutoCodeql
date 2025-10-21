## ADDED Requirements

### Requirement: 参数化源码根目录配置
系统 SHALL 支持动态配置源码根目录，而不是硬编码路径。

#### Scenario: 灵活的源码根目录设置
- **WHEN** 初始化 JavaSourceAnalysisAgent 或 JavaPathAnalysisAgent
- **THEN** Agent SHALL 接受 `source_root` 参数作为构造函数输入
- **AND** 如果未提供参数，SHALL 使用默认值 `"h5-vsan-service.jar_Decompiler.com"`
- **AND** Agent SHALL 使用提供的 `source_root` 进行所有文件路径操作

### Requirement: 参数化分析函数接口
系统 SHALL 提供参数化的主分析函数接口，支持自定义分析目标。

#### Scenario: 可配置的分析参数
- **WHEN** 调用 `run_multi_agent_analysis` 函数
- **THEN** 函数 SHALL 接受以下可选参数：
  - `json_path`: CVE JSON 文件路径（默认: "CVE-2021-21985.json"）
  - `diff_path`: Diff 文件路径（默认: "CVE-2021-21985.diff"）
  - `source_root`: 源码根目录路径（默认: "h5-vsan-service.jar_Decompiler.com"）
- **AND** 函数 SHALL 将 `source_root` 参数传递给所有相关的 Agent

### Requirement: 简化的主入口点
系统 SHALL 提供简化的主入口点，支持直接在代码中配置参数。

#### Scenario: 直接参数设置
- **WHEN** 通过 `main()` 函数启动分析
- **THEN** 函数 SHALL 在内部直接设置参数变量：
  - `source_root`: 源码根目录路径
  - `json_path`: CVE JSON 文件路径
  - `diff_path`: Diff 文件路径
- **AND** 参数修改 SHALL 直接在函数内部进行，便于调试
- **AND** SHALL 保持向后兼容性，现有调用方式继续有效

### Requirement: Agent 构造函数参数传递
系统 SHALL 确保参数正确传递到所有 Agent 实例。

#### Scenario: 参数传递链路
- **WHEN** 在 `run_multi_agent_analysis` 中创建 Agent 实例
- **THEN** JavaSourceAnalysisAgent SHALL 接收 `source_root` 参数
- **AND** JavaPathAnalysisAgent SHALL 接收 `source_root` 参数
- **AND** CVEAnalysisAgent SHALL 保持现有接口不变（不需要 source_root）

### Requirement: 参数验证和错误处理
系统 SHALL 提供参数验证和适当的错误处理。

#### Scenario: 路径参数验证
- **WHEN** 接收到 `source_root` 参数
- **THEN** 系统 SHALL 验证路径是否存在
- **AND** 如果路径不存在，SHALL 记录警告但不中断执行
- **WHEN** 接收到 `json_path` 或 `diff_path` 参数
- **THEN** 系统 SHALL 验证文件是否可读
- **AND** 如果文件不存在或不可读，SHALL 提供清晰的错误信息

### Requirement: 向后兼容性保证
系统 SHALL 保持与现有代码的完全向后兼容性。

#### Scenario: 现有调用方式保持有效
- **WHEN** 使用现有的无参数调用方式
- **THEN** 所有功能 SHALL 使用默认参数值正常工作
- **AND** 不 SHALL 引入任何破坏性变更
- **AND** 现有的测试和集成 SHALL 继续通过

### Requirement: 配置集中化
系统 SHALL 提供集中化的配置管理，避免参数分散。

#### Scenario: 统一的参数管理
- **WHEN** 需要修改默认配置
- **THEN** 开发者 SHALL 能够在 `main()` 函数中直接修改参数值
- **AND** 参数变更 SHALL 自动传播到所有相关组件
- **AND** 配置修改 SHALL 简单直观，便于调试和测试

### Requirement: 文档和示例更新
系统 SHALL 提供更新的文档和使用示例。

#### Scenario: 用户指导
- **WHEN** 实现参数化功能后
- **THEN** 代码注释 SHALL 说明如何在 `main()` 函数中修改参数
- **AND** 示例 SHALL 展示如何设置不同的参数值进行调试
- **AND** 文档 SHALL 说明所有可用的参数选项和默认值