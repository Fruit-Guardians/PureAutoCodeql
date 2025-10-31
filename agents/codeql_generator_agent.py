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
    """基于自然语言需求生成CodeQL查询代码的Agent。"""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer"):
        self.analyzer = analyzer
    
    def build_prompt(self, requirement: str, language: str = "java") -> str:
        """构建提示词来生成基于用户需求和目标语言的CodeQL代码。"""
        lang = (language or "java").lower()
        
        # 简化的中文提示词
        common_intro = (
            "你是CodeQL专家。根据用户需求生成CodeQL查询代码。\n"
            "只输出用<codeql></codeql>标签包裹的CodeQL代码，不要额外解释。\n\n"
        )
        
        # 语言特定的导入提示
        if lang == "python":
            imports_hint = "生成Python的CodeQL语法，包含必要导入（如import python, import semmle.code.python.dataflow.TaintTracking）\n"
        elif lang in {"cpp", "c", "c++"}:
            imports_hint = "生成C/C++的CodeQL语法，包含必要导入（如import cpp, import semmle.code.cpp.dataflow.TaintTracking）\n"
        else:
            # Java特定要求
            imports_hint = (
                "生成Java的CodeQL语法，包含必要导入（如import java, import semmle.code.java.dataflow.TaintTracking）\n"
                "**重要要求：**\n"
                "- 禁止使用MethodAccess（已废弃）\n"
                "- 必须使用MethodCall\n"
                "- 示例：使用`exists(MethodCall mc | mc.getMethod().hasName(\"invoke\"))`\n"
            )
        
        return (
            common_intro + 
            imports_hint + 
            "将代码包裹在<codeql></codeql>标签中，不要额外解释。\n\n" +
            f"用户需求：{requirement}\n"
        )
    
    async def generate_codeql(self, requirement: str, language: str = "java") -> "AgentResult":
        """基于用户需求和目标语言生成CodeQL代码。"""
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

