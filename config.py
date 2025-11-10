"""
PureAutoCodeQL LLM 配置系统入口

为了保持向后兼容，这个文件作为 config 模块的入口
所有导入 config.py 的代码都会自动使用新的模块化系统

新系统特性：
- 模块化架构（config/ 文件夹）
- keys.toml 统一配置
- 零代码修改快速部署
"""

# 导入所有配置内容，保持向后兼容
from config import *  # noqa: F401, F403

@dataclass
class LLMConfig:
    model: str
    api_key: str
    base_url: str
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None
    max_retries: int = 3
    provider: Optional[str] = None  # 仅做记录，兼容调用方

    # 重试机制配置
    retry_base_delay: float = 1.0  # 基础延迟时间（秒）
    retry_backoff_factor: float = 2.0  # 退避因子
    retry_jitter: bool = True  # 是否启用抖动算法
    retryable_status_codes: List[int] = None  # 可重试的HTTP状态码

    def __post_init__(self):
        """初始化后处理，设置默认值"""
        if self.retryable_status_codes is None:
            self.retryable_status_codes = [404, 500, 502, 503, 504, 429]

# 命令行工具入口
if __name__ == "__main__":
    from config.cli import main
    main()
