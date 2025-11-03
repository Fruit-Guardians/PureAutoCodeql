from pathlib import Path
from typing import Optional, TYPE_CHECKING

if TYPE_CHECKING:
    from dataclasses import dataclass
    
    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None
    
    class MultiAgentAnalyzer:
        pass

from utils.io import read_json_text


class CVEAnalysisAgent:
    """用于分析CVE JSON文件的Agent。"""
    
    def __init__(self, analyzer: "MultiAgentAnalyzer"):
        self.analyzer = analyzer
    
    def build_prompt(self, json_text: str, intel_block: Optional[str] = None) -> str:
        """构建提示词将提供的CVE JSON转换为专注的Markdown格式。"""
        sections = [
            "你是一名安全分析助手。请从CVE JSON中仅提取以下信息：",
            "",
            "**严格的输出格式要求：**",
            "- 仅输出Markdown内容，不要添加额外注释",
            "- 使用以下确切的章节标题（逐字复制）：",
            "  - ## 漏洞类型",
            "  - ## 技术细节",
            "- **仅包含以下具体细节：**",
            "  - 漏洞类型（如：远程代码执行、SQL注入、缓冲区溢出等）",
            "  - 具体的漏洞位置（哪个文件、哪个函数、哪个代码片段）",
            "  - 漏洞的具体成因（如：输入验证缺失、不安全的反序列化等）",
            "- **不要包含以下任何内容：**",
            "  - 影响版本信息",
            "  - CVSS评分",
            "  - 参考信息",
            "  - 威胁情报",
            "  - 修复建议",
            "  - 攻击条件",
            "  - 攻击影响",
            "- 保持输出极其简洁，仅关注技术漏洞细节",
        ]
        if intel_block:
            sections.append("")
            sections.append("**附加情报集成：**")
            sections.append("- 仅从GHSA/NVD集成关于漏洞位置和类型的技术细节")
            sections.append("- 忽略所有非技术信息，如版本、评分、参考信息")
            sections.append(intel_block)
        sections.append("")
        sections.append("**CVE JSON数据：**")
        sections.append(json_text)
        return "\n".join(sections)
    
    async def analyze_cve(
        self,
        json_path: Path,
        *,
        intel_prompt: Optional[str] = None,
        show_thinking: bool = True,
    ) -> "AgentResult":
        """分析CVE JSON文件并返回Markdown报告。"""
        try:
            json_text = read_json_text(json_path)
            prompt = self.build_prompt(json_text, intel_prompt)
            return await self.analyzer.run_agent(prompt, show_thinking=show_thinking)
        except Exception as e:
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))
