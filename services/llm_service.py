"""LLM服务模块

提供大语言模型的服务封装，包括Agent管理和执行。
"""

import asyncio
import functools
import json
import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_openai import ChatOpenAI

from config import get_chat_config, LLMConfig, get_resilient_llm_config, LLMRole
from utils.logger import get_logger


class APIErrorClassifier:
    """API错误分类器，用于判断错误是否可重试"""
    NON_RETRYABLE_STATUS_CODES = {400, 401, 403, 422}
    RETRYABLE_SERVER_ERRORS = {404, 429, 500, 502, 503, 504}
    NETWORK_EXCEPTIONS = (
        ConnectionError,
        TimeoutError,
        OSError,
    )

    OPENAI_EXCEPTIONS = (
        'openai.RateLimitError',
        'openai.APIConnectionError',
        'openai.APITimeoutError',
        'openai.InternalServerError',
    )

    @classmethod
    def is_retryable_error(cls, error: Exception) -> bool:
        """判断错误是否可重试"""
        if hasattr(error, 'status_code'):
            status_code = error.status_code
            if status_code in cls.NON_RETRYABLE_STATUS_CODES:
                return False
            if status_code in cls.RETRYABLE_SERVER_ERRORS:
                return True

        if isinstance(error, cls.NETWORK_EXCEPTIONS):
            return True

        error_full_name = f"{type(error).__module__}.{type(error).__name__}"
        if error_full_name in cls.OPENAI_EXCEPTIONS:
            return True

        error_str = str(error).lower()
        retryable_keywords = [
            'connection', 'timeout', 'network', 'dns', 'unreachable',
            'rate limit', 'too many requests', 'service unavailable',
            'bad gateway', 'gateway timeout', 'internal server error'
        ]
        non_retryable_keywords = [
            'invalid api key', 'authentication', 'authorization',
            'permission denied', 'forbidden', 'invalid request'
        ]

        if any(keyword in error_str for keyword in retryable_keywords):
            return True

        if any(keyword in error_str for keyword in non_retryable_keywords):
            return False

        return False

    @classmethod
    def get_error_context(cls, error: Exception) -> dict:
        """获取错误的详细上下文信息"""
        context = {
            'error_type': type(error).__name__,
            'error_message': str(error),
            'is_retryable': cls.is_retryable_error(error),
            'status_code': None,
            'error_category': 'unknown'
        }

        if hasattr(error, 'status_code'):
            context['status_code'] = error.status_code
            if error.status_code in cls.NON_RETRYABLE_STATUS_CODES:
                context['error_category'] = 'authentication/permission'
            elif error.status_code in cls.RETRYABLE_SERVER_ERRORS:
                context['error_category'] = 'server_error'

        if isinstance(error, cls.NETWORK_EXCEPTIONS):
            context['error_category'] = 'network_error'

        error_full_name = f"{type(error).__module__}.{type(error).__name__}"
        if error_full_name in cls.OPENAI_EXCEPTIONS:
            context['error_category'] = 'openai_error'

        return context


def llm_retry_decorator(config: LLMConfig):
    """LLM API重试装饰器"""
    def decorator(func: Callable) -> Callable:
        async def async_wrapper(*args, **kwargs):
            last_error = None

            for attempt in range(config.max_retries + 1):
                try:
                    return await func(*args, **kwargs)
                except Exception as error:
                    last_error = error

                    error_context = APIErrorClassifier.get_error_context(error)

                    if not error_context['is_retryable']:
                        raise error

                    if attempt == config.max_retries:
                        print(f"❌ 重试失败 ({attempt + 1}/{config.max_retries + 1}): {error}")
                        raise error

                    delay = config.retry_base_delay * (config.retry_backoff_factor ** attempt)
                    if config.retry_jitter:
                        delay *= (0.5 + random.random() * 0.5)  # 50%-100%的随机抖动

                    print(f"🔄 重试 ({attempt + 1}/{config.max_retries + 1})")

                    await asyncio.sleep(delay)

            raise last_error

        return async_wrapper
    return decorator


class AgentRetryTracker:
    """Agent重试状态跟踪器"""

    def __init__(self):
        self.retry_attempts = {}
        self.retry_logs = []

    def start_retry_session(self, session_id: str, agent_name: str = None):
        """开始重试会话"""
        self.retry_attempts[session_id] = {
            "agent_name": agent_name,
            "start_time": time.time(),
            "attempts": 0,
            "errors": [],
            "error_categories": []
        }

    def log_retry_attempt(self, session_id: str, error: Exception, attempt: int, delay: float = None):
        """记录重试尝试"""
        if session_id not in self.retry_attempts:
            return

        error_context = APIErrorClassifier.get_error_context(error)

        self.retry_attempts[session_id]["attempts"] = attempt
        self.retry_attempts[session_id]["errors"].append({
            "attempt": attempt,
            "error": str(error),
            "error_type": type(error).__name__,
            "timestamp": time.time(),
            "delay_before_retry": delay,
            "error_context": error_context
        })

        if error_context['error_category'] not in self.retry_attempts[session_id]["error_categories"]:
            self.retry_attempts[session_id]["error_categories"].append(error_context['error_category'])

        log_entry = {
            "session_id": session_id,
            "agent_name": self.retry_attempts[session_id]["agent_name"],
            "attempt": attempt,
            "error": str(error),
            "error_type": type(error).__name__,
            "timestamp": time.time(),
            "delay_before_retry": delay,
            "error_context": error_context
        }
        self.retry_logs.append(log_entry)

    def end_retry_session(self, session_id: str, success: bool = False, final_error: Exception = None):
        """结束重试会话"""
        if session_id not in self.retry_attempts:
            return

        session = self.retry_attempts[session_id]
        session["end_time"] = time.time()
        session["duration"] = session["end_time"] - session["start_time"]
        session["success"] = success

        if final_error:
            session["final_error"] = {
                "error": str(final_error),
                "error_type": type(final_error).__name__,
                "error_context": APIErrorClassifier.get_error_context(final_error)
            }

        if not success and session.get("final_error"):
            print(f"❌ 最终失败 ({session['attempts']}/{session['attempts']}): {session['final_error']['error']}")

    def get_retry_summary(self, session_id: str) -> dict:
        """获取重试会话摘要"""
        return self.retry_attempts.get(session_id, {})

    def get_all_retry_logs(self) -> List[dict]:
        """获取所有重试日志"""
        return self.retry_logs

    def clear_old_logs(self, max_age_seconds: int = 3600):
        """清理旧的重试日志"""
        current_time = time.time()
        self.retry_logs = [
            log for log in self.retry_logs
            if current_time - log["timestamp"] <= max_age_seconds
        ]


class RetryableChatOpenAI:
    """带有重试机制的ChatOpenAI包装器"""

    def __init__(self, config: LLMConfig, retry_tracker: AgentRetryTracker = None, event_callback=None):
        self.config = config
        self.retry_tracker = retry_tracker or AgentRetryTracker()
        self.event_callback = event_callback
        self._llm = ChatOpenAI(
            model=config.model,
            api_key=config.api_key,
            base_url=config.base_url,
            temperature=config.temperature,
            streaming=config.streaming,
            max_tokens=config.max_tokens,
            max_retries=0,  # 我们自己控制重试
        )

    @llm_retry_decorator
    async def astream_events(self, *args, **kwargs):
        """带有重试机制的astream_events方法"""
        # 创建会话ID用于跟踪
        session_id = f"llm_session_{int(time.time() * 1000)}"
        self.retry_tracker.start_retry_session(session_id)

        # 发送重试会话开始事件
        if self.event_callback:
            from datetime import datetime
            await self.event_callback({
                "type": "retry_session_start",
                "timestamp": datetime.now().isoformat(),
                "session_id": session_id,
                "max_retries": self.config.max_retries,
                "data": {
                    "base_delay": self.config.retry_base_delay,
                    "backoff_factor": self.config.retry_backoff_factor,
                    "jitter": self.config.retry_jitter
                }
            })

        try:
            # 创建一个包装的函数来跟踪重试
            async def tracked_astream():
                return self._llm.astream_events(*args, **kwargs)

            # 使用装饰器包装的函数
            async def retry_wrapper():
                last_error = None

                for attempt in range(self.config.max_retries + 1):
                    try:
                        return await tracked_astream()
                    except Exception as error:
                        last_error = error

                        # 检查是否可重试
                        if not APIErrorClassifier.is_retryable_error(error):
                            self.retry_tracker.end_retry_session(session_id, False)
                            raise error

                        # 如果是最后一次尝试，直接抛出异常
                        if attempt == self.config.max_retries:
                            self.retry_tracker.end_retry_session(session_id, False)
                            raise error

                        # 计算延迟时间（指数退避 + 抖动）
                        delay = self.config.retry_base_delay * (self.config.retry_backoff_factor ** attempt)
                        if self.config.retry_jitter:
                            delay *= (0.5 + random.random() * 0.5)

                        # 记录重试尝试
                        self.retry_tracker.log_retry_attempt(session_id, error, attempt + 1, delay)

                        # 发送重试尝试事件
                        if self.event_callback:
                            from datetime import datetime
                            await self.event_callback({
                                "type": "retry_attempt",
                                "timestamp": datetime.now().isoformat(),
                                "session_id": session_id,
                                "attempt": attempt + 1,
                                "max_attempts": self.config.max_retries + 1,
                                "error": str(error),
                                "error_type": type(error).__name__,
                                "delay_before_retry": delay,
                                "data": {
                                    "retryable": True,
                                    "next_attempt_in": delay
                                }
                            })

                        await asyncio.sleep(delay)

            result = await retry_wrapper()
            self.retry_tracker.end_retry_session(session_id, True)

            # 发送重试会话成功事件
            if self.event_callback:
                from datetime import datetime
                await self.event_callback({
                    "type": "retry_session_end",
                    "timestamp": datetime.now().isoformat(),
                    "session_id": session_id,
                    "success": True,
                    "data": self.retry_tracker.get_retry_summary(session_id)
                })

            return result

        except Exception as e:
            self.retry_tracker.end_retry_session(session_id, False, e)

            # 发送重试会话失败事件
            if self.event_callback:
                from datetime import datetime
                error_context = APIErrorClassifier.get_error_context(e)
                await self.event_callback({
                    "type": "retry_session_end",
                    "timestamp": datetime.now().isoformat(),
                    "session_id": session_id,
                    "success": False,
                    "error": str(e),
                    "error_type": type(e).__name__,
                    "error_context": error_context,
                    "data": self.retry_tracker.get_retry_summary(session_id)
                })

            raise e

    def __getattr__(self, name):
        """代理其他方法到底层ChatOpenAI实例"""
        return getattr(self._llm, name)


@dataclass
class AgentResult:
    """Agent执行结果"""
    content: str
    success: bool
    error: Optional[str] = None


def _limit_tool_output_tokens(output: Any, token_limit: int = 10000) -> Any:
    """限制工具输出的Token数量，确保不超过指定限制。

    支持两种返回格式：
    1. 单个值：直接返回截断后的字符串
    2. 元组 (content, artifact)：保持元组格式，只截断content部分
    
    对于代码文件，采用智能截断策略，尝试保留函数定义区域。
    """
    import re
    
    # 检查是否是 (content, artifact) 元组格式
    is_tuple_format = isinstance(output, tuple) and len(output) == 2

    if is_tuple_format:
        content, artifact = output
        text = str(content)
    else:
        text = str(output)

    try:
        import tiktoken
        encoding = tiktoken.get_encoding("cl100k_base")
        token_count = len(encoding.encode(text))
    except Exception as e:
        token_count = len(text) // 4
        logger = get_logger(__name__)
        logger.debug(f"Token计数失败，使用估算: {e}")

    if token_count <= token_limit:
        return output

    # Log truncation
    logger = get_logger(__name__)
    logger.info(f"⚠️ 工具输出超过限制: {token_count} tokens > {token_limit} tokens，正在截断...")

    # 检测是否是代码文件（通过常见代码文件特征）
    is_code_file = False
    code_keywords = [
        r'\b(static\s+)?(int|void|char|struct|class|def|function)\s+\w+\s*\(',  # 函数定义
        r'#include\s*<',  # C/C++头文件
        r'^\s*//',  # 注释
        r'^\s*/\*',  # 多行注释
    ]
    for pattern in code_keywords:
        if re.search(pattern, text, re.MULTILINE):
            is_code_file = True
            break

    try:
        import tiktoken
        encoding = tiktoken.get_encoding("cl100k_base")
        tokens = encoding.encode(text)
        
        # 对于代码文件，尝试智能截断：保留更多中间区域
        if is_code_file and len(tokens) > token_limit * 2:
            # 代码文件：头部(15%) + 中间关键区域(70%) + 尾部(15%)
            # 这样可以更好地保留中间的函数定义
            first_token_count = int(token_limit * 0.15)
            middle_token_count = int(token_limit * 0.70)
            last_token_count = int(token_limit * 0.15)
            
            first_tokens = tokens[:first_token_count]
            # 中间区域：从总长度的25%到75%之间
            total_tokens = len(tokens)
            middle_start = int(total_tokens * 0.25)
            middle_end = middle_start + middle_token_count
            middle_tokens = tokens[middle_start:middle_end] if middle_end < total_tokens else tokens[middle_start:]
            last_tokens = tokens[-last_token_count:]
            
            first_part = encoding.decode(first_tokens)
            middle_part = encoding.decode(middle_tokens)
            last_part = encoding.decode(last_tokens)
            
            truncated_text = (
                f"[Token限制: 输出共{token_count}个Token，已截断至{token_limit}个Token（代码文件智能截断）]\n\n"
                f"{first_part}\n\n"
                f"... [中间区域: 行{middle_start//50}-{middle_end//50}] ...\n\n"
                f"{middle_part}\n\n"
                f"...\n\n"
                f"{last_part}"
            )
        else:
            # 非代码文件或小文件：使用原有策略
            first_token_count = int(token_limit * 0.4)
            last_token_count = int(token_limit * 0.6)

            first_tokens = tokens[:first_token_count]
            last_tokens = tokens[-last_token_count:]

            first_part = encoding.decode(first_tokens)
            last_part = encoding.decode(last_tokens)

            truncated_text = f"[Token限制: 输出共{token_count}个Token，已截断至{token_limit}个Token]\n\n{first_part}\n\n...\n\n{last_part}"
    except Exception as e:
        logger.warning(f"Token截断失败，使用字符截断: {e}")
        char_limit = token_limit * 4
        
        if is_code_file:
            # 代码文件：保留更多中间区域
            first_char_count = int(char_limit * 0.15)
            middle_char_count = int(char_limit * 0.70)
            last_char_count = int(char_limit * 0.15)
            
            total_chars = len(text)
            first_part = text[:first_char_count]
            middle_start = int(total_chars * 0.25)
            middle_end = middle_start + middle_char_count
            middle_part = text[middle_start:middle_end] if middle_end < total_chars else text[middle_start:]
            last_part = text[-last_char_count:]
            
            truncated_text = (
                f"[Token限制: 输出约{token_count}个Token，已截断（代码文件智能截断）]\n\n"
                f"{first_part}\n\n... [中间区域] ...\n\n{middle_part}\n\n...\n\n{last_part}"
            )
        else:
            first_char_count = int(char_limit * 0.4)
            last_char_count = int(char_limit * 0.6)

            first_part = text[:first_char_count]
            last_part = text[-last_char_count:]
            truncated_text = f"[Token限制: 输出约{token_count}个Token，已截断]\n\n{first_part}\n\n...\n\n{last_part}"

    # 如果原始输出是元组格式，保持元组格式返回
    if is_tuple_format:
        return (truncated_text, artifact)
    else:
        return truncated_text


def _format_tool_output(tool_name: str, output: Any) -> str:
    """简化常用工具的输出，便于终端阅读。"""

    text = str(output).strip()
    if not text:
        return "完成"

    if tool_name == "list_allowed_directories":
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        if lines and lines[0].lower().startswith("allowed"):
            lines = lines[1:]
        count = len(lines)
        return f"找到 {count} 个目录"

    if tool_name == "directory_tree":
        return "读取目录结构"

    if tool_name == "search_files":
        # 清理输出，移除可能的tool_call_id等元数据
        cleaned_text = text.split("' name=")[0] if "' name=" in text else text
        cleaned_text = cleaned_text.strip().strip("'\"")

        # 检查是否是 "No matches found" 的情况
        if "no matches found" in cleaned_text.lower() or cleaned_text.lower() == "no matches found":
            return "未找到匹配"

        lines = [line.strip() for line in cleaned_text.splitlines() if line.strip()]
        count = len(lines)
        if count == 0:
            return "未找到文件"
        elif count == 1:
            # 提取文件名
            try:
                filename = Path(lines[0]).name if lines[0] else "文件"
            except Exception:
                filename = "文件"
            return f"找到文件: {filename}"
        else:
            return f"找到 {count} 个文件"

    if tool_name == "ripgrep":
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        line_count = len(lines)
        if "no matches found" in text.lower() or text.lower() == "no matches found" or line_count == 0:
            return "未找到匹配"

        if line_count > 2000:
            first_part = lines[:1000]
            last_part = lines[-1000:]
            truncated_output = '\n'.join(first_part) + '\n...\n' + '\n'.join(last_part)

            feedback = f"搜索结果共{line_count}行，已截断显示前1000行和后1000行。\n"
            feedback += "建议：使用更精确的搜索参数（如 -m 限制匹配数、更具体的关键词）来获取更少的结果。\n\n"
            feedback += truncated_output

            return feedback
        else:
            if line_count == 1:
                return f"找到1个匹配: {lines[0]}"
            else:
                return f"找到{line_count}个匹配:\n" + '\n'.join(lines[:10]) + (f"\n... 还有{line_count-10}个匹配" if line_count > 10 else "")

    if tool_name == "read_text_file":
        return "读取文件"

    # 其他工具简化输出
    if len(text) > 100:
        return "完成"
    return text[:100]


def _print_detailed_tool_output(tool_name: str, output: Any) -> None:
    """打印工具输出的详细内容。"""
    import json
    import re

    # 如果输出为 None 或空字符串，跳过详细显示（空列表会显示"空目录"）
    if output is None or output == "":
        return

    # 检查是否是目录列表工具
    is_list_dir = (
        tool_name == "list_directory" or
        "list_directory" in tool_name or
        "listDirectory" in tool_name or
        tool_name == "directory_tree"
    )

    # 检查是否是文件读取工具
    is_read_file = (
        tool_name == "read_text_file" or
        tool_name == "read_file" or
        "read_file" in tool_name or
        "readFile" in tool_name or
        "read_text_file" in tool_name
    )

    if is_list_dir:
        try:
            output_str = str(output)
            content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
            if content_match:
                content = content_match.group(1)
                content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
                output_str = content

            try:
                dir_data = json.loads(output_str)
            except (json.JSONDecodeError, ValueError):
                if isinstance(output_str, str):
                    lines = output_str.strip().split('\n')
                    items = []
                    for line in lines:
                        line = line.strip()
                        if line.startswith('[DIR]'):
                            name = line[5:].strip()
                            items.append({'name': name, 'type': 'directory'})
                        elif line.startswith('[FILE]'):
                            name = line[6:].strip()
                            items.append({'name': name, 'type': 'file'})

                    if items:
                        dir_data = items
                    else:
                        raise ValueError("无法解析目录列表")
                else:
                    dir_data = output

            print("📁 目录列表:")

            if isinstance(dir_data, list):
                if len(dir_data) == 0:
                    print("   (空目录)")
                else:
                    for item in dir_data:
                        if isinstance(item, dict):
                            name = item.get('name', '')
                            item_type = item.get('type', '')
                            icon = "📂" if item_type == 'directory' else "📄"
                            suffix = "/" if item_type == 'directory' else ""
                            print(f"   {icon} {name}{suffix}")
                        else:
                            print(f"   📄 {item}")
            elif isinstance(dir_data, dict):
                # 处理树形结构
                def print_tree(data: dict, prefix: str = "", is_last: bool = True):
                    """递归打印目录树"""
                    if "type" in data:
                        if data["type"] == "directory":
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}📂 {data.get('name', '')}/")
                            if "children" in data:
                                children = list(data["children"].items())
                                for i, (name, child) in enumerate(children):
                                    is_last_child = i == len(children) - 1
                                    extension = "    " if is_last else "│   "
                                    print_tree(child, prefix + extension, is_last_child)
                        else:
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}📄 {data.get('name', '')}")
                print_tree(dir_data)
            else:
                print(f"   完整输出: {output}")
        except (json.JSONDecodeError, TypeError, ValueError) as e:
            # 如果所有解析都失败，尝试按行显示原始内容
            output_str = str(output)
            # 提取 content='...' 中的内容（处理转义的单引号）
            content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
            if content_match:
                content = content_match.group(1)
                # 处理转义字符
                content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
                lines = content.strip().split('\n')
                print("📁 目录列表:")
                for line in lines:
                    if line.strip():
                        # 尝试识别 [DIR] 和 [FILE] 标记
                        if line.strip().startswith('[DIR]'):
                            name = line.strip()[5:].strip()
                            print(f"   📂 {name}/")
                        elif line.strip().startswith('[FILE]'):
                            name = line.strip()[6:].strip()
                            print(f"   📄 {name}")
                        else:
                            print(f"   📄 {line.strip()}")
            elif '\n' in output_str:
                lines = output_str.strip().split('\n')
                print("📁 目录列表:")
                for line in lines:
                    if line.strip():
                        print(f"   📄 {line.strip()}")

    elif is_read_file:
        # 文件读取操作：格式化显示文件内容（显示前N行）
        MAX_PREVIEW_LINES = 30  # 最多显示30行

        output_str = str(output)

        # 处理可能包含 content='...' 格式的字符串（处理转义的单引号）
        content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
        if content_match:
            content = content_match.group(1)
            # 处理转义的换行符和其他转义字符
            content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
            output_str = content

        # 处理可能包含转义字符的字符串
        if '\\n' in output_str and '\n' not in output_str:
            output_str = output_str.replace('\\n', '\n')

        lines = output_str.split('\n')
        total_lines = len(lines)

        print(f"📖 文件内容 (共 {total_lines} 行):")
        print("-" * 60)

        # 显示前N行
        preview_lines = lines[:MAX_PREVIEW_LINES]
        for i, line in enumerate(preview_lines, 1):
            print(f"{i:4d} | {line}")

        if total_lines > MAX_PREVIEW_LINES:
            print(f"... (还有 {total_lines - MAX_PREVIEW_LINES} 行未显示)")

        print("-" * 60)


class MultiAgentAnalyzer:
    """用于漏洞分析工作流的多Agent分析器。"""

    def __init__(self, config: LLMConfig = None):
        """初始化多Agent分析器。"""
        # 优先使用外部传入；否则采用具备自动切换的配置（网络不好时自动换服务商）
        self.config = config or get_resilient_llm_config(LLMRole.CHAT)
        self.llm = None
        self.mcp_client = None
        self.tools = None
        self.retry_tracker = AgentRetryTracker()

    async def initialize(self, event_callback=None) -> None:
        """初始化LLM和MCP客户端以便在多个Agent之间复用。"""
        self.llm = RetryableChatOpenAI(self.config, self.retry_tracker, event_callback)

        self.mcp_client = MultiServerMCPClient(
            {
                "filesystem": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-filesystem",
                        str(Path.cwd()),
                    ],
                    "transport": "stdio",
                },
                "ripgrep": {
                    "command": "npx",
                    "args": ["-y", "mcp-ripgrep@latest"],
                    "transport": "stdio",
                },
            }
        )

        self.tools = await self.mcp_client.get_tools()
        
        # Add LSP Function Lookup Tool (uses ripgrep, no LSP engine needed)
        from tools.lsp_lookup_tool import LSPFunctionLookupTool
        lsp_lookup_tool = LSPFunctionLookupTool()
        self.tools.append(lsp_lookup_tool)

        # Wrap all tools with token limiting
        logger = get_logger(__name__)
        logger.info(f"正在包装 {len(self.tools)} 个工具（包含MCP工具和LSP查询工具）...")

        for t in self.tools:
            tool_name = getattr(t, 'name', 'unknown')

            # Wrap the _run and _arun methods (internal methods used by BaseTool)
            if hasattr(t, '_run') and callable(t._run):
                original_run = t._run

                def create_wrapped_run(original_func, name):
                    @functools.wraps(original_func)
                    def wrapped_run(*args, **kwargs):
                        logger = get_logger(__name__)
                        logger.debug(f"🔧 调用工具 {name} (sync)")
                        result = original_func(*args, **kwargs)
                        return _limit_tool_output_tokens(result)
                    return wrapped_run

                t._run = create_wrapped_run(original_run, tool_name)

            if hasattr(t, '_arun') and callable(t._arun):
                original_arun = t._arun

                def create_wrapped_arun(original_func, name):
                    @functools.wraps(original_func)
                    async def wrapped_arun(*args, **kwargs):
                        logger = get_logger(__name__)
                        logger.debug(f"🔧 调用工具 {name} (async)")
                        result = await original_func(*args, **kwargs)
                        return _limit_tool_output_tokens(result)
                    return wrapped_arun

                t._arun = create_wrapped_arun(original_arun, tool_name)
                logger.debug(f"  ✓ 已包装工具: {tool_name}")

            # Set error handling attributes
            try:
                setattr(t, "handle_tool_error", True)
            except Exception:
                pass
            try:
                setattr(t, "handle_validation_error", True)
            except Exception:
                pass

        logger.info(f"✓ 完成包装 {len(self.tools)} 个工具")

    async def run_agent(self, prompt: str, show_thinking: bool = True, event_callback=None, agent_name: str = None, agent_type: str = None) -> AgentResult:
        """使用给定的提示词运行单个Agent，可选择显示思考过程。"""
        try:
            if not self.llm or not self.tools:
                await self.initialize(event_callback)

            agent = create_agent(self.llm, self.tools)
            content_parts = []

            # 跟踪工具执行状态
            current_tool = None
            tool_start_time = {}
            output_started = False
            ai_streaming = False  # 跟踪AI是否正在流式输出

            async for event in agent.astream_events(
                {"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 100}
            ):
                event_name = event.get("event")

                # Phase 3.2: 推送 AGENT_THINKING 事件（当检测到思考标记）
                if event_callback and event_name == "on_agent_action":
                    from datetime import datetime
                    action = event.get("data", {}).get("action")
                    if action and hasattr(action, "tool"):
                        thinking_message = f"决定使用工具 '{action.tool}'"
                        await event_callback({
                            "type": "agent_thinking",
                            "timestamp": datetime.now().isoformat(),
                            "agent_name": agent_name,
                            "agent_type": agent_type,
                            "message": thinking_message,
                            "data": {
                                "tool": action.tool,
                                "tool_input": action.tool_input if hasattr(action, "tool_input") else None
                            }
                        })

                if event_callback and event_name == "on_tool_start":
                    from datetime import datetime
                    tool_name = event.get("name", "")
                    await event_callback({
                        "type": "agent_tool_call",
                        "timestamp": datetime.now().isoformat(),
                        "agent_name": agent_name,
                        "agent_type": agent_type,
                        "message": f"开始调用工具: {tool_name}",
                        "data": {
                            "tool_name": tool_name,
                            "event_data": event.get("data", {})
                        }
                    })

                if show_thinking:
                    if event_name == "on_agent_action":
                        action = event.get("data", {}).get("action")
                        if action and hasattr(action, "tool"):
                            print(f"🤔 AI思考: 决定使用工具 '{action.tool}'")
                            if hasattr(action, "tool_input") and action.tool_input:
                                print(f"   工具输入: {action.tool_input}")

                    elif event_name == "on_tool_start":
                        # 工具开始执行
                        tool_name = event.get("name", "")
                        current_tool = tool_name
                        # 如果AI正在流式输出，先换行
                        if ai_streaming:
                            print()
                            ai_streaming = False
                        print(f"🔧 {tool_name} → ", end="", flush=True)

                    elif event_name == "on_tool_end":
                        # 工具执行完成，在同一行显示结果
                        tool_name = event.get("name", "")
                        output = event.get("data", {}).get("output", "")
                        output_preview = _format_tool_output(tool_name, output)
                        status = None
                        try:
                            status = getattr(output, "status", None)
                        except Exception:
                            status = None
                        if status is None and isinstance(output, dict):
                            status = output.get("status")

                        if status == "error":
                            print(f"❌ {output_preview}")
                        else:
                            if "未找到" in output_preview or "no matches found" in str(output).lower():
                                print(f"⚠️ {output_preview}")
                            else:
                                print(f"✅ {output_preview}")

                        # 显示详细内容（针对特定工具）
                        _print_detailed_tool_output(tool_name, output)
                        current_tool = None

                if event_name == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join(
                                    [
                                        c.get("text", "")
                                        for c in chunk.content
                                        if isinstance(c, dict)
                                    ]
                                )
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            content_parts.append(text)

                            if event_callback:
                                from datetime import datetime
                                await event_callback({
                                    "type": "agent_thinking",
                                    "timestamp": datetime.now().isoformat(),
                                    "agent_name": agent_name,
                                    "agent_type": agent_type,
                                    "message": text,
                                    "data": {"stream_chunk": text}
                                })

                            if show_thinking:
                                # 第一次输出时添加分隔符
                                if not output_started:
                                    print("\n" + "="*50)
                                    output_started = True
                                print(text, end="", flush=True)
                                ai_streaming = True  # 标记AI正在流式输出

            final_content = "".join(content_parts)
            if show_thinking and output_started:
                print("\n" + "="*50)
                print("🎯 AI推理完成\n")

            return AgentResult(content=final_content, success=True)

        except Exception as e:
            if show_thinking:
                print(f"\n❌ 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))
