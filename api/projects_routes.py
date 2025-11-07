"""项目案例管理API路由"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query

from api.config import get_config
from api.models import FileInfo, ProjectDetail, ProjectFilesResponse, ProjectInfo
from utils.case import (
    CasePaths,
    discover_cve_assets,
    resolve_case,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/projects", tags=["Projects"])


def _detect_languages(case_paths: CasePaths) -> List[str]:
    """检测项目中的编程语言"""
    languages = set()
    
    # 检查数据库目录
    if case_paths.db.exists():
        for item in case_paths.db.iterdir():
            if item.is_dir():
                lang = item.name.lower()
                if lang in ["java", "python", "cpp"]:
                    languages.add(lang)
    
    # 检查源代码目录
    if case_paths.source_code.exists():
        extensions_map = {
            ".java": "java",
            ".py": "python",
            ".cpp": "cpp",
        }
        
        for file_path in case_paths.source_code.rglob("*"):
            if file_path.is_file():
                ext = file_path.suffix.lower()
                if ext in extensions_map:
                    languages.add(extensions_map[ext])
    
    return sorted(list(languages))


def _count_files(directory: Path) -> int:
    """统计目录中的文件数量"""
    if not directory.exists():
        return 0
    return sum(1 for _ in directory.rglob("*") if _.is_file())


def _build_directory_structure(directory: Path, max_depth: int = 3) -> dict:
    """构建目录结构树"""
    if not directory.exists():
        return {}
    
    def _build_tree(path: Path, current_depth: int = 0) -> dict:
        if current_depth >= max_depth:
            return {"type": "directory", "truncated": True}
        
        if path.is_file():
            return {
                "type": "file",
                "size": path.stat().st_size,
                "extension": path.suffix,
            }
        
        children = {}
        try:
            for item in sorted(path.iterdir()):
                children[item.name] = _build_tree(item, current_depth + 1)
        except PermissionError:
            return {"type": "directory", "error": "Permission denied"}
        
        return {"type": "directory", "children": children}
    
    return _build_tree(directory)


@router.get("", response_model=List[ProjectInfo])
async def list_projects(
    include_invalid: bool = Query(False, description="是否包含无效项目")
) -> List[ProjectInfo]:
    """
    获取所有项目列表
    
    返回projects目录下的所有项目基本信息
    """
    config = get_config()
    projects_dir = config.projects_dir
    
    if not projects_dir.exists():
        logger.warning(f"Projects directory not found: {projects_dir}")
        return []
    
    projects = []
    
    for item in projects_dir.iterdir():
        if not item.is_dir():
            continue
        
        # 跳过隐藏目录和特殊目录
        if item.name.startswith(".") or item.name in ["__pycache__", "node_modules"]:
            continue
        
        case_id = item.name
        
        try:
            # 尝试解析项目结构
            case_paths = resolve_case(case_id, base_dir=projects_dir)
            
            # 检测语言
            languages = _detect_languages(case_paths)
            
            # 读取描述（如果存在README.md）
            description = None
            readme_path = case_paths.root / "README.md"
            if readme_path.exists():
                try:
                    with open(readme_path, "r", encoding="utf-8") as f:
                        # 读取第一行作为描述
                        first_line = f.readline().strip()
                        if first_line.startswith("#"):
                            description = first_line.lstrip("#").strip()
                        else:
                            description = first_line
                except Exception:
                    pass
            
            projects.append(
                ProjectInfo(
                    case_id=case_id,
                    path=str(case_paths.root),
                    description=description,
                    exists=True,
                    has_database=case_paths.db.exists() and any(case_paths.db.iterdir()),
                    has_source=case_paths.source_code.exists() and any(case_paths.source_code.iterdir()),
                    languages=languages,
                )
            )
        except FileNotFoundError as e:
            # 项目结构不完整
            if include_invalid:
                projects.append(
                    ProjectInfo(
                        case_id=case_id,
                        path=str(item),
                        description=f"Invalid project: {str(e)}",
                        exists=False,
                        has_database=False,
                        has_source=False,
                        languages=[],
                    )
                )
            logger.debug(f"Skipping invalid project {case_id}: {e}")
        except Exception as e:
            logger.error(f"Error processing project {case_id}: {e}")
            if include_invalid:
                projects.append(
                    ProjectInfo(
                        case_id=case_id,
                        path=str(item),
                        description=f"Error: {str(e)}",
                        exists=False,
                        has_database=False,
                        has_source=False,
                        languages=[],
                    )
                )
    
    return projects


@router.get("/{case_id}", response_model=ProjectDetail)
async def get_project_detail(case_id: str) -> ProjectDetail:
    """
    获取项目详细信息
    
    包含CVE信息、文件结构、语言检测结果等
    """
    config = get_config()
    
    try:
        case_paths = resolve_case(case_id, base_dir=config.projects_dir)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=f"Project not found: {str(e)}")
    
    # 检测语言
    languages = _detect_languages(case_paths)
    
    # 获取CVE信息
    cve_id = None
    cve_description = None
    try:
        cve_assets = discover_cve_assets(case_paths)
        cve_id = cve_assets.cve_id
        
        # 读取CVE描述
        if cve_assets.json_path and cve_assets.json_path.exists():
            try:
                with open(cve_assets.json_path, "r", encoding="utf-8") as f:
                    cve_data = json.load(f)
                    # 尝试从不同的JSON结构中提取描述
                    if "cve" in cve_data:
                        descriptions = cve_data["cve"].get("descriptions", [])
                        if descriptions:
                            cve_description = descriptions[0].get("value", "")
                    elif "description" in cve_data:
                        cve_description = cve_data["description"]
            except Exception as e:
                logger.warning(f"Failed to read CVE description: {e}")
    except Exception as e:
        logger.debug(f"No CVE assets found for {case_id}: {e}")
    
    # 统计文件数量
    file_count = _count_files(case_paths.source_code)
    
    # 构建目录结构
    directory_structure = {
        "source_code": _build_directory_structure(case_paths.source_code, max_depth=2),
        "queries": _build_directory_structure(case_paths.queries, max_depth=2),
        "db": _build_directory_structure(case_paths.db, max_depth=1),
    }
    
    # 读取描述
    description = None
    readme_path = case_paths.root / "README.md"
    if readme_path.exists():
        try:
            with open(readme_path, "r", encoding="utf-8") as f:
                first_line = f.readline().strip()
                if first_line.startswith("#"):
                    description = first_line.lstrip("#").strip()
                else:
                    description = first_line
        except Exception:
            pass
    
    return ProjectDetail(
        case_id=case_id,
        path=str(case_paths.root),
        description=description,
        exists=True,
        has_database=case_paths.db.exists() and any(case_paths.db.iterdir()),
        has_source=case_paths.source_code.exists() and any(case_paths.source_code.iterdir()),
        languages=languages,
        cve_id=cve_id,
        cve_description=cve_description,
        file_count=file_count,
        directory_structure=directory_structure,
    )


@router.get("/{case_id}/files", response_model=ProjectFilesResponse)
async def get_project_files(
    case_id: str,
    directory: Optional[str] = Query(None, description="子目录路径（相对于source_code）"),
    max_files: int = Query(1000, ge=1, le=10000, description="最大文件数量"),
) -> ProjectFilesResponse:
    """
    获取项目文件列表
    
    返回项目的文件树结构，包含文件路径、大小、类型等信息
    """
    config = get_config()
    
    try:
        case_paths = resolve_case(case_id, base_dir=config.projects_dir)
    except FileNotFoundError as e:
        raise HTTPException(status_code=404, detail=f"Project not found: {str(e)}")
    
    # 确定要扫描的目录
    if directory:
        scan_dir = case_paths.source_code / directory
        if not scan_dir.exists() or not scan_dir.is_relative_to(case_paths.source_code):
            raise HTTPException(status_code=400, detail="Invalid directory path")
    else:
        scan_dir = case_paths.source_code
    
    if not scan_dir.exists():
        raise HTTPException(status_code=404, detail="Source code directory not found")
    
    # 扫描文件
    files = []
    count = 0
    
    for item in sorted(scan_dir.rglob("*")):
        if count >= max_files:
            break
        
        try:
            relative_path = item.relative_to(case_paths.source_code)
            
            file_info = FileInfo(
                path=str(relative_path),
                name=item.name,
                size=item.stat().st_size if item.is_file() else 0,
                is_directory=item.is_dir(),
                extension=item.suffix if item.is_file() else None,
            )
            files.append(file_info)
            count += 1
        except Exception as e:
            logger.warning(f"Error processing file {item}: {e}")
            continue
    
    return ProjectFilesResponse(
        case_id=case_id,
        files=files,
        total_count=len(files),
    )
