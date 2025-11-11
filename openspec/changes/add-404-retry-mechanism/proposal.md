## Why
AI Agent在调用LLM API时遇到404等网络错误会直接停止运行，导致整个分析流程中断。需要实现智能重试机制来提高系统的健壮性，确保Agent在API服务不稳定时能够自动恢复并继续分析任务。

## What Changes
- **新增** LLM API请求的404错误重试机制到MultiAgentAnalyzer
- **修改** services/llm_service.py中的ChatOpenAI初始化和错误处理逻辑
- **增强** LLMConfig的max_retries配置，支持指数退避算法
- **新增** Agent级别的重试状态跟踪和日志记录
- **修改** create_agent函数以支持重试包装器

## Impact
- Affected specs: llm-service
- Affected code: services/llm_service.py, config.py
- **BREAKING**: 修改了LLM API调用的错误处理行为，404/500等错误不再直接失败而是会重试