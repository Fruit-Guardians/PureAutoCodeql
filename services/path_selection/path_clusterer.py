"""路径聚类器

对相似路径进行聚类，减少冗余，选择代表性路径
"""

from __future__ import annotations

import logging
from collections import defaultdict
from typing import Any, Dict, List

logger = logging.getLogger(__name__)


class PathClusterer:
    """路径聚类器 - 识别和去除重复路径模式"""
    
    def cluster_and_deduplicate(
        self,
        enriched_paths: List[Dict[str, Any]],
        cve_context: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """
        对路径进行聚类和去重
        
        Args:
            enriched_paths: 增强后的路径列表
            cve_context: CVE上下文
        
        Returns:
            去重后的代表性路径列表
        """
        if len(enriched_paths) <= 3:
            return enriched_paths
        
        logger.info(f"开始路径聚类: {len(enriched_paths)} 条路径")
        
        # 按Sink点聚类
        sink_clusters = self._cluster_by_sink(enriched_paths)
        logger.info(f"  按Sink点聚类: {len(sink_clusters)} 个簇")
        
        # 从每个簇中选择代表
        representatives = []
        for sink_key, paths in sink_clusters.items():
            # 每个Sink簇内再按Source聚类
            source_clusters = self._cluster_by_source(paths)
            
            # 从每个Source子簇选择最完整的路径
            for source_key, sub_paths in source_clusters.items():
                best_path = self._select_best_from_cluster(sub_paths)
                representatives.append(best_path)
        
        logger.info(f"  选出 {len(representatives)} 条代表性路径")
        
        return representatives
    
    def _cluster_by_sink(
        self,
        paths: List[Dict[str, Any]]
    ) -> Dict[str, List[Dict[str, Any]]]:
        """按Sink点聚类"""
        
        clusters = defaultdict(list)
        
        for path in paths:
            sink_loc = path.get("sink_location", {})
            # 使用 (文件, 行号) 作为key
            key = (
                sink_loc.get("file", ""),
                sink_loc.get("startLine", 0)
            )
            clusters[key].append(path)
        
        return dict(clusters)
    
    def _cluster_by_source(
        self,
        paths: List[Dict[str, Any]]
    ) -> Dict[str, List[Dict[str, Any]]]:
        """按Source点聚类"""
        
        clusters = defaultdict(list)
        
        for path in paths:
            source_loc = path.get("source_location", {})
            # 使用 (文件, 行号) 作为key
            key = (
                source_loc.get("file", ""),
                source_loc.get("startLine", 0)
            )
            clusters[key].append(path)
        
        return dict(clusters)
    
    def _select_best_from_cluster(
        self,
        cluster_paths: List[Dict[str, Any]]
    ) -> Dict[str, Any]:
        """从簇中选择最好的路径"""
        
        if len(cluster_paths) == 1:
            return cluster_paths[0]
        
        # 评分标准：
        # 1. 路径长度适中（不要太短也不要太长）
        # 2. 有代码上下文
        # 3. Source和Sink描述清晰
        
        def score_path(path: Dict[str, Any]) -> float:
            score = 0.0
            
            # 路径长度评分（4-8步最佳）
            path_len = path.get("path_length", 0)
            if 4 <= path_len <= 8:
                score += 10.0
            elif 2 <= path_len <= 10:
                score += 5.0
            else:
                score += 1.0
            
            # 有代码上下文加分
            if path.get("source_code"):
                score += 5.0
            if path.get("sink_code"):
                score += 5.0
            
            # Source/Sink分析清晰度
            source_analysis = path.get("source_analysis", {})
            if source_analysis.get("description") and source_analysis.get("description") != "未知":
                score += 3.0
            
            sink_analysis = path.get("sink_analysis", {})
            if sink_analysis.get("description") and sink_analysis.get("description") != "未知":
                score += 3.0
            
            return score
        
        # 选择得分最高的路径
        best_path = max(cluster_paths, key=score_path)
        return best_path


