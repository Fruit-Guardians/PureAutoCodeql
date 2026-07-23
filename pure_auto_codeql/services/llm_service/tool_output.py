"""Format, truncate, and pretty-print agent tool output."""

import json
from pathlib import Path
from typing import Any

from pure_auto_codeql.utils.logger import get_logger


def _limit_tool_output_tokens(output: Any, token_limit: int = 10000, tool_name: str = None) -> Any:
    """限制工具输出的Token数量，确保不超过指定限制。

    支持两种返回格式：
    1. 单个值：直接返回截断后的字符串
    2. 元组 (content, artifact)：保持元组格式，只截断content部分

    对于代码文件，采用智能截断策略，尝试保留函数定义区域。

    Args:
        output: 工具输出结果
        token_limit: Token限制数量
        tool_name: 工具名称，用于特殊处理（如LSP MCP工具使用8000限制）
    """
    # LSP MCP 工具使用 8000 token 限制
    # 检测条件：
    # 1. 工具名包含 'language-server' (MCP服务器名称)
    # 2. 工具名包含 'lsp' (如 lsp_function_lookup)
    # 3. 工具名是常见的LSP操作 (definition, hover, references等)
    lsp_tool_keywords = ['language-server', 'lsp', 'definition', 'hover', 'references',
                         'symbols', 'completion', 'signature', 'document_symbol',
                         'workspace_symbol', 'goto_definition']

    if tool_name and any(keyword in tool_name.lower() for keyword in lsp_tool_keywords):
        token_limit = 8000
        logger = get_logger(__name__)
        logger.debug(f"󰢛 LSP MCP工具 '{tool_name}' 使用8000 Token限制")
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
    logger.info(f"󰀪 工具输出超过限制: {token_count} tokens > {token_limit} tokens，正在截断...")

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

    # 处理 ripgrep/search 工具（mcp-ripgrep 的工具名称是 "search"）
    if tool_name in ("ripgrep", "search"):
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

            print("󰉋 目录列表:")

            if isinstance(dir_data, list):
                if len(dir_data) == 0:
                    print("   (空目录)")
                else:
                    for item in dir_data:
                        if isinstance(item, dict):
                            name = item.get('name', '')
                            item_type = item.get('type', '')
                            icon = "󰉋" if item_type == 'directory' else "󰈙"
                            suffix = "/" if item_type == 'directory' else ""
                            print(f"   {icon} {name}{suffix}")
                        else:
                            print(f"   󰈙 {item}")
            elif isinstance(dir_data, dict):
                # 处理树形结构
                def print_tree(data: dict, prefix: str = "", is_last: bool = True):
                    """递归打印目录树"""
                    if "type" in data:
                        if data["type"] == "directory":
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}󰉋 {data.get('name', '')}/")
                            if "children" in data:
                                children = list(data["children"].items())
                                for i, (name, child) in enumerate(children):
                                    is_last_child = i == len(children) - 1
                                    extension = "    " if is_last else "│   "
                                    print_tree(child, prefix + extension, is_last_child)
                        else:
                            marker = "└── " if is_last else "├── "
                            print(f"{prefix}{marker}󰈙 {data.get('name', '')}")
                print_tree(dir_data)
            else:
                print(f"   完整输出: {output}")
        except (json.JSONDecodeError, TypeError, ValueError):
            # 如果所有解析都失败，尝试按行显示原始内容
            output_str = str(output)
            # 提取 content='...' 中的内容（处理转义的单引号）
            content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
            if content_match:
                content = content_match.group(1)
                # 处理转义字符
                content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
                lines = content.strip().split('\n')
                print("󰉋 目录列表:")
                for line in lines:
                    if line.strip():
                        # 尝试识别 [DIR] 和 [FILE] 标记
                        if line.strip().startswith('[DIR]'):
                            name = line.strip()[5:].strip()
                            print(f"   󰉋 {name}/")
                        elif line.strip().startswith('[FILE]'):
                            name = line.strip()[6:].strip()
                            print(f"   󰈙 {name}")
                        else:
                            print(f"   󰈙 {line.strip()}")
            elif '\n' in output_str:
                lines = output_str.strip().split('\n')
                print("󰉋 目录列表:")
                for line in lines:
                    if line.strip():
                        print(f"   󰈙 {line.strip()}")

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

    # 检查是否是 ripgrep/search 工具
    is_ripgrep = (
        tool_name == "ripgrep" or
        tool_name == "search" or
        "ripgrep" in tool_name.lower() or
        "search" in tool_name.lower()
    )

    if is_ripgrep:
        # 显示 ripgrep 搜索结果的详细信息
        output_str = str(output)

        # 提取 content='...' 中的内容
        content_match = re.search(r"content='((?:[^'\\]|\\.)*)'", output_str)
        if content_match:
            content = content_match.group(1)
            content = content.replace('\\n', '\n').replace('\\r', '\r').replace('\\t', '\t').replace("\\'", "'")
            output_str = content

        # 处理转义字符
        if '\\n' in output_str and '\n' not in output_str:
            output_str = output_str.replace('\\n', '\n')

        lines = [line.strip() for line in output_str.splitlines() if line.strip()]
        line_count = len(lines)

        if "no matches found" in output_str.lower() or line_count == 0:
            print("󰍉 搜索结果: 未找到匹配")
        else:
            print(f"󰍉 搜索结果: 找到 {line_count} 个匹配")
            if line_count <= 20:
                # 如果结果不多，显示所有结果
                print("-" * 60)
                for i, line in enumerate(lines, 1):
                    print(f"{i:3d} | {line}")
                print("-" * 60)
            else:
                # 如果结果很多，只显示前10个
                print("-" * 60)
                for i, line in enumerate(lines[:10], 1):
                    print(f"{i:3d} | {line}")
                print(f"... (还有 {line_count - 10} 个匹配未显示)")
                print("-" * 60)
