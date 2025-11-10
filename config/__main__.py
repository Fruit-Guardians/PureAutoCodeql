"""
使 config 模块可以通过 python -m config 直接运行
"""

from .cli import main

if __name__ == "__main__":
    main()

