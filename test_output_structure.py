#!/usr/bin/env python3
"""
测试新的输出结构
"""

import asyncio
from pathlib import Path
from core.pipeline import AnalysisPipeline
from core.context import AnalysisContext, AnalysisResult
from dataclasses import dataclass

@dataclass
class MockAgentResult:
    content: str
    success: bool = True
    error: str = None

async def test_output_structure():
    """测试新的输出结构"""
    
    # 创建模拟的分析结果
    result = AnalysisResult(
        case_id="test_case_001",
        language="java",
        cve_result=MockAgentResult(content="# CVE Analysis\nTest CVE analysis content"),
        sink_result=MockAgentResult(content="# Sink Analysis\nTest sink analysis content"),
        source_result=MockAgentResult(content="# Source Analysis\nTest source analysis content"),
        codeql_result=MockAgentResult(content="import java\nselect 1"),
        success=True,
        execution_time=10.5
    )
    
    # 创建模拟的上下文
    context = AnalysisContext(
        case_id="test_case_001",
        case_paths=None,
        cve_assets=None,
        language="java",
        intel_bundle=None,
        show_thinking=False
    )
    
    # 创建分析流水线实例
    pipeline = AnalysisPipeline([])
    
    # 测试输出整合功能
    print("🧪 测试新的输出结构...")
    await pipeline._consolidate_output_files(context, result)
    
    # 检查输出目录是否创建
    output_dirs = list(Path("./output").glob("analysis_output_*"))
    if output_dirs:
        latest_dir = max(output_dirs, key=lambda x: x.stat().st_mtime)
        print(f"✅ 输出目录已创建: {latest_dir}")
        
        # 检查文件
        files = list(latest_dir.iterdir())
        print(f"📁 目录包含 {len(files)} 个文件:")
        for file in files:
            print(f"   - {file.name}")
            
        # 检查是否包含必要的文件
        expected_files = ['output.md']
        missing_files = [f for f in expected_files if not (latest_dir / f).exists()]
        
        if not missing_files:
            print("✅ 所有必需文件都已生成")
        else:
            print(f"❌ 缺少文件: {missing_files}")
            
    else:
        print("❌ 输出目录未创建")

if __name__ == "__main__":
    asyncio.run(test_output_structure())