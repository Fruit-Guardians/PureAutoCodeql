from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional, List, Tuple
import json
import re
import os

if TYPE_CHECKING:
    from utils.io import AgentResult
from services.llm_service import MultiAgentAnalyzer


class CodeQLBreakpointAgent:
    """
    通过固定断流点寻找的codeql语句查询的回显结果，来找出断点并且编写断点连接条件
    """

    def __init__(
        self, 
        analyzer: "MultiAgentAnalyzer", 
        prompt_file: Optional[Path] = None,
        source_root: str = "projects",
        project_name: str = None
    ):
        self.analyzer = analyzer
        self.source_root = source_root
        self.project_name = project_name
        # 始终使用断点检测提示词
        self.prompt_file = prompt_file or (
            Path(__file__).resolve().parent.parent.parent / "prompts" / "codeql_breakpoint_detect.md"
        )

    def _load_prompt(self) -> str:
        """从markdown文件加载提示词模板内容。"""
        try:
            return self.prompt_file.read_text(encoding="utf-8")
        except Exception as e:
            return f"加载提示词文件时出错: {e}"

    @staticmethod
    def _fill_placeholders(template: str, values: Dict[str, Optional[str]]) -> str:
        """用提供的值替换模板中的[[KEY]]占位符。"""
        result = template
        for k, v in (values or {}).items():
            token = f"[[{k}]]"
            result = result.replace(token, (v or ""))
        return result

    def _read_source_file(self, file_path: str, context_lines: int = 5) -> str:
        """读取断点周围的源码文件内容及其上下文。"""
        try:
            # 构建源码目录路径
            if self.project_name:
                # 如果指定了项目名称，使用 projects/xxx/source_code 目录
                source_dir = os.path.join(self.source_root, self.project_name, "source_code")
            else:
                # 否则尝试从文件路径中提取项目名称
                # 假设文件路径格式为 projects/xxx/source_code/...
                parts = file_path.split(os.sep)
                if "projects" in parts:
                    projects_idx = parts.index("projects")
                    if projects_idx + 2 < len(parts):
                        # 提取项目名称并构建源码目录
                        project_name = parts[projects_idx + 1]
                        source_dir = os.path.join(self.source_root, project_name, "source_code")
                    else:
                        source_dir = self.source_root
                else:
                    source_dir = self.source_root
            
            # 将相对路径转换为绝对路径
            if not os.path.isabs(file_path):
                # 如果文件路径已经包含 projects/xxx/source_code，直接使用
                if file_path.startswith("projects") and "source_code" in file_path:
                    abs_path = file_path
                else:
                    # 否则将文件路径与源码目录结合
                    abs_path = os.path.join(source_dir, file_path)
            else:
                abs_path = file_path
                
            if not os.path.exists(abs_path):
                return f"错误: 在{abs_path}处未找到文件"
                
            with open(abs_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()
                
            # 目前返回所有行，可以增强为返回特定行范围
            return ''.join(lines)
        except Exception as e:
            return f"读取文件{file_path}时出错: {str(e)}"

    def _extract_nodes_from_results(self, codeql_results: str) -> Tuple[List[Dict], List[Dict]]:
        """从CodeQL查询结果中提取源节点和汇节点。"""
        source_nodes = []
        sink_nodes = []
        
        try:
            # 首先尝试解析为JSON
            if codeql_results.strip().startswith('{') or codeql_results.strip().startswith('['):
                results = json.loads(codeql_results)
                # 处理不同的结果格式
                if isinstance(results, list):
                    for item in results:
                        if 'source' in item or 'src' in item:
                            source_nodes.append(item)
                        if 'sink' in item or 'dst' in item:
                            sink_nodes.append(item)
                elif isinstance(results, dict):
                    if 'sources' in results:
                        source_nodes = results['sources']
                    if 'sinks' in results:
                        sink_nodes = results['sinks']
            else:
                # 尝试从文本格式中提取
                lines = codeql_results.split('\n')
                for line in lines:
                    if 'source' in line.lower() or 'src' in line.lower():
                        # 简单提取 - 可以用正则表达式增强
                        parts = line.split('|')
                        if len(parts) >= 3:
                            source_nodes.append({
                                'file': parts[0].strip(),
                                'line': parts[1].strip(),
                                'content': parts[2].strip()
                            })
                    elif 'sink' in line.lower() or 'dst' in line.lower():
                        parts = line.split('|')
                        if len(parts) >= 3:
                            sink_nodes.append({
                                'file': parts[0].strip(),
                                'line': parts[1].strip(),
                                'content': parts[2].strip()
                            })
        except Exception as e:
            # 如果解析失败，返回空列表
            pass
            
        return source_nodes, sink_nodes

    def _get_source_context(self, nodes: List[Dict]) -> str:
        """获取节点的源码上下文。"""
        context_parts = []
        
        for node in nodes:
            file_path = node.get('file', '')
            line_num = node.get('line', '')
            
            if file_path:
                content = self._read_source_file(file_path)
                context_parts.append(f"### 文件: {file_path}")
                context_parts.append(f"行号: {line_num}")
                context_parts.append("```")
                context_parts.append(content)
                context_parts.append("```")
                context_parts.append("")
        
        return '\n'.join(context_parts)

    def build_prompt(
        self,
        codeql_results: str,
        source_nodes: Optional[List[Dict]] = None,
        sink_nodes: Optional[List[Dict]] = None,
        language: str = "java",
    ) -> str:
        """构建包含CodeQL结果和节点信息的最终提示词。
        
        参数:
            codeql_results: CodeQL查询执行的结果
            source_nodes: 源节点列表(可选，如果未提供将从结果中提取)
            sink_nodes: 汇节点列表(可选，如果未提供将从结果中提取)
            language: 目标编程语言
        """
        # 如果未提供节点则提取
        if source_nodes is None or sink_nodes is None:
            extracted_source, extracted_sink = self._extract_nodes_from_results(codeql_results)
            source_nodes = source_nodes or extracted_source
            sink_nodes = sink_nodes or extracted_sink
        
        # 获取源码上下文
        source_context = self._get_source_context(source_nodes + sink_nodes)
        
        # 构建源码目录信息
        if self.project_name:
            source_dir_info = f"projects/{self.project_name}/source_code"
        else:
            source_dir_info = "projects/xxx/source_code (请根据实际情况替换xxx为项目名称)"
        
        template = self._load_prompt()
        values = {
            "CODEQL_RESULTS": codeql_results or "",
            "SOURCE_NODES": json.dumps(source_nodes, indent=2) if source_nodes else "[]",
            "SINK_NODES": json.dumps(sink_nodes, indent=2) if sink_nodes else "[]",
            "LANGUAGE": language or "java",
            "SOURCE_CONTEXT": source_context,
            "SOURCE_DIR": source_dir_info,
        }
        return self._fill_placeholders(template, values)

    async def detect_breakpoints(
        self,
        codeql_results: str,
        language: str = "java",
        source_nodes: Optional[List[Dict]] = None,
        sink_nodes: Optional[List[Dict]] = None,
        show_thinking: bool = False,
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """分析CodeQL查询结果以检测断点并生成isAdditionalFlowStep条件。
        
        参数:
            codeql_results: CodeQL查询执行的结果
            language: 目标编程语言
            source_nodes: 源节点列表(可选)
            sink_nodes: 汇节点列表(可选)
            show_thinking: 是否显示思考过程
            event_callback: 事件回调
            agent_name: 代理名称
            agent_type: 代理类型
        """
        try:
            _agent_name = agent_name or "CodeQL断点检测代理"
            _agent_type = agent_type or "codeql_breakpoint_detection"
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始CodeQL断点检测分析（{language}）",
                    "data": {"language": language}
                })
            
            # 如果未提供节点则提取
            if source_nodes is None or sink_nodes is None:
                extracted_source, extracted_sink = self._extract_nodes_from_results(codeql_results)
                source_nodes = source_nodes or extracted_source
                sink_nodes = sink_nodes or extracted_sink
            
            prompt = self.build_prompt(
                codeql_results=codeql_results,
                source_nodes=source_nodes,
                sink_nodes=sink_nodes,
                language=language,
            )
            
            result = await self.analyzer.run_agent(
                prompt, 
                show_thinking=show_thinking, 
                event_callback=event_callback
            )
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL断点检测分析完成（{language}）",
                    "data": {"success": result.success, "language": language}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL断点检测代理"
                _agent_type = agent_type or "codeql_breakpoint_detection"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL断点检测分析失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))