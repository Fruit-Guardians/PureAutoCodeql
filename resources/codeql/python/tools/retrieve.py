"""
CodeQL 知识库检索工具

该工具用于从 CodeQL 知识库中检索条目，支持按标签过滤和多种输出格式。
主要功能包括：
- 从不同类型的知识库文件中加载数据（模块、辅助函数、模板、用例、错误）
- 根据标签进行过滤检索
- 支持文本和JSON两种输出格式
- 可限制每个部分的返回结果数量

使用示例：
python retrieve.py --tags injection --sections modules cases --limit 10 --format json
"""

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple


KB_DIR = Path(__file__).resolve().parent.parent / "knowledge_base"
DEFAULT_SECTIONS = ("modules", "helpers", "templates", "cases", "errors")
CORE_MODULE_IDS = [
    "module:dataflow",
    "module:tainttracking",
    "module:remote-flow-sources",
]


def load_section(section: str) -> List[Dict[str, Any]]:
    """
    从知识库文件中加载指定部分的数据

    Args:
        section (str): 知识库部分名称，如 "modules", "helpers", "templates", "cases", "errors"

    Returns:
        List[Dict[str, Any]]: 该部分的所有条目数据列表

    Raises:
        FileNotFoundError: 当指定的知识库文件不存在时抛出
        ValueError: 当知识库文件内容不是JSON列表格式时抛出
    """
    path = KB_DIR / f"{section}.json"
    if not path.is_file():
        raise FileNotFoundError(f"Knowledge base file not found: {path}")
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
        if not isinstance(data, list):
            raise ValueError(f"Knowledge base file {path} must contain a JSON list.")
        return data


def tag_filter(items: Iterable[Dict[str, Any]], tags: List[str]) -> List[Dict[str, Any]]:
    """
    根据标签过滤知识库条目

    Args:
        items (Iterable[Dict[str, Any]]): 待过滤的知识库条目集合
        tags (List[str]): 过滤标签列表，如果为空则返回所有条目

    Returns:
        List[Dict[str, Any]]: 包含至少一个匹配标签的条目列表

    Note:
        标签匹配不区分大小写，只要条目的标签与过滤标签有任意一个匹配即被选中
    """
    if not tags:
        return list(items)
    tagset = {t.lower() for t in tags}
    filtered: List[Dict[str, Any]] = []
    for item in items:
        item_tags = {t.lower() for t in item.get("tags", [])}
        if item_tags & tagset:
            filtered.append(item)
    return filtered


def summarise(section: str, item: Dict[str, Any]) -> Dict[str, Any]:
    """
    根据不同的知识库部分类型，提取条目的关键信息并生成摘要

    Args:
        section (str): 知识库部分名称，支持 "modules", "helpers", "templates", "cases", "errors"
        item (Dict[str, Any]): 原始的知识库条目数据

    Returns:
        Dict[str, Any]: 包含该部分类型关键字段的摘要字典

    Note:
        不同类型的条目有不同的关键字段：
        - modules: id, import_path, summary, exports, tags
        - helpers: id, signature, description, example, tags
        - templates: id, file, description, notes, tags
        - cases: id, path, summary, helpers, tags
        - errors: id, pattern, cause, fix, tags
    """
    if section == "modules":
        return {
            "id": item.get("id"),
            "import": item.get("import_path"),
            "summary": item.get("summary"),
            "exports": item.get("exports", []),
            "tags": item.get("tags", []),
        }
    if section == "helpers":
        return {
            "id": item.get("id"),
            "signature": item.get("signature"),
            "description": item.get("description"),
            "example": item.get("example"),
            "tags": item.get("tags", []),
        }
    if section == "templates":
        return {
            "id": item.get("id"),
            "file": item.get("file"),
            "description": item.get("description"),
            "notes": item.get("notes"),
            "modules_required": item.get("modules_required", []),
            "helpers_required": item.get("helpers_required", []),
            "tags": item.get("tags", []),
        }
    if section == "cases":
        return {
            "id": item.get("id"),
            "path": item.get("path"),
            "summary": item.get("summary"),
            "helpers": item.get("helpers", []),
            "tags": item.get("tags", []),
        }
    if section == "errors":
        return {
            "id": item.get("id"),
            "pattern": item.get("pattern"),
            "cause": item.get("cause"),
            "fix": item.get("fix"),
            "tags": item.get("tags", []),
        }
    return item


def retrieve(sections: Iterable[str], tags: List[str], limit: int) -> Dict[str, List[Dict[str, Any]]]:
    """
    从指定的知识库部分中检索匹配标签的条目

    Args:
        sections (Iterable[str]): 要检索的知识库部分列表
        tags (List[str]): 用于过滤的标签列表，为空时返回所有条目
        limit (int): 每个部分返回的最大条目数，0表示无限制

    Returns:
        Dict[str, List[Dict[str, Any]]]: 以部分名称为键，匹配条目摘要列表为值的字典

    Note:
        如果某个部分的知识库文件不存在，会向标准错误输出打印错误信息并跳过该部分
    """
    results: Dict[str, List[Dict[str, Any]]] = {}
    for section in sections:
        try:
            items = load_section(section)
        except FileNotFoundError as exc:
            print(str(exc), file=sys.stderr)
            continue
        filtered = tag_filter(items, tags)

        if section == "modules":
            id_lookup = {item.get("id"): item for item in items}
            combined: List[Dict[str, Any]] = []
            seen_ids = set()
            for core_id in CORE_MODULE_IDS:
                module = id_lookup.get(core_id)
                if module and core_id not in seen_ids:
                    combined.append(module)
                    seen_ids.add(core_id)
            for item in filtered:
                item_id = item.get("id")
                if item_id and item_id not in seen_ids:
                    combined.append(item)
                    seen_ids.add(item_id)
            filtered = combined

        if limit > 0:
            filtered = filtered[:limit]

        results[section] = [summarise(section, item) for item in filtered]
    return results


def format_text(results: Dict[str, List[Dict[str, Any]]]) -> str:
    """
    将检索结果格式化为可读的文本格式

    Args:
        results (Dict[str, List[Dict[str, Any]]]): retrieve函数返回的检索结果字典

    Returns:
        str: 格式化后的文本字符串，按部分分组显示每个条目的关键信息

    Note:
        - 输出格式：[section_name] 开头，每个条目以 "- id: xxx" 开始
        - 列表类型的值会转换为逗号分隔的字符串
        - 空值（None、空字符串、空列表）会被跳过不显示
    """
    lines: List[str] = []
    for section, items in results.items():
        lines.append(f"[{section}]")
        if not items:
            lines.append("  (no matches)")
            continue
        for item in items:
            lines.append(f"- id: {item.get('id')}")
            for key, value in item.items():
                if key == "id" or value in (None, "", []):
                    continue
                if isinstance(value, list):
                    value_str = ", ".join(map(str, value))
                else:
                    value_str = str(value)
                lines.append(f"    {key}: {value_str}")
    return "\n".join(lines)


def parse_args(argv: List[str]) -> argparse.Namespace:
    """
    解析命令行参数

    Args:
        argv (List[str]): 命令行参数列表（不包含程序名）

    Returns:
        argparse.Namespace: 包含解析后参数的命名空间对象

    支持的参数：
        --tags: 过滤标签列表，支持多个标签，不区分大小写
        --sections: 指定要检索的知识库部分，默认包含所有部分
        --limit: 每个部分返回的最大条目数，默认5，0表示无限制
        --format: 输出格式，支持 text（默认）和 json 两种格式
    """
    parser = argparse.ArgumentParser(
        description="Retrieve CodeQL knowledge base entries filtered by tags.",
    )
    parser.add_argument(
        "--tags",
        nargs="*",
        default=[],
        help="Filter items by tags (case-insensitive). Match if any tag overlaps.",
    )
    parser.add_argument(
        "--sections",
        nargs="*",
        default=list(DEFAULT_SECTIONS),
        choices=list(DEFAULT_SECTIONS),
        help="Knowledge base sections to include.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=5,
        help="Maximum items per section (0 means no limit).",
    )
    parser.add_argument(
        "--format",
        choices=["text", "json"],
        default="text",
        help="Output format.",
    )
    return parser.parse_args(argv)


def main(argv: List[str]) -> int:
    """
    程序主入口函数

    Args:
        argv (List[str]): 命令行参数列表（不包含程序名）

    Returns:
        int: 程序退出码，0表示成功执行

    执行流程：
        1. 解析命令行参数
        2. 根据参数从知识库中检索条目
        3. 根据指定的格式输出结果（文本或JSON）
    """
    args = parse_args(argv)
    results = retrieve(args.sections, args.tags, args.limit)
    if args.format == "json":
        json.dump(results, sys.stdout, indent=2, ensure_ascii=False)
        sys.stdout.write("\n")
    else:
        print(format_text(results))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
