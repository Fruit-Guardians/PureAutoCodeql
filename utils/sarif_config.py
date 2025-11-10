"""
SARIF 配置模块

SARIF (Static Analysis Results Interchange Format) 相关的配置
这是项目分析结果的配置，与LLM配置无关
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class Sarif2JsonConfig:
    """SARIF 转 JSON 的全局配置"""
    max_results: int = 3
    threadflow_index: int = 0
    rule_filter: Optional[str] = None


# 全局配置实例
SARIF2JSON_CONFIG = Sarif2JsonConfig()


def get_sarif2json_config() -> Sarif2JsonConfig:
    """获取 SARIF 转 JSON 的配置实例"""
    return SARIF2JSON_CONFIG

