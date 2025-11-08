#!/usr/bin/env python3
"""
测试CodeQL空结果检测功能
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from services.codeql_execution import CodeQLExecutionResult

def test_empty_results_detection():
    """测试空结果检测功能"""
    
    print("=== 测试CodeQL空结果检测功能 ===\n")
    
    # 测试用例1: analyze模式，paths_count为0
    print("测试用例1: analyze模式，paths_count为0")
    result1 = CodeQLExecutionResult(
        success=True,
        output="查询执行成功",
        paths_count=0
    )
    print(f"结果: {result1.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result1.has_results else '失败'}\n")
    
    # 测试用例2: analyze模式，paths_count为5
    print("测试用例2: analyze模式，paths_count为5")
    result2 = CodeQLExecutionResult(
        success=True,
        output="查询执行成功",
        paths_count=5
    )
    print(f"结果: {result2.has_results}")
    print(f"预期: True")
    print(f"测试结果: {'通过' if result2.has_results else '失败'}\n")
    
    # 测试用例3: run模式，输出包含"No results."
    print("测试用例3: run模式，输出包含'No results.'")
    result3 = CodeQLExecutionResult(
        success=True,
        output="No results.",
        paths_count=None
    )
    print(f"结果: {result3.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result3.has_results else '失败'}\n")
    
    # 测试用例4: run模式，输出包含"0 results"
    print("测试用例4: run模式，输出包含'0 results'")
    result4 = CodeQLExecutionResult(
        success=True,
        output="查询结果: 0 results found",
        paths_count=None
    )
    print(f"结果: {result4.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result4.has_results else '失败'}\n")
    
    # 测试用例5: run模式，输出包含实际数据
    print("测试用例5: run模式，输出包含实际数据")
    result5 = CodeQLExecutionResult(
        success=True,
        output="""| file | line | vulnerability |
|------|------|---------------|
| main.py | 10 | SQL Injection |
| utils.py | 25 | XSS |""",
        paths_count=None
    )
    print(f"结果: {result5.has_results}")
    print(f"预期: True")
    print(f"测试结果: {'通过' if result5.has_results else '失败'}\n")
    
    # 测试用例6: run模式，只有表头
    print("测试用例6: run模式，只有表头")
    result6 = CodeQLExecutionResult(
        success=True,
        output="""| file | line | vulnerability |
|------|------|---------------|""",
        paths_count=None
    )
    print(f"结果: {result6.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result6.has_results else '失败'}\n")
    
    # 测试用例7: 空输出
    print("测试用例7: 空输出")
    result7 = CodeQLExecutionResult(
        success=True,
        output="",
        paths_count=None
    )
    print(f"结果: {result7.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result7.has_results else '失败'}\n")
    
    # 测试用例8: 空白输出
    print("测试用例8: 空白输出")
    result8 = CodeQLExecutionResult(
        success=True,
        output="   \n  \t  \n  ",
        paths_count=None
    )
    print(f"结果: {result8.has_results}")
    print(f"预期: False")
    print(f"测试结果: {'通过' if not result8.has_results else '失败'}\n")
    
    print("=== 测试完成 ===")

if __name__ == "__main__":
    test_empty_results_detection()