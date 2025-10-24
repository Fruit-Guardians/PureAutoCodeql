"""
Basic tests to verify LangChain tools are properly structured.
"""

import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from tools import CodeQLGeneratorTool, CodeQLRunnerTool


def test_tools_importable():
    """Test that tools can be imported successfully."""
    assert CodeQLGeneratorTool is not None
    assert CodeQLRunnerTool is not None


def test_codeql_generator_tool_attributes():
    """Test CodeQLGeneratorTool has required attributes."""
    tool = CodeQLGeneratorTool()
    
    # Check required LangChain tool attributes
    assert hasattr(tool, 'name')
    assert hasattr(tool, 'description')
    assert hasattr(tool, 'args_schema')
    assert hasattr(tool, '_run')
    assert hasattr(tool, '_arun')
    
    # Check attribute values
    assert tool.name == "codeql_generator"
    assert isinstance(tool.description, str)
    assert len(tool.description) > 0


def test_codeql_runner_tool_attributes():
    """Test CodeQLRunnerTool has required attributes."""
    tool = CodeQLRunnerTool()
    
    # Check required LangChain tool attributes
    assert hasattr(tool, 'name')
    assert hasattr(tool, 'description')
    assert hasattr(tool, 'args_schema')
    assert hasattr(tool, '_run')
    assert hasattr(tool, '_arun')
    
    # Check attribute values
    assert tool.name == "codeql_runner"
    assert isinstance(tool.description, str)
    assert len(tool.description) > 0


def test_codeql_generator_input_schema():
    """Test CodeQLGeneratorTool input schema."""
    from tools.codeql_generator_tool import CodeQLGeneratorInput
    
    # Test schema can be instantiated
    input_data = CodeQLGeneratorInput(requirement="test requirement")
    assert input_data.requirement == "test requirement"


def test_codeql_runner_input_schema():
    """Test CodeQLRunnerTool input schema."""
    from tools.codeql_runner_tool import CodeQLRunnerInput
    
    # Test schema can be instantiated
    input_data = CodeQLRunnerInput(
        query_content="test query",
        database_path="./test/db"
    )
    assert input_data.query_content == "test query"
    assert input_data.database_path == "./test/db"


def test_codeql_generator_extract_function():
    """Test CodeQL extraction from response."""
    tool = CodeQLGeneratorTool()
    
    # Test with tags
    content = "<codeql>import java\nfrom Method m\nselect m</codeql>"
    extracted = tool._extract_codeql_from_response(content)
    assert extracted == "import java\nfrom Method m\nselect m"
    
    # Test without tags
    content_no_tags = "import java\nfrom Method m\nselect m"
    extracted_no_tags = tool._extract_codeql_from_response(content_no_tags)
    assert extracted_no_tags == content_no_tags


def test_codeql_runner_format_function():
    """Test result formatting."""
    tool = CodeQLRunnerTool()
    
    # Test successful result with SARIF output
    result = {
        'success': True,
        'output': 'analyze ok',
        'results': [],
        'sarif_path': '/output/result_20240101_000000.sarif',
    }
    formatted = tool._format_results(result)
    assert "CodeQL analyze completed successfully" in formatted
    assert "/output/result_20240101_000000.sarif" in formatted
    
    # Test failed result
    failed_result = {
        'success': False,
        'output': 'Error message',
        'results': [],
        'sarif_path': '/output/result_20240101_000000.sarif',
    }
    formatted_fail = tool._format_results(failed_result)
    assert "Execution failed" in formatted_fail
    assert "Error message" in formatted_fail
    assert "/output/result_20240101_000000.sarif" in formatted_fail
    
    # Test success without SARIF path (edge case)
    empty_result = {
        'success': True,
        'output': 'ok',
        'results': [],
    }
    formatted_empty = tool._format_results(empty_result)
    assert "CodeQL analyze completed successfully" in formatted_empty


if __name__ == "__main__":
    # Run basic checks
    print("Running basic tool verification...")
    
    test_tools_importable()
    print("[OK] Tools are importable")
    
    test_codeql_generator_tool_attributes()
    print("[OK] CodeQLGeneratorTool has correct attributes")
    
    test_codeql_runner_tool_attributes()
    print("[OK] CodeQLRunnerTool has correct attributes")
    
    test_codeql_generator_input_schema()
    print("[OK] CodeQLGeneratorTool input schema works")
    
    test_codeql_runner_input_schema()
    print("[OK] CodeQLRunnerTool input schema works")
    
    test_codeql_generator_extract_function()
    print("[OK] CodeQL extraction function works")
    
    test_codeql_runner_format_function()
    print("[OK] Result formatting function works")
    
    print("\n[SUCCESS] All verification tests passed!")

