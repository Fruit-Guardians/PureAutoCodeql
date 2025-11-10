"""路径增强器

为每条路径添加实际代码上下文和语义信息
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any, Dict, List, Optional

from .language_adapters import get_language_adapter

logger = logging.getLogger(__name__)


class PathEnricher:
    """路径增强器 - 为路径添加代码上下文"""
    
    def __init__(self, language: str):
        """
        初始化路径增强器
        
        Args:
            language: 编程语言 (java/c/python)
        """
        self.language = language.lower()
        self.adapter = get_language_adapter(self.language)
    
    async def enrich_paths(
        self,
        paths: List[Dict[str, Any]],
        source_root: str | Path,
        cve_context: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """
        增强所有路径
        
        Args:
            paths: 原始路径列表
            source_root: 源代码根目录
            cve_context: CVE上下文
        
        Returns:
            增强后的路径列表
        """
        source_root = Path(source_root)
        enriched = []
        
        for idx, path in enumerate(paths):
            try:
                enriched_path = await self._enrich_single_path(
                    idx, path, source_root, cve_context
                )
                enriched.append(enriched_path)
            except Exception as e:
                logger.warning(f"路径 {idx} 增强失败: {e}")
                # 返回原始路径（带最小增强）
                enriched.append(self._minimal_enrichment(idx, path))
        
        return enriched
    
    async def _enrich_single_path(
        self,
        index: int,
        path: Dict[str, Any],
        source_root: Path,
        cve_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        """增强单条路径"""
        
        steps = path.get("threadFlows", [{}])[0].get("steps", [])
        
        if not steps:
            return self._minimal_enrichment(index, path)
        
        source_step = steps[0]
        sink_step = steps[-1]
        
        # 读取Source和Sink的代码上下文
        source_code = await self._read_code_context(
            source_step.get("location", {}),
            source_root
        )
        
        sink_code = await self._read_code_context(
            sink_step.get("location", {}),
            source_root
        )
        
        # 使用语言适配器提取关键信息
        source_analysis = self.adapter.analyze_source_point(
            source_step.get("location", {}),
            source_code
        )
        
        sink_analysis = self.adapter.analyze_sink_point(
            sink_step.get("location", {}),
            sink_code
        )
        
        # 生成流程摘要
        flow_summary = self._generate_flow_summary(steps)
        
        return {
            "index": index,
            "original_path": path,
            "path_length": len(steps),
            "source_location": source_step.get("location", {}),
            "source_code": source_code,
            "source_analysis": source_analysis,
            "sink_location": sink_step.get("location", {}),
            "sink_code": sink_code,
            "sink_analysis": sink_analysis,
            "intermediate_steps": len(steps) - 2,
            "flow_summary": flow_summary,
            "language": self.language
        }
    
    async def _read_code_context(
        self,
        location: Dict[str, Any],
        source_root: Path,
        context_lines: int = 3
    ) -> str:
        """读取位置的代码上下文"""
        
        file_path = location.get("file", "")
        start_line = location.get("startLine", 0)
        
        if not file_path or not start_line:
            return ""
        
        # 构建完整路径
        full_path = source_root / file_path
        
        if not full_path.exists():
            logger.debug(f"文件不存在: {full_path}")
            return ""
        
        try:
            with open(full_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()
            
            # 提取上下文行
            start_idx = max(0, start_line - context_lines - 1)
            end_idx = min(len(lines), start_line + context_lines)
            
            context_lines_list = []
            for i in range(start_idx, end_idx):
                line_num = i + 1
                prefix = ">>> " if line_num == start_line else "    "
                context_lines_list.append(f"{prefix}{line_num:4d} | {lines[i].rstrip()}")
            
            return "\n".join(context_lines_list)
        
        except Exception as e:
            logger.debug(f"读取文件失败 {full_path}: {e}")
            return ""
    
    def _generate_flow_summary(self, steps: List[Dict[str, Any]]) -> str:
        """生成数据流摘要"""
        
        if not steps:
            return "空路径"
        
        source = steps[0].get("location", {})
        sink = steps[-1].get("location", {})
        
        source_desc = source.get("description", "未知")
        sink_desc = sink.get("description", "未知")
        
        intermediate_count = len(steps) - 2
        
        if intermediate_count == 0:
            return f"{source_desc} → {sink_desc}"
        else:
            return f"{source_desc} → [{intermediate_count}个中间步骤] → {sink_desc}"
    
    def _minimal_enrichment(self, index: int, path: Dict[str, Any]) -> Dict[str, Any]:
        """最小增强（当完整增强失败时）"""
        
        steps = path.get("threadFlows", [{}])[0].get("steps", [])
        
        return {
            "index": index,
            "original_path": path,
            "path_length": len(steps),
            "source_location": steps[0].get("location", {}) if steps else {},
            "source_code": "",
            "source_analysis": {"description": "未知", "type": "unknown"},
            "sink_location": steps[-1].get("location", {}) if steps else {},
            "sink_code": "",
            "sink_analysis": {"description": "未知", "type": "unknown"},
            "intermediate_steps": len(steps) - 2 if len(steps) > 2 else 0,
            "flow_summary": self._generate_flow_summary(steps),
            "language": self.language
        }

