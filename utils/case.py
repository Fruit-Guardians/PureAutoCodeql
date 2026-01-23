"""用于管理每个案例分析输入的工作区助手。

PureAuto - 纯AI漏洞分析工具
"""

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
    inputs: Path
    intel: Path


@dataclass(frozen=True)
class ExtraFile:
    """额外输入文件的元数据。"""
    
    path: Path
    
    def read_text(self) -> str:
        """读取文件内容为文本。"""
        return self.path.read_text(encoding='utf-8')


@dataclass(frozen=True)
class CveAssets:
    """案例中单个CVE的本地工件。"""

    cve_id: str
    json_path: Path
    diff_path: Optional[Path]
    extra_files: tuple[ExtraFile, ...] = ()  # 额外输入文件
    
    def has_extra_files(self) -> bool:
        """检查是否有额外文件。"""
        return len(self.extra_files) > 0
    
    def get_all_extra_content(self) -> str:
        """获取所有额外文件的内容（用于分析上下文）。"""
        if not self.extra_files:
            return ""
        
        sections = [f"=== 额外输入文件 ({len(self.extra_files)} 个) ===\n"]
        
        for extra_file in self.extra_files:
            sections.append(f"\n--- 文件: {extra_file.path.name} ---")
            try:
                content = extra_file.read_text()
                sections.append(content)
            except Exception as e:
                sections.append(f"[无法读取文件: {e}]")
        
        return "\n".join(sections)


def resolve_case(case_id: str, *, base_dir: Path = Path("projects")) -> CasePaths:
    """解析并验证案例工作区。
    
    自动创建缺失的intel和inputs目录（纯AI分析模式）。
    source_code目录如果不存在会抛出错误。
    """

    root = (base_dir / case_id).resolve()
    
    # 检查根目录是否存在
    if not root.exists():
        raise FileNotFoundError(f"Case directory not found: {root}")
    
    # source_code 是必需的
    source_code = root / "source_code"
    if not source_code.exists():
        raise FileNotFoundError(
            f"Case '{case_id}' is missing required 'source_code' directory"
        )
    
    # 自动创建 inputs 和 intel 目录（如果缺失）
    inputs = root / "inputs"
    intel = root / "intel"
    
    if not inputs.exists():
        logger.info(f"📁 [自动创建] 创建 inputs 目录: {inputs}")
        inputs.mkdir(parents=True, exist_ok=True)
    
    if not intel.exists():
        logger.info(f"📁 [自动创建] 创建 intel 目录: {intel}")
        intel.mkdir(parents=True, exist_ok=True)

    return CasePaths(
        root=root,
        source_code=source_code,
        inputs=inputs,
        intel=intel,
    )


def _discover_extra_files(inputs_dir: Path, cve_id: str) -> tuple[ExtraFile, ...]:
    """
    发现 inputs 目录中的额外文件。
    
    排除标准的 CVE JSON、diff 和 patch 文件。
    """
    extra_files = []
    
    for file_path in inputs_dir.iterdir():
        if not file_path.is_file():
            continue
        
        # 排除标准 CVE 文件
        if file_path.name.startswith('CVE-') and file_path.suffix in ('.json', '.diff', '.patch'):
            continue
        
        # 排除隐藏文件和临时文件
        if file_path.name.startswith('.') or file_path.name.endswith('~'):
            continue
        
        extra_file = ExtraFile(path=file_path)
        extra_files.append(extra_file)
    
    # 按文件名排序
    extra_files.sort(key=lambda f: f.path.name)
    
    if extra_files:
        logger.info(f"📂 [额外文件] 发现 {len(extra_files)} 个额外输入文件:")
        for extra_file in extra_files:
            logger.info(f"   - {extra_file.path.name}")
    
    return tuple(extra_files)


def discover_cve_assets(case_paths: CasePaths) -> CveAssets:
    """
    在案例输入目录中定位CVE JSON/diff对，支持文件缺失时的回退机制。

    自动选择第一个可用的CVE进行，如果JSON文件缺失则从NVD API获取。
    同时发现并分类额外的输入文件。
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
            # 本地有JSON文件但格式都无效，尝试从diff/patch文件推断CVE ID
            diff_files = sorted(case_paths.inputs.glob("CVE-*.diff"))
            patch_files = sorted(case_paths.inputs.glob("CVE-*.patch"))
            all_diff_patch_files = sorted(diff_files + patch_files)
            if all_diff_patch_files:
                cve_id = extract_cve_id(all_diff_patch_files[0].name)
                if cve_id:
                    logger.info(f"📝 [推断ID] 从diff/patch文件推断CVE ID: {cve_id}")
                else:
                    raise ValueError(
                        f"Inputs directory {case_paths.inputs} contains files but no valid CVE IDs could be extracted"
                    )
            else:
                raise ValueError(
                    f"Inputs directory {case_paths.inputs} contains JSON files but no valid CVE IDs could be extracted"
                )
    else:
        # 没有本地JSON文件，尝试从diff/patch文件推断CVE ID
        diff_files = sorted(case_paths.inputs.glob("CVE-*.diff"))
        patch_files = sorted(case_paths.inputs.glob("CVE-*.patch"))
        all_diff_patch_files = sorted(diff_files + patch_files)
        if all_diff_patch_files:
            cve_id = extract_cve_id(all_diff_patch_files[0].name)
            if not cve_id:
                raise ValueError(
                    f"Inputs directory {case_paths.inputs} contains diff/patch files but no valid CVE IDs could be extracted"
                )
            logger.info(f"📝 [推断ID] 未找到JSON文件，从diff/patch文件推断CVE ID: {cve_id}")
        else:
            raise FileNotFoundError(
                f"No CVE JSON, diff or patch files found in {case_paths.inputs}"
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

    # 处理diff/patch文件（可选，缺失时不抛出异常）
    # 优先使用diff文件，如果没有则使用patch文件
    diff_candidates = sorted(case_paths.inputs.glob("CVE-*.diff"))
    patch_candidates = sorted(case_paths.inputs.glob("CVE-*.patch"))
    all_candidates = diff_candidates + patch_candidates
    
    diff_map = {extract_cve_id(path.name): path for path in all_candidates}
    diff_path = diff_map.get(cve_id.upper())

    if diff_path:
        file_type = "diff" if diff_path.suffix == ".diff" else "patch"
        logger.info(f"📄 [本地文件] 找到{file_type}文件: {diff_path}")
    else:
        logger.info(f"⚠️  [文件缺失] 未找到 {cve_id} 的diff/patch文件，将继续进行分析（无diff模式）")

    # 发现额外输入文件
    extra_files = _discover_extra_files(case_paths.inputs, cve_id)

    return CveAssets(
        cve_id=cve_id, 
        json_path=json_path, 
        diff_path=diff_path,
        extra_files=extra_files
    )


def extract_cve_id(filename: str) -> Optional[str]:
    """从文件名中提取CVE标识符。"""

    match = CVE_FILE_PATTERN.search(filename)
    if match:
        return match.group(1).upper()
    return None
