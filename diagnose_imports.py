#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
导入诊断脚本 - 检查项目的导入问题
"""

import sys
import traceback
from pathlib import Path

def print_status(message, status="INFO"):
    """打印状态信息"""
    icons = {"OK": "✅", "FAIL": "❌", "INFO": "ℹ️"}
    print(f"{icons.get(status, 'ℹ️')} {message}")

def test_imports():
    """测试关键导入"""
    print_status("开始导入诊断...")

    # 测试基础模块
    basic_imports = [
        ("pathlib.Path", "from pathlib import Path"),
        ("argparse", "import argparse"),
        ("asyncio", "import asyncio"),
    ]

    print_status("\n1. 测试基础Python模块:")
    for name, import_stmt in basic_imports:
        try:
            exec(import_stmt)
            print_status(f"  {name}", "OK")
        except Exception as e:
            print_status(f"  {name}: {e}", "FAIL")

    # 测试项目核心模块 (不依赖外部库)
    core_imports = [
        ("config", "import config"),
        ("utils.case", "from utils.case import resolve_case, discover_cve_assets"),
        ("utils.intel", "from utils.intel import collect_intel"),
    ]

    print_status("\n2. 测试项目核心模块:")
    for name, import_stmt in core_imports:
        try:
            exec(import_stmt)
            print_status(f"  {name}", "OK")
        except Exception as e:
            print_status(f"  {name}: {e}", "FAIL")

    # 测试外部依赖
    external_imports = [
        ("httpx", "import httpx"),
        ("langchain", "import langchain"),
        ("langchain.agents", "from langchain.agents import create_agent"),
        ("langchain_openai", "from langchain_openai import ChatOpenAI"),
    ]

    print_status("\n3. 测试外部依赖:")
    for name, import_stmt in external_imports:
        try:
            exec(import_stmt)
            print_status(f"  {name}", "OK")
        except Exception as e:
            print_status(f"  {name}: {e}", "FAIL")

    # 检查文件是否存在
    print_status("\n4. 检查关键文件:")
    key_files = [
        "Analyze.py",
        "core/__init__.py",
        "core/context.py",
        "core/pipeline.py",
        "core/orchestrator.py",
        "services/__init__.py",
        "services/llm_service.py",
        "utils/case.py",
        "utils/intel.py",
        "config.py",
        "pyproject.toml"
    ]

    for file_path in key_files:
        if Path(file_path).exists():
            print_status(f"  {file_path}", "OK")
        else:
            print_status(f"  {file_path}: 文件不存在", "FAIL")

def main():
    """主函数"""
    print("=" * 60)
    print("🔍 PureAutoCodeQL 导入诊断")
    print("=" * 60)

    try:
        test_imports()

        print("\n" + "=" * 60)
        print_status("诊断完成！", "INFO")

        print("\n💡 解决方案:")
        print("1. 如果外部依赖缺失，运行: uv sync")
        print("2. 如果文件缺失，检查项目结构")
        print("3. 如果Python版本问题，确保使用Python 3.8+")

    except Exception as e:
        print_status(f"诊断过程中出现错误: {e}", "FAIL")
        traceback.print_exc()

if __name__ == "__main__":
    main()