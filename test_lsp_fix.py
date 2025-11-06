#!/usr/bin/env python3
"""
测试LSP服务修复的脚本
验证第二轮查询时LSP服务连接不会失败
"""

import asyncio
import sys
from pathlib import Path

# 添加项目路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from tools.codeql_compose import CodeQLComposeTool

async def test_lsp_service_fix():
    """测试LSP服务修复"""
    print("🧪 开始测试LSP服务修复...")
    
    # 创建一个简单的CodeQLComposeTool实例
    # 注意：这里需要设置analyzer和database_path，但我们可以模拟一个简单的测试
    tool = CodeQLComposeTool()
    
    # 由于需要analyzer，我们直接测试LSP服务的启动和停止逻辑
    print("🔍 测试LSP服务生命周期管理...")
    
    # 导入LSP服务类
    from tools.codeql_compose import LSPCodeQLService
    
    # 创建临时目录
    import tempfile
    with tempfile.TemporaryDirectory() as temp_dir:
        print(f"📁 创建临时目录: {temp_dir}")
        
        # 测试LSP服务的启动和停止
        lsp_service = LSPCodeQLService(temp_dir)
        
        # 启动服务
        print("🚀 启动LSP服务...")
        if lsp_service.start_server(temp_dir):
            print("✅ LSP服务启动成功")
            
            # 模拟第一轮查询
            print("🔍 模拟第一轮查询语法检查...")
            try:
                # 创建一个简单的CodeQL查询进行测试
                test_query = """
                import cpp
                
                from Function f
                select f
                """
                
                result1 = lsp_service.check_codeql_syntax(test_query)
                print(f"📊 第一轮查询结果: {result1}")
                
                # 模拟第二轮查询 - 这里应该不会出现连接失败
                print("🔍 模拟第二轮查询语法检查...")
                result2 = lsp_service.check_codeql_syntax(test_query)
                print(f"📊 第二轮查询结果: {result2}")
                
                print("✅ 第二轮查询成功完成，没有出现连接失败！")
                
            except Exception as e:
                print(f"❌ 查询过程中出现错误: {e}")
                return False
            
            # 停止服务
            print("🛑 停止LSP服务...")
            lsp_service.stop_server()
            print("✅ LSP服务停止成功")
            
            return True
        else:
            print("❌ LSP服务启动失败")
            return False

async def main():
    """主测试函数"""
    print("=" * 60)
    print("LSP服务修复测试")
    print("=" * 60)
    
    success = await test_lsp_service_fix()
    
    print("\n" + "=" * 60)
    if success:
        print("🎉 测试通过！LSP服务修复成功")
        print("✅ 第二轮查询时不会出现连接失败问题")
    else:
        print("❌ 测试失败！需要进一步检查修复")
    print("=" * 60)

if __name__ == "__main__":
    asyncio.run(main())