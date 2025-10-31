"""
Basic tests to verify LangChain tools are properly structured.
"""

import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from tools import CodeQLComposeTool


def test_tools_importable():
    """Test that tools can be imported successfully."""
    assert CodeQLComposeTool is not None


def test_codeql_compose_tool_attributes():
    """Test CodeQLComposeTool has required attributes."""
    tool = CodeQLComposeTool()
    
    # Check required LangChain tool attributes
    assert hasattr(tool, 'name')
    assert hasattr(tool, 'description')
    assert hasattr(tool, 'args_schema')
    assert hasattr(tool, '_run')
    assert hasattr(tool, '_arun')
    
    # Check attribute values
    assert tool.name == "codeql_compose"
    assert isinstance(tool.description, str)
    assert len(tool.description) > 0


def test_codeql_compose_input_schema():
    """Test CodeQLComposeTool input schema."""
    from tools.codeql_compose import CodeQLComposeInput
    
    # Test schema can be instantiated
    input_data = CodeQLComposeInput(requirement="test requirement")
    assert input_data.requirement == "test requirement"


if __name__ == "__main__":
    # Run basic checks
    print("Running basic tool verification...")
    
    test_tools_importable()
    print("[OK] Tools are importable")
    
    test_codeql_compose_tool_attributes()
    print("[OK] CodeQLComposeTool has correct attributes")
    
    test_codeql_compose_input_schema()
    print("[OK] CodeQLComposeTool input schema works")
    
    print("\n[SUCCESS] All verification tests passed!")

