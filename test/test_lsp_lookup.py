#!/usr/bin/env python3
"""
Tests for LSP Function Lookup functionality

Tests both the LSPDefinitionLookup utility and LSPFunctionLookupTool.
"""

import sys
from pathlib import Path

import pytest

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from pure_auto_codeql.tools.lsp_codeql import HotCodeQL
from pure_auto_codeql.utils.lsp_definition import LSPDefinitionLookup
from pure_auto_codeql.tools.lsp_lookup_tool import LSPFunctionLookupTool


def test_lsp_definition_lookup():
    """Test LSPDefinitionLookup utility class."""
    
    print("=" * 60)
    print("Testing LSPDefinitionLookup")
    print("=" * 60)
    
    # Setup test environment
    test_dir = Path(__file__).parent / "lsp_advanced_test"
    query_file = test_dir / "test.ql"
    
    if not query_file.exists():
        print("✗ Test query file not found")
        pytest.skip("Test query file not found")
    
    try:
        # Start LSP engine
        print("\n[1] Starting LSP engine...")
        engine = HotCodeQL(
            codeql="codeql",
            pack_root=test_dir,
            query_file=query_file,
            synchronous=True,
            init_timeout=60.0,
            quiet_logs=True
        )
        engine.start()
        print("✓ LSP engine started")
        
        # Create lookup instance
        print("\n[2] Creating LSPDefinitionLookup instance...")
        lookup = LSPDefinitionLookup(engine)
        print("✓ Lookup instance created")
        
        # Test find_symbol_in_text
        print("\n[3] Testing find_symbol_in_text...")
        query_text = query_file.read_text()
        position = lookup.find_symbol_in_text(query_text, "hasQualifiedName")
        if position:
            print(f"✓ Found 'hasQualifiedName' at line {position[0]}, char {position[1]}")
        else:
            print("✗ Could not find 'hasQualifiedName' in query")
            pytest.fail("Could not find 'hasQualifiedName' in query")
        
        # Test query_definition
        print("\n[4] Testing query_definition...")
        definition = lookup.query_definition(position[0], position[1], timeout=5.0)
        if definition:
            print("✓ Definition query successful")
            print(f"  Result type: {type(definition)}")
        else:
            print("✗ Definition query failed")
            pytest.fail("Definition query failed")
        
        # Test get_function_definition (main entry point)
        print("\n[5] Testing get_function_definition...")
        result = lookup.get_function_definition("hasQualifiedName", timeout=5.0)
        if result:
            print("✓ Function definition retrieved successfully")
            print(f"  File: {result['file_path']}")
            print(f"  Lines: {result['start_line']}-{result['end_line']}")
            print(f"  Code length: {len(result['code'])} characters")
            print("\n  First 5 lines of definition:")
            for i, line in enumerate(result['code'].split('\n')[:5], 1):
                print(f"    {i}: {line}")
        else:
            print("✗ Could not retrieve function definition")
            pytest.fail("Could not retrieve function definition")
        
        print("\n" + "=" * 60)
        print("LSPDefinitionLookup tests passed")
        print("=" * 60)
        
        engine.shutdown()
        return
        
    except Exception as e:
        print(f"\n✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        pytest.fail(str(e))


def test_lsp_function_lookup_tool():
    """Test LSPFunctionLookupTool LangChain integration."""
    
    print("\n" + "=" * 60)
    print("Testing LSPFunctionLookupTool")
    print("=" * 60)
    
    # Setup test environment
    test_dir = Path(__file__).parent / "lsp_advanced_test"
    query_file = test_dir / "test.ql"
    
    if not query_file.exists():
        print("✗ Test query file not found")
        pytest.skip("Test query file not found")
    
    try:
        # Start LSP engine
        print("\n[1] Starting LSP engine...")
        engine = HotCodeQL(
            codeql="codeql",
            pack_root=test_dir,
            query_file=query_file,
            synchronous=True,
            init_timeout=60.0,
            quiet_logs=True
        )
        engine.start()
        print("✓ LSP engine started")
        
        # Create tool instance
        print("\n[2] Creating LSPFunctionLookupTool...")
        tool = LSPFunctionLookupTool(engine=engine)
        print("✓ Tool created")
        print(f"  Tool name: {tool.name}")
        print(f"  Tool description: {tool.description[:80]}...")
        
        # Test tool execution
        print("\n[3] Testing tool.run() with 'hasQualifiedName'...")
        result = tool.run("hasQualifiedName")
        print("✓ Tool execution successful")
        print("\n  Result:")
        for line in result.split('\n')[:15]:  # Show first 15 lines
            print(f"    {line}")
        
        # Test with non-existent function
        print("\n[4] Testing with non-existent function...")
        result = tool.run("nonExistentFunction123")
        if "Could not find definition" in result:
            print("✓ Correctly handled non-existent function")
        else:
            print("✗ Unexpected result for non-existent function")
            pytest.fail("Unexpected result for non-existent function")
        
        # Test error handling
        print("\n[5] Testing error handling with invalid input...")
        result = tool.run("")
        if "Error" in result:
            print("✓ Correctly handled invalid input")
        else:
            print("✗ Should have returned error for empty input")
            pytest.fail("Should have returned error for empty input")
        
        print("\n" + "=" * 60)
        print("LSPFunctionLookupTool tests passed")
        print("=" * 60)
        
        engine.shutdown()
        return
        
    except Exception as e:
        print(f"\n✗ Test failed: {e}")
        import traceback
        traceback.print_exc()
        pytest.fail(str(e))


def main():
    """Run all tests."""
    print("\n" + "=" * 60)
    print("LSP Function Lookup Test Suite")
    print("=" * 60)
    
    results = []
    
    # Test 1: LSPDefinitionLookup
    results.append(("LSPDefinitionLookup", test_lsp_definition_lookup()))
    
    # Test 2: LSPFunctionLookupTool
    results.append(("LSPFunctionLookupTool", test_lsp_function_lookup_tool()))
    
    # Summary
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    for name, passed in results:
        status = "✓ PASSED" if passed else "✗ FAILED"
        print(f"{status}: {name}")
    
    all_passed = all(passed for _, passed in results)
    print("\n" + ("=" * 60))
    if all_passed:
        print("All tests passed!")
    else:
        print("Some tests failed!")
    print("=" * 60)
    
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
