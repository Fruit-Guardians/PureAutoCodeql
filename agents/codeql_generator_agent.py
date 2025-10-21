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
    
    def build_prompt(self, requirement: str) -> str:
        """Build a prompt to generate CodeQL code based on user requirements."""
        return (
            "You are a CodeQL expert. Generate CodeQL query code based on the user's requirement.\n"
            "Only output the CodeQL code wrapped in <codeql></codeql> tags without any extra explanation or styling.\n\n"
            "Common scenarios:\n"
            "- Querying source points (e.g., user input entry points)\n"
            "- Querying specific function calls\n"
            "- Analyzing data flow paths (taint tracking)\n"
            "- Finding security vulnerabilities\n\n"
            "Requirements:\n"
            "1. Generate valid CodeQL syntax\n"
            "2. Include necessary imports (e.g., import java, import semmle.code.java.dataflow.TaintTracking)\n"
            "3. Wrap the entire code in <codeql></codeql> tags\n"
            "4. Do not include any explanation outside the tags\n\n"
            f"User requirement: {requirement}\n"
        )
    
    async def generate_codeql(self, requirement: str) -> "AgentResult":
        """Generate CodeQL code based on user requirement."""
        try:
            prompt = self.build_prompt(requirement)
            return await self.analyzer.run_agent(prompt)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))

