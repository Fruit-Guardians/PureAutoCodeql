from pathlib import Path
from typing import TYPE_CHECKING, Dict, Optional, List, Tuple
import json
import re
import os

from pure_auto_codeql.paths import prompts_dir

if TYPE_CHECKING:
    from pure_auto_codeql.utils.io import AgentResult
from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer


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
        self.prompts_dir = prompts_dir()
        
        # 默认使用通用提示词
        self.analysis_prompt_file = self.prompts_dir / "codeql_breakpoint_analysis.md"
        self.flowstep_prompt_file = self.prompts_dir / "codeql_breakpoint_flowstep.md"
        
        # 保留原始提示词作为备用
        self.prompt_file = prompt_file or (self.prompts_dir / "codeql_breakpoint_detect.md")

    def _get_prompt_file(self, base_name: str, language: str = "java") -> Path:
        """
        根据语言获取对应的提示词文件。
        例如：codeql_breakpoint_analysis.md -> codeql_breakpoint_analysis_python.md
        如果特定语言的文件不存在，则回退到通用文件。
        """
        lang = (language or "java").lower()
        
        # 处理别名
        if lang in ["py"]: lang = "python"
        if lang in ["js"]: lang = "javascript"
        if lang in ["ts"]: lang = "typescript"
        
        # 尝试查找特定语言的提示词文件
        # 格式：base_name_language.md (例如 codeql_breakpoint_analysis_python.md)
        file_stem = Path(base_name).stem
        lang_specific_name = f"{file_stem}_{lang}.md"
        lang_specific_path = self.prompts_dir / lang_specific_name
        
        if lang_specific_path.exists():
            return lang_specific_path
            
        # 如果不存在，尝试查找旧的通用文件
        return self.prompts_dir / base_name

    def _load_prompt(self, prompt_file: Optional[Path] = None) -> str:
        """从markdown文件加载提示词模板内容。"""
        try:
            file_to_load = prompt_file or self.prompt_file
            return file_to_load.read_text(encoding="utf-8")
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
            
            # 检查父目录是否存在，如果不存在则创建
            parent_dir = os.path.dirname(abs_path)
            if parent_dir and not os.path.exists(parent_dir):
                try:
                    os.makedirs(parent_dir, exist_ok=True)
                except Exception as e:
                    return f"错误: 无法创建父目录 {parent_dir}: {str(e)}"
                
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

    def _extract_unique_files(self, nodes: List[Dict]) -> List[str]:
        """从节点列表中提取唯一的文件路径。"""
        files = set()
        for node in nodes:
            file_path = node.get('file', '') or node.get('file_path', '')
            if file_path:
                files.add(file_path)
        return list(files)
    
    def _read_multiple_source_files(self, file_paths: List[str], max_files: int = 5) -> str:
        """读取多个源文件并格式化为易读的文本。
        
        参数:
            file_paths: 文件路径列表
            max_files: 最多读取的文件数量（防止过大）
        
        返回:
            格式化的源码内容
        """
        if not file_paths:
            return "未找到相关源文件路径"
        
        # 限制文件数量
        file_paths = file_paths[:max_files]
        
        source_contents = []
        for file_path in file_paths:
            content = self._read_source_file(file_path)
            
            # 检查是否读取成功
            if content and not content.startswith("错误:") and not content.startswith("读取文件"):
                # 限制单个文件大小（最多5000行，防止超出上下文）
                lines = content.split('\n')
                if len(lines) > 5000:
                    content = '\n'.join(lines[:5000]) + f"\n\n... (文件过大，已截断，总共{len(lines)}行)"
                
                source_contents.append(f"""
### 文件: {file_path}
```
{content}
```
""")
            else:
                source_contents.append(f"""
### 文件: {file_path}
```
{content}
```
""")
        
        if source_contents:
            return "\n".join(source_contents)
        else:
            return "所有源文件读取失败"

    def build_analysis_prompt(
        self,
        codeql_results: str,
        source_nodes: Optional[List[Dict]] = None,
        sink_nodes: Optional[List[Dict]] = None,
        language: str = "java",
    ) -> str:
        """构建断流点分析提示词（增强版：主动读取源文件）。
        
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
        
        # 构建源码目录信息
        if self.project_name:
            source_dir_info = f"projects/{self.project_name}/source_code"
        else:
            source_dir_info = "projects/xxx/source_code (请根据实际情况替换xxx为项目名称)"
        
        # 【增强】主动提取并读取源文件
        all_nodes = (source_nodes or []) + (sink_nodes or [])
        file_paths = self._extract_unique_files(all_nodes)
        
        # 读取源文件内容
        preloaded_source_code = self._read_multiple_source_files(file_paths)
        
        print(f"🔍 [BreakpointAgent] 主动读取了 {len(file_paths)} 个源文件用于分析")
        
        # 动态选择语言特定的提示词文件
        prompt_file = self._get_prompt_file("codeql_breakpoint_analysis.md", language)
        template = self._load_prompt(prompt_file)
        
        values = {
            "CODEQL_RESULTS": codeql_results or "",
            "SOURCE_NODES": json.dumps(source_nodes, indent=2) if source_nodes else "[]",
            "SINK_NODES": json.dumps(sink_nodes, indent=2) if sink_nodes else "[]",
            "LANGUAGE": language or "java",
            "SOURCE_DIR": source_dir_info,
            "PRELOADED_SOURCE_CODE": preloaded_source_code,  # 新增：预加载的源码
        }
        return self._fill_placeholders(template, values)

    def build_flowstep_prompt(
        self,
        breakpoint_analysis: str,
        language: str = "java",
    ) -> str:
        """构建isAdditionalFlowStep条件生成提示词。
        
        参数:
            breakpoint_analysis: 断流点分析结果（JSON格式）
            language: 目标编程语言
        """
        # 动态选择语言特定的提示词文件
        prompt_file = self._get_prompt_file("codeql_breakpoint_flowstep.md", language)
        template = self._load_prompt(prompt_file)
        
        values = {
            "BREAKPOINT_ANALYSIS": breakpoint_analysis or "",
            "LANGUAGE": language or "java",
        }
        return self._fill_placeholders(template, values)

    def build_prompt(
        self,
        codeql_results: str,
        source_nodes: Optional[List[Dict]] = None,
        sink_nodes: Optional[List[Dict]] = None,
        language: str = "java",
    ) -> str:
        """构建包含CodeQL结果和节点信息的最终提示词（保留原始方法以保持兼容性）。
        
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

    async def analyze_breakpoints(
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
        """分析CodeQL查询结果以检测断流点（第一步）。
        
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
            _agent_name = agent_name or "CodeQL断流点分析代理"
            _agent_type = agent_type or "codeql_breakpoint_analysis"
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始CodeQL断流点分析（{language}）",
                    "data": {"language": language}
                })
            
            # 如果未提供节点则提取
            if source_nodes is None or sink_nodes is None:
                extracted_source, extracted_sink = self._extract_nodes_from_results(codeql_results)
                source_nodes = source_nodes or extracted_source
                sink_nodes = sink_nodes or extracted_sink
            
            prompt = self.build_analysis_prompt(
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
                    "message": f"CodeQL断流点分析完成（{language}）",
                    "data": {"success": result.success, "language": language}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL断流点分析代理"
                _agent_type = agent_type or "codeql_breakpoint_analysis"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL断流点分析失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))

    async def generate_flowstep(
        self,
        breakpoint_analysis: str,
        language: str = "java",
        show_thinking: bool = False,
        event_callback = None,
        agent_name: str = None,
        agent_type: str = None,
    ) -> "AgentResult":
        """基于断流点分析结果生成isAdditionalFlowStep条件（第二步）。
        
        参数:
            breakpoint_analysis: 断流点分析结果（JSON格式）
            language: 目标编程语言
            show_thinking: 是否显示思考过程
            event_callback: 事件回调
            agent_name: 代理名称
            agent_type: 代理类型
        """
        try:
            _agent_name = agent_name or "CodeQL流步骤生成代理"
            _agent_type = agent_type or "codeql_flowstep_generation"
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始生成isAdditionalFlowStep条件（{language}）",
                    "data": {"language": language}
                })
            
            prompt = self.build_flowstep_prompt(
                breakpoint_analysis=breakpoint_analysis,
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
                    "message": f"isAdditionalFlowStep条件生成完成（{language}）",
                    "data": {"success": result.success, "language": language}
                })
            
            return result
            
        except Exception as e:
            if event_callback:
                from datetime import datetime
                _agent_name = agent_name or "CodeQL流步骤生成代理"
                _agent_type = agent_type or "codeql_flowstep_generation"
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"isAdditionalFlowStep条件生成失败: {str(e)}",
                    "data": {"error": str(e)}
                })
            
            from dataclasses import dataclass

            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(content="", success=False, error=str(e))

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
        """分析CodeQL查询结果以检测断点并生成isAdditionalFlowStep条件（两步流程）。
        
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
            
            # 第一步：分析断流点
            analysis_result = await self.analyze_breakpoints(
                codeql_results=codeql_results,
                language=language,
                source_nodes=source_nodes,
                sink_nodes=sink_nodes,
                show_thinking=show_thinking,
                event_callback=event_callback,
                agent_name=f"{_agent_name}-分析",
                agent_type=f"{_agent_type}-analysis"
            )
            
            if not analysis_result.success:
                return analysis_result
            
            # 第二步：生成isAdditionalFlowStep条件
            flowstep_result = await self.generate_flowstep(
                breakpoint_analysis=analysis_result.content,
                language=language,
                show_thinking=show_thinking,
                event_callback=event_callback,
                agent_name=f"{_agent_name}-流步骤",
                agent_type=f"{_agent_type}-flowstep"
            )
            
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"CodeQL断点检测分析完成（{language}）",
                    "data": {"success": flowstep_result.success, "language": language}
                })
            
            return flowstep_result
            
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
