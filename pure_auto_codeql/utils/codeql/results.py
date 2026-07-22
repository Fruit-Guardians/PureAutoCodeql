"""Persist queries and parse/inspect CodeQL result artifacts."""

import json
from pathlib import Path
from typing import Any, Dict, List, Optional


def save_query_to_persistent_dir(query_content: str, task_id: str, language: str, metadata: Optional[Dict[str, Any]] = None) -> Optional[Path]:
    """
    将生成的 CodeQL 查询保存到持久化目录（temp/ql_queries），每轮保存一个版本。

    Args:
        query_content: CodeQL 查询内容
        task_id: 任务 ID
        language: 查询语言
        metadata: 可选的元数据字典（应包含 round 信息）

    Returns:
        保存的查询文件路径，如果失败则返回 None
    """
    try:
        output_base_dir = Path('./temp/ql_queries')
        output_base_dir.mkdir(parents=True, exist_ok=True)

        task_dir = output_base_dir / task_id
        task_dir.mkdir(parents=True, exist_ok=True)

        # 获取轮次信息，用于生成不同版本的文件名
        round_num = metadata.get('round', 1) if metadata else 1

        # 保存查询文件（每轮一个文件）
        query_file = task_dir / f'query_round{round_num}.ql'
        query_file.write_text(query_content, encoding='utf-8')

        # 保存或更新元数据
        if metadata:
            metadata_file = task_dir / f'metadata_round{round_num}.json'
            metadata_file.write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding='utf-8')

        print(f"✅ [持久化] 查询已保存到: {query_file}")
        return query_file

    except Exception as e:
        print(f"⚠️  [持久化] 保存查询失败: {e}")
        return None


def is_empty_result(sarif_path: Optional[str]) -> bool:
    """
    检测SARIF结果文件是否为空（无数据流路径）。

    Args:
        sarif_path: SARIF输出文件的路径

    Returns:
        如果结果为空返回True，否则返回False
    """
    if not sarif_path:
        return True

    sarif_file = Path(sarif_path)
    if not sarif_file.exists():
        return True

    try:
        with open(sarif_file, 'r', encoding='utf-8') as f:
            sarif_data = json.load(f)

        # 检查SARIF格式的结果
        if isinstance(sarif_data, dict) and 'runs' in sarif_data:
            for run in sarif_data.get('runs', []):
                results = run.get('results', [])
                if results and len(results) > 0:
                    return False
            return True

        return True
    except Exception as e:
        print(f"⚠️  [is_empty_result] 解析SARIF文件失败: {e}")
        return True


def count_dataflow_paths(sarif_path: Optional[str] = None, json_path: Optional[str] = None) -> int:
    """
    统计数据流路径数量。

    Args:
        sarif_path: SARIF输出文件的路径
        json_path: JSON格式的路径输出文件

    Returns:
        数据流路径的数量
    """
    count = 0

    # 优先检查SARIF文件
    if sarif_path:
        sarif_file = Path(sarif_path)
        if sarif_file.exists():
            try:
                with open(sarif_file, 'r', encoding='utf-8') as f:
                    sarif_data = json.load(f)

                if isinstance(sarif_data, dict) and 'runs' in sarif_data:
                    for run in sarif_data.get('runs', []):
                        results = run.get('results', [])
                        count += len(results)
                    return count
            except Exception as e:
                print(f"⚠️  [count_dataflow_paths] 解析SARIF文件失败: {e}")

    # 检查JSON路径文件
    if json_path:
        json_file = Path(json_path)
        if json_file.exists():
            try:
                with open(json_file, 'r', encoding='utf-8') as f:
                    json_data = json.load(f)

                if isinstance(json_data, list):
                    count += len(json_data)
                elif isinstance(json_data, dict):
                    # 可能是包含paths键的字典
                    paths = json_data.get('paths', [])
                    count += len(paths)
                return count
            except Exception as e:
                print(f"⚠️  [count_dataflow_paths] 解析JSON文件失败: {e}")

    return count


def parse_codeql_results(result_output: str) -> List[Dict[str, Any]]:
    """
    将CodeQL查询输出解析为结构化数据。

    Args:
        result_output: CodeQL查询执行的原始输出。

    Returns:
        解析后的结果记录列表。如果没有结果或解析失败，则返回空列表。
    """
    if not result_output or not result_output.strip():
        return []

    try:
        data = json.loads(result_output)
        if isinstance(data, dict) and 'runs' in data:
            results = []
            for run in data.get('runs', []):
                for result in run.get('results', []):
                    results.append(result)
            return results
        elif isinstance(data, list):
            return data
        else:
            return []
    except json.JSONDecodeError:
        lines = result_output.strip().split('\n')
        if not lines:
            return []

        results = []
        for line in lines:
            line = line.strip()
            if line and not line.startswith('#'):
                parts = line.split('|')
                if len(parts) > 1:
                    results.append({
                        'data': [p.strip() for p in parts]
                    })
        return results
