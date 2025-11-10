"""CVE上下文提取器

从output.md中提取CVE分析、Sink分析、Source分析等信息
"""

from __future__ import annotations

import re
from typing import Any, Dict, Optional


class CVEContextExtractor:
    """CVE上下文提取器 - 从output.md提取关键信息"""
    
    def extract(self, output_md_content: str) -> Dict[str, Any]:
        """
        提取CVE上下文信息
        
        Args:
            output_md_content: output.md的内容
        
        Returns:
            包含CVE上下文的字典
        """
        context = {
            "raw_content": output_md_content,
            "cve_id": self._extract_cve_id(output_md_content),
            "vulnerability_type": None,
            "technical_details": None,
            "expected_sink": None,
            "expected_source": None,
            "sink_analysis": None,
            "source_analysis": None,
            "intel_summary": None
        }
        
        # 提取CVE Analysis部分
        cve_analysis = self._extract_section(output_md_content, "## CVE Analysis")
        if cve_analysis:
            context["vulnerability_type"] = self._extract_subsection(
                cve_analysis, "### 漏洞类型"
            )
            context["technical_details"] = self._extract_subsection(
                cve_analysis, "### 技术细节"
            )
            context["expected_sink"] = self._extract_subsection(
                cve_analysis, "### Sink点"
            )
            context["expected_source"] = self._extract_subsection(
                cve_analysis, "### Source点"
            ) or self._extract_subsection(
                cve_analysis, "### 可能的Source点"
            )
        
        # 提取Sink Analysis部分（多语言支持）
        sink_section = (
            self._extract_section(output_md_content, "## Python Sink Analysis") or
            self._extract_section(output_md_content, "## Java Sink Analysis") or
            self._extract_section(output_md_content, "## C Sink Analysis") or
            self._extract_section(output_md_content, "## Sink Analysis")
        )
        if sink_section:
            context["sink_analysis"] = sink_section
        
        # 提取Source Analysis部分（多语言支持）
        source_section = (
            self._extract_section(output_md_content, "## Python Source Analysis") or
            self._extract_section(output_md_content, "## Java Source Analysis") or
            self._extract_section(output_md_content, "## C Source Analysis") or
            self._extract_section(output_md_content, "## Source Analysis")
        )
        if source_section:
            context["source_analysis"] = source_section
        
        # 提取情报摘要
        intel_section = self._extract_section(output_md_content, "## 情报采集")
        if intel_section:
            context["intel_summary"] = intel_section
        
        return context
    
    def _extract_cve_id(self, content: str) -> Optional[str]:
        """提取CVE ID"""
        match = re.search(r'CVE-\d{4}-\d+', content)
        return match.group(0) if match else None
    
    def _extract_section(self, content: str, section_header: str) -> Optional[str]:
        """提取指定章节的内容"""
        # 匹配章节标题到下一个同级或更高级标题
        pattern = rf'{re.escape(section_header)}(.*?)(?=\n##[^#]|\Z)'
        match = re.search(pattern, content, re.DOTALL)
        
        if match:
            section_content = match.group(1).strip()
            return section_content if section_content else None
        
        return None
    
    def _extract_subsection(self, section_content: str, subsection_header: str) -> Optional[str]:
        """从章节中提取子章节内容"""
        # 匹配子章节标题到下一个同级或更高级标题
        pattern = rf'{re.escape(subsection_header)}(.*?)(?=\n###[^#]|\n##[^#]|\Z)'
        match = re.search(pattern, section_content, re.DOTALL)
        
        if match:
            subsection_content = match.group(1).strip()
            # 移除开头的换行和空白
            subsection_content = re.sub(r'^\s+', '', subsection_content)
            return subsection_content if subsection_content else None
        
        return None

