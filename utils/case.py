"""用于管理每个案例分析输入的工作区助手。"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, Optional

CVE_FILE_PATTERN = re.compile(r"(CVE-\d{4}-\d+)", re.IGNORECASE)


@dataclass(frozen=True)
class CasePaths:
    """单个案例分析已解析的位置。"""

    root: Path
    source_code: Path
    queries: Path
    db: Path
    inputs: Path
    intel: Path


@dataclass(frozen=True)
class CveAssets:
    """案例中单个CVE的本地工件。"""

    cve_id: str
    json_path: Path
    diff_path: Optional[Path]


def resolve_case(case_id: str, *, base_dir: Path = Path("projects")) -> CasePaths:
    """解析并验证案例工作区。"""

    root = (base_dir / case_id).resolve()
    mapping = {
        "source_code": root / "source_code",
        "queries": root / "queries",
        "db": root / "db",
        "inputs": root / "inputs",
        "intel": root / "intel",
    }

    missing = [name for name, path in mapping.items() if not path.exists()]
    if missing:
        raise FileNotFoundError(
            f"Case '{case_id}' is missing required directories: {', '.join(missing)}"
        )

    return CasePaths(
        root=root,
        source_code=mapping["source_code"],
        queries=mapping["queries"],
        db=mapping["db"],
        inputs=mapping["inputs"],
        intel=mapping["intel"],
    )


def discover_cve_assets(
    case_paths: CasePaths, *, preferred_cve: Optional[str] = None
) -> CveAssets:
    """
    在案例输入目录中定位CVE JSON/diff对。

    如果提供了preferred_cve，它必须存在；否则函数选择
    唯一存在的CVE或在有歧义时抛出异常。
    """

    json_files = sorted(case_paths.inputs.glob("CVE-*.json"))
    if not json_files:
        raise FileNotFoundError(
            f"No CVE JSON files found in {case_paths.inputs}"
        )

    json_map: Dict[str, Path] = {}
    for path in json_files:
        cve_id = extract_cve_id(path.name)
        if not cve_id:
            continue
        json_map[cve_id.upper()] = path

    if not json_map:
        raise ValueError(
            f"Inputs directory {case_paths.inputs} does not contain valid CVE JSON filenames"
        )

    cve_id = _select_cve_id(json_map.keys(), preferred_cve)
    json_path = json_map[cve_id]

    diff_candidates = sorted(case_paths.inputs.glob("CVE-*.diff"))
    diff_map = {extract_cve_id(path.name): path for path in diff_candidates}
    diff_path = diff_map.get(cve_id)

    return CveAssets(cve_id=cve_id, json_path=json_path, diff_path=diff_path)


def extract_cve_id(filename: str) -> Optional[str]:
    """从文件名中提取CVE标识符。"""

    match = CVE_FILE_PATTERN.search(filename)
    if match:
        return match.group(1).upper()
    return None


def _select_cve_id(cve_ids: Iterable[str], preferred: Optional[str]) -> str:
    """解析要使用哪个CVE标识符。"""

    cve_list = sorted({cve.upper() for cve in cve_ids})
    if preferred:
        preferred_upper = preferred.upper()
        if preferred_upper not in cve_list:
            raise ValueError(
                f"CVE {preferred} not found in inputs. Available: {', '.join(cve_list)}"
            )
        return preferred_upper

    if len(cve_list) == 1:
        return cve_list[0]

    raise ValueError(
        "Multiple CVE JSON files found. Specify one with --cve. "
        f"Available: {', '.join(cve_list)}"
    )


def default_language_db(case_paths: CasePaths, language: str) -> Optional[Path]:
    """如果存在，返回给定语言的预期CodeQL数据库路径。"""

    language = language.lower()
    candidate = case_paths.db / language
    if candidate.exists():
        return candidate
    if case_paths.db.exists() and any(case_paths.db.iterdir()):
        return case_paths.db
    return None
