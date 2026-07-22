import json
import logging
from typing import TYPE_CHECKING, Any, Dict, List

if TYPE_CHECKING:
    from dataclasses import dataclass

    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    class MultiAgentAnalyzer:
        pass

from pure_auto_codeql.prompts.path_analysis_prompts import build_path_analysis_prompt

logger = logging.getLogger(__name__)


class PathAnalysisAgent:
    """路径分析代理，用于分析源点到汇点的路径并识别 isAdditionalFlowStep 点。"""

    def __init__(self, analyzer: "MultiAgentAnalyzer", language: str = "java", source_root: str = ""):
        """
        初始化路径分析代理。

        Args:
            analyzer: MultiAgentAnalyzer 实例
            language: 目标语言 (java, python, cpp)
            source_root: 源码根目录绝对路径
        """
        self.analyzer = analyzer
        self.language = language
        self.source_root = source_root
        self.flow_step_types = [
            "assignment",
            "deserialization",
            "arithmetic",
            "offset",
            "type_conversion"
        ]

    def _get_language_patterns(self, language: str) -> Dict[str, List[str]]:
        """获取特定语言的流步骤检测模式。"""
        patterns = {
            "java": {
                "assignment": ["field assignment", "variable assignment", "method chaining"],
                "deserialization": ["ObjectInputStream.readObject", "XMLDecoder.readObject", "JSON parsing"],
                "arithmetic": ["addition", "subtraction", "multiplication", "increment", "decrement"],
                "offset": ["array indexing", "pointer arithmetic", "buffer offset"],
                "type_conversion": ["casting", "type coercion", "wrapper conversion"]
            },
            "python": {
                "assignment": ["variable assignment", "dictionary operation", "list operation", "attribute access"],
                "deserialization": ["pickle.loads", "json.loads", "yaml.load", "eval"],
                "arithmetic": ["addition", "subtraction", "multiplication", "division"],
                "offset": ["list indexing", "slice operation", "array access"],
                "type_conversion": ["int()", "str()", "float()", "type casting"]
            },
            "cpp": {
                "assignment": ["pointer assignment", "struct field access", "reference assignment"],
                "deserialization": ["memcpy", "memmove", "strcpy", "binary parsing"],
                "arithmetic": ["addition", "subtraction", "multiplication", "overflow-prone operations"],
                "offset": ["pointer arithmetic", "array indexing", "buffer offset calculation"],
                "type_conversion": ["static_cast", "reinterpret_cast", "C-style cast"]
            }
        }
        return patterns.get(language, patterns["java"])

    def _validate_path_data(self, path_data: Dict[str, Any]) -> bool:
        """验证路径数据格式是否正确。"""
        required_fields = ["source_function", "sink_function", "call_chain"]

        for field in required_fields:
            if field not in path_data:
                logger.warning(f"路径数据缺少必需字段: {field}")
                return False

        # 验证 source_function 和 sink_function 结构
        for func_field in ["source_function", "sink_function"]:
            func = path_data[func_field]
            if not isinstance(func, dict):
                logger.warning(f"{func_field} 必须是字典类型")
                return False
            if "name" not in func:
                logger.warning(f"{func_field} 缺少必需字段: name")
                return False

        # 验证 call_chain 是列表
        if not isinstance(path_data["call_chain"], list):
            logger.warning("call_chain 必须是列表类型")
            return False

        return True

    def _parse_flow_steps_result(self, result_content: str) -> List[Dict[str, Any]]:
        """解析流步骤分析结果。

        Returns:
            List[Dict]: 流步骤候选项列表
        """
        import re

        content = result_content.strip()
        data = None

        # 策略1: 直接解析
        try:
            data = json.loads(content)
        except json.JSONDecodeError:
            pass

        # 策略2: 提取 Markdown 代码块
        if data is None:
            match = re.search(r"```(?:json)?\s*(.*?)\s*```", content, re.DOTALL)
            if match:
                try:
                    data = json.loads(match.group(1).strip())
                except json.JSONDecodeError:
                    pass

        # 策略3: 提取最外层 JSON 对象
        if data is None:
            match = re.search(r"(\{.*\})", content, re.DOTALL)
            if match:
                try:
                    data = json.loads(match.group(1).strip())
                except json.JSONDecodeError:
                    pass

        if data is None:
            logger.error("无法从流步骤结果中提取有效的 JSON")
            logger.debug(f"原始内容: {result_content[:500]}...")
            return []

        try:
            # 提取 flow_steps
            flow_steps = data.get("flow_steps", [])
            logger.info(f"提取到 {len(flow_steps)} 个流步骤候选项")

            # 验证每个流步骤
            valid_steps = []
            for i, step in enumerate(flow_steps):
                if self._validate_flow_step(step):
                    valid_steps.append(step)
                    logger.debug(f"流步骤 {i+1} 验证通过: {step.get('type')} at {step.get('location')}")
                else:
                    logger.warning(f"流步骤 {i+1} 验证失败，已跳过")

            if len(valid_steps) < len(flow_steps):
                logger.warning(f"共 {len(flow_steps)} 个流步骤，{len(valid_steps)} 个通过验证")

            return valid_steps

        except json.JSONDecodeError as e:
            logger.error(f"JSON 解析失败: {e}")
            logger.debug(f"原始内容: {result_content[:500]}...")
            return []
        except Exception as e:
            logger.error(f"解析流步骤结果时出错: {e}")
            return []

    def _validate_flow_step(self, flow_step: Dict[str, Any]) -> bool:
        """验证单个流步骤数据格式。"""
        required_fields = ["type", "description", "location", "pattern", "confidence"]

        for field in required_fields:
            if field not in flow_step:
                logger.warning(f"流步骤缺少必需字段: {field}")
                return False

        # 验证 type 是否在支持的类型列表中
        if flow_step["type"] not in self.flow_step_types:
            logger.warning(f"不支持的流步骤类型: {flow_step['type']}")
            return False

        # 验证 confidence 是否为有效值
        valid_confidences = ["high", "medium", "low"]
        if flow_step["confidence"] not in valid_confidences:
            logger.warning(f"无效的置信度级别: {flow_step['confidence']}")
            return False

        return True

    async def analyze_path(
        self,
        path_data: Dict[str, Any],
        show_thinking: bool = True,
        event_callback=None,
        agent_name: str = None,
        agent_type: str = None
    ) -> "AgentResult":
        """
        分析单条源点到汇点的路径，识别 isAdditionalFlowStep 点。

        Args:
            path_data: 路径数据，包含 source_function, sink_function, call_chain, transformations
            show_thinking: 是否显示思考过程
            event_callback: 事件回调函数
            agent_name: Agent 名称（用于事件回调）
            agent_type: Agent 类型（用于事件回调）

        Returns:
            AgentResult: 包含流步骤候选项的 JSON 结果
        """
        try:
            _agent_name = agent_name or "Path Analysis Agent"
            _agent_type = agent_type or "path_analysis"

            # 检查语言是否支持
            if self.language.lower() != "java":
                logger.info(f"当前语言 {self.language} 不支持路径分析，跳过")
                from dataclasses import dataclass
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(
                    content=json.dumps({"flow_steps": []}),
                    success=True
                )

            # 推送 AGENT_START 事件
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_start",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"开始路径分析: {path_data.get('source_function', {}).get('name', 'unknown')} -> {path_data.get('sink_function', {}).get('name', 'unknown')}",
                    "data": {"language": self.language}
                })

            # 验证路径数据
            if not self._validate_path_data(path_data):
                error_msg = "路径数据格式无效"
                logger.error(error_msg)

                if event_callback:
                    from datetime import datetime
                    await event_callback({
                        "type": "error",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": _agent_name,
                        "agent_type": _agent_type,
                        "message": error_msg,
                        "data": {"error": error_msg}
                    })

                from dataclasses import dataclass
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                return AgentResult(
                    content=json.dumps({"flow_steps": []}),
                    success=False,
                    error=error_msg
                )

            # 构建提示词
            language_patterns = self._get_language_patterns(self.language)
            prompt = build_path_analysis_prompt(
                language=self.language,
                path_data=path_data,
                language_patterns=language_patterns,
                source_root=self.source_root
            )

            # 运行分析
            result = await self.analyzer.run_agent(
                prompt,
                show_thinking=show_thinking,
                event_callback=event_callback
            )

            # 解析结果
            if result.success and result.content:
                flow_steps = self._parse_flow_steps_result(result.content)

                logger.info(f"路径分析完成: 识别到 {len(flow_steps)} 个流步骤")

                # 构建结构化结果
                structured_result = {
                    "source_function": path_data.get("source_function"),
                    "sink_function": path_data.get("sink_function"),
                    "flow_steps": flow_steps
                }

                from dataclasses import dataclass
                @dataclass
                class AgentResult:
                    content: str
                    success: bool
                    error: str = None

                result = AgentResult(
                    content=json.dumps(structured_result, ensure_ascii=False, indent=2),
                    success=True
                )

            # 推送完成事件
            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "agent_complete",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": "路径分析完成",
                    "data": {"success": result.success}
                })

            return result

        except Exception as exc:
            logger.exception("路径分析过程中发生异常")

            if event_callback:
                from datetime import datetime
                await event_callback({
                    "type": "error",
                    "timestamp": datetime.now().isoformat(),
                    "agent_name": _agent_name,
                    "agent_type": _agent_type,
                    "message": f"路径分析失败: {str(exc)}",
                    "data": {"error": str(exc)}
                })

            from dataclasses import dataclass
            @dataclass
            class AgentResult:
                content: str
                success: bool
                error: str = None

            return AgentResult(
                content=json.dumps({"flow_steps": []}),
                success=False,
                error=f"路径分析异常: {str(exc)}"
            )

    async def identify_flow_steps(
        self,
        paths: List[Dict[str, Any]],
        show_thinking: bool = True,
        event_callback=None
    ) -> Dict[str, Any]:
        """
        批量分析多条路径，识别所有 isAdditionalFlowStep 点。

        Args:
            paths: 路径数据列表
            show_thinking: 是否显示思考过程
            event_callback: 事件回调函数

        Returns:
            Dict: 包含所有路径的流步骤分析结果
        """
        try:
            logger.info(f"开始批量路径分析，共 {len(paths)} 条路径")

            all_flow_steps = []
            successful_paths = 0
            failed_paths = 0

            for i, path_data in enumerate(paths):
                logger.info(f"分析路径 {i+1}/{len(paths)}")

                result = await self.analyze_path(
                    path_data=path_data,
                    show_thinking=show_thinking,
                    event_callback=event_callback
                )

                if result.success:
                    try:
                        path_result = json.loads(result.content)
                        flow_steps = path_result.get("flow_steps", [])
                        all_flow_steps.extend(flow_steps)
                        successful_paths += 1
                    except json.JSONDecodeError:
                        logger.error(f"路径 {i+1} 结果解析失败")
                        failed_paths += 1
                else:
                    failed_paths += 1
                    logger.warning(f"路径 {i+1} 分析失败: {result.error}")

            logger.info(f"批量路径分析完成: {successful_paths} 成功, {failed_paths} 失败, 共识别 {len(all_flow_steps)} 个流步骤")

            return {
                "total_paths": len(paths),
                "successful_paths": successful_paths,
                "failed_paths": failed_paths,
                "total_flow_steps": len(all_flow_steps),
                "flow_steps": all_flow_steps
            }

        except Exception as exc:
            logger.exception("批量路径分析过程中发生异常")
            return {
                "total_paths": len(paths),
                "successful_paths": 0,
                "failed_paths": len(paths),
                "total_flow_steps": 0,
                "flow_steps": [],
                "error": str(exc)
            }
