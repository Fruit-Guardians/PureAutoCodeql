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
            "你是一名安全分析助手。请从CVE JSON中提取特定的技术漏洞细节，并按照严格的格式要求输出Markdown内容。",
            "",
            "### 输出格式",
            "- 仅输出Markdown内容，不要添加额外注释。",
            "- 使用以下确切的章节标题（逐字复制）：",
            "  - 漏洞类型",
            "  - 技术细节",
            "  - Sink点",
            "  - Source点",
            "",
            "### 提取的具体信息",
            "",
            "#### 漏洞类型",
            "- 明确指出漏洞类型（如：远程代码执行、SQL注入、缓冲区溢出等）。",
            "",
            "#### 技术细节",
            "- 具体的漏洞位置（哪个文件、哪个函数、哪个代码片段）。",
            "- 漏洞的具体成因（如：输入验证缺失、不安全的反序列化等）。",
            "",
            "#### Sink点",
            "- 关于这个CVE，具体命令执行点或者文件上传点的位置和类名和函数名。",
            "",
            "#### 可能的Source点",
            "- 关于这个CVE，用户输入点具体位置和函数（如log4j2的JNDI注入的Source点为Logger类下的error等方法）。",
            "- 如果不能给出具体的Source点，则给出方法论（如：用户输入参数、HTTP请求头、文件内容读取、网络数据接收、环境变量、配置文件等路径传输方式，如果CVE比较确定则不考虑其他路径）。",
            "",
            "### 不要包含以下任何内容",
            "- 影响版本信息",
            "- CVSS评分",
            "- 参考信息",
            "- 威胁情报",
            "- 修复建议",
            "- 攻击条件",
            "- 攻击影响",
            "",
        ]
        if intel_block:
            sections.append("")
            sections.append("**附加情报集成：**")
            sections.append("- 仅从GHSA/NVD集成关于漏洞位置和类型的技术细节")
            sections.append("- 重点关注可能的Sink点和Source点相关信息")
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
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """分析CVE JSON文件并返回Markdown报告。"""
        try:
            # Phase 4.4: 使用传入的agent_name和agent_type，若无则使用默认值
            _agent_name = agent_name or "CVE Analysis Agent"
            _agent_type = agent_type or "cve_analysis"
            
            # Phase 2.2: 推送 AGENT_START 事件
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": "开始CVE分析",
                    "data": {"json_path": str(json_path)}
                })
            
            json_text = read_json_text(json_path)
            prompt = self.build_prompt(json_text, intel_prompt)
            result = await self.analyzer.run_agent(prompt, show_thinking=show_thinking, event_callback=event_callback)
            
            # Phase 2.3: 推送 AGENT_COMPLETE 事件
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": "CVE分析完成",
                    "data": {"success": result.success}
                })
            
            return result
        except Exception as e:
            # 推送错误事件
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CVE分析失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass
            
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None
            
            return AgentResult(content="", success=False, error=str(e))
