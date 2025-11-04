"""用于管理每个案例分析输入的工作区助手。"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, Optional

from .cve_fetcher import fetch_cve_from_nvd, save_cve_data

CVE_FILE_PATTERN = re.compile(r"(CVE-\d{4}-\d+)", re.IGNORECASE)
logger = logging.getLogger(__name__)


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


def discover_cve_assets(case_paths: CasePaths) -> CveAssets:
    """
    在案例输入目录中定位CVE JSON/diff对，支持文件缺失时的回退机制。

    自动选择第一个可用的CVE进行，如果JSON文件缺失则从NVD API获取。
    """

    json_files = sorted(case_paths.inputs.glob("CVE-*.json"))
    json_map: Dict[str, Path] = {}
    cve_id: str
    json_path: Path

    # 处理本地JSON文件
    if json_files:
        for path in json_files:
            cve_id_extracted = extract_cve_id(path.name)
            if not cve_id_extracted:
                continue
            json_map[cve_id_extracted.upper()] = path

        if json_map:
            # 使用本地找到的JSON文件
            cve_id = sorted(json_map.keys())[0]
            json_path = json_map[cve_id]
            logger.info(f"📁 [本地文件] 找到CVE JSON文件: {json_path}")
        else:
            # 本地有JSON文件但格式都无效，尝试从diff文件推断CVE ID
            diff_files = sorted(case_paths.inputs.glob("CVE-*.diff"))
            if diff_files:
                cve_id = extract_cve_id(diff_files[0].name)
                if cve_id:
                    logger.info(f"📝 [推断ID] 从diff文件推断CVE ID: {cve_id}")
                else:
                    raise ValueError(
                        f"Inputs directory {case_paths.inputs} contains files but no valid CVE IDs could be extracted"
                    )
            else:
                raise ValueError(
                    f"Inputs directory {case_paths.inputs} contains JSON files but no valid CVE IDs could be extracted"
                )
    else:
        # 没有本地JSON文件，尝试从diff文件推断CVE ID
        diff_files = sorted(case_paths.inputs.glob("CVE-*.diff"))
        if diff_files:
            cve_id = extract_cve_id(diff_files[0].name)
            if not cve_id:
                raise ValueError(
                    f"Inputs directory {case_paths.inputs} contains diff files but no valid CVE IDs could be extracted"
                )
            logger.info(f"📝 [推断ID] 未找到JSON文件，从diff文件推断CVE ID: {cve_id}")
        else:
            raise FileNotFoundError(
                f"No CVE JSON or diff files found in {case_paths.inputs}"
            )

    # 如果没有有效的本地JSON文件，从NVD API获取
    if not json_map:
        try:
            logger.info(f"🌐 [网络获取] 正在从NVD API获取CVE数据: {cve_id}")
            cve_data = fetch_cve_from_nvd(cve_id)
            json_path = save_cve_data(cve_id, cve_data, case_paths.inputs)
            logger.info(f"✅ [获取成功] CVE数据已保存到: {json_path}")
        except Exception as e:
            logger.error(f"❌ [获取失败] 无法获取CVE数据 {cve_id}: {e}")
            raise RuntimeError(f"Failed to fetch CVE data for {cve_id}: {e}")

    # 处理diff文件（可选，缺失时不抛出异常）
    diff_candidates = sorted(case_paths.inputs.glob("CVE-*.diff"))
    diff_map = {extract_cve_id(path.name): path for path in diff_candidates}
    diff_path = diff_map.get(cve_id.upper())

    if diff_path:
        logger.info(f"📄 [本地文件] 找到diff文件: {diff_path}")
    else:
        logger.info(f"⚠️  [文件缺失] 未找到 {cve_id} 的diff文件，将继续进行分析（无diff模式）")

    return CveAssets(cve_id=cve_id, json_path=json_path, diff_path=diff_path)


def extract_cve_id(filename: str) -> Optional[str]:
    """从文件名中提取CVE标识符。"""

    match = CVE_FILE_PATTERN.search(filename)
    if match:
        return match.group(1).upper()
    return None





def default_language_db(case_paths: CasePaths, language: str) -> Optional[Path]:
    """如果存在，返回给定语言的预期CodeQL数据库路径。"""

    language = language.lower()
    candidate = case_paths.db / language
    if candidate.exists():
        return candidate
    if case_paths.db.exists() and any(case_paths.db.iterdir()):
        return case_paths.db
    return None
