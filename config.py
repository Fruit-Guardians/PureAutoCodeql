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

# 命令行工具入口
if __name__ == "__main__":
    from config.cli import main
    main()
