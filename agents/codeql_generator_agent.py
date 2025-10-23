from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None
    
    class MultiAgentAnalyzer:
        pass


class CodeQLGeneratorAgent:
    """Agent for generating CodeQL query code based on natural language requirements."""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer"):
        self.analyzer = analyzer
    
    def build_prompt(self, requirement: str, language: str = "java") -> str:
        """Build a prompt to generate CodeQL code based on user requirements and target language."""
        lang = (language or "java").lower()
        common_intro = (
            "You are a CodeQL expert. Generate CodeQL query code based on the user's requirement.\n"
            "Only output the CodeQL code wrapped in <codeql></codeql> tags without any extra explanation or styling.\n\n"
            "Common scenarios:\n"
            "- Querying source points (e.g., user input entry points)\n"
            "- Querying specific function calls\n"
            "- Analyzing data flow paths (taint tracking)\n"
            "- Finding security vulnerabilities\n\n"
        )
        if lang == "python":
            imports_hint = (
                "Requirements:\n"
                "1. Generate valid CodeQL syntax for Python\n"
                "2. Include necessary imports (e.g., import python, import semmle.code.python.dataflow.TaintTracking or appropriate dataflow/security modules)\n"
                "3. Wrap the entire code in <codeql></codeql> tags\n"
                "4. Do not include any explanation outside the tags\n\n"
            )
        elif lang in {"cpp", "c", "c++"}:
            imports_hint = (
                "Requirements:\n"
                "1. Generate valid CodeQL syntax for C/C++\n"
                "2. Include necessary imports (e.g., import cpp, import semmle.code.cpp.dataflow.TaintTracking)\n"
                "3. Wrap the entire code in <codeql></codeql> tags\n"
                "4. Do not include any explanation outside the tags\n\n"
            )
        else:
            imports_hint = (
                "Requirements:\n"
                "1. Generate valid CodeQL syntax for Java\n"
                "2. Include necessary imports (e.g., import java, import semmle.code.java.dataflow.TaintTracking)\n"
                "3. Wrap the entire code in <codeql></codeql> tags\n"
                "4. Do not include any explanation outside the tags\n\n"
            )
        return (
            common_intro + imports_hint + f"User requirement: {requirement}\n"
        )
    
    async def generate_codeql(self, requirement: str, language: str = "java") -> "AgentResult":
        """Generate CodeQL code based on user requirement and target language."""
        try:
            prompt = self.build_prompt(requirement, language=language)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))

