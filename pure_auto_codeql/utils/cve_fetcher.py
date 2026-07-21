"""CVE数据获取工具模块，专门处理从NVD API获取CVE数据。"""

from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any, Dict

import requests

# 配置日志记录
logger = logging.getLogger(__name__)

# NVD API配置
NVD_API_BASE_URL = "https://services.nvd.nist.gov/rest/json/cves/2.0"
REQUEST_TIMEOUT = 30
MAX_RETRIES = 3
RETRY_DELAY = 2


def fetch_cve_from_nvd(cve_id: str) -> Dict[str, Any]:
    """
    从NVD API获取指定CVE的数据。

    Args:
        cve_id: CVE标识符，格式如"CVE-2021-21985"

    Returns:
        包含CVE数据的字典

    Raises:
        ValueError: 当CVE ID格式无效时
        requests.RequestException: 当API请求失败时
        RuntimeError: 当API返回空数据或格式错误时
    """
    if not cve_id or not cve_id.upper().startswith("CVE-"):
        raise ValueError(f"Invalid CVE ID format: {cve_id}")

    cve_id = cve_id.upper()
    url = f"{NVD_API_BASE_URL}?cveId={cve_id}"

    logger.info(f"Fetching CVE data from NVD API: {cve_id}")

    for attempt in range(MAX_RETRIES):
        try:
            response = requests.get(
                url,
                timeout=REQUEST_TIMEOUT,
                headers={
                    "User-Agent": "PureAutoCodeql-CVE-Fetcher/1.0",
                    "Accept": "application/json"
                }
            )

            if response.status_code == 200:
                data = response.json()

                # 检查是否找到了CVE数据
                if data.get("totalResults", 0) == 0 or not data.get("vulnerabilities"):
                    raise RuntimeError(f"No CVE data found for {cve_id}")

                logger.info(f"Successfully fetched CVE data for {cve_id}")
                return data

            elif response.status_code == 404:
                raise RuntimeError(f"CVE {cve_id} not found in NVD database")
            else:
                logger.warning(f"API request failed with status {response.status_code}, attempt {attempt + 1}/{MAX_RETRIES}")
                if attempt == MAX_RETRIES - 1:
                    response.raise_for_status()

        except requests.exceptions.Timeout:
            logger.warning(f"Request timeout for {cve_id}, attempt {attempt + 1}/{MAX_RETRIES}")
            if attempt == MAX_RETRIES - 1:
                raise requests.exceptions.Timeout(f"Timeout fetching CVE data for {cve_id} after {MAX_RETRIES} attempts")
        except requests.exceptions.ConnectionError:
            logger.warning(f"Connection error for {cve_id}, attempt {attempt + 1}/{MAX_RETRIES}")
            if attempt == MAX_RETRIES - 1:
                raise requests.exceptions.ConnectionError(f"Connection error fetching CVE data for {cve_id} after {MAX_RETRIES} attempts")
        except requests.exceptions.RequestException as e:
            logger.warning(f"Request exception for {cve_id}: {e}, attempt {attempt + 1}/{MAX_RETRIES}")
            if attempt == MAX_RETRIES - 1:
                raise

        if attempt < MAX_RETRIES - 1:
            time.sleep(RETRY_DELAY * (attempt + 1))  # 递增延迟

    raise RuntimeError(f"Failed to fetch CVE data for {cve_id} after {MAX_RETRIES} attempts")


def validate_cve_data(data: Dict[str, Any]) -> bool:
    """
    验证从NVD API获取的数据结构是否符合预期格式。

    Args:
        data: 从NVD API获取的JSON数据

    Returns:
        bool: 数据结构是否有效
    """
    if not isinstance(data, dict):
        logger.error("CVE data is not a dictionary")
        return False

    # 检查必需的顶级字段
    required_fields = ["vulnerabilities", "totalResults", "resultsPerPage"]
    for field in required_fields:
        if field not in data:
            logger.error(f"Missing required field in CVE data: {field}")
            return False

    vulnerabilities = data.get("vulnerabilities")
    if not isinstance(vulnerabilities, list) or len(vulnerabilities) == 0:
        logger.error("CVE data has no vulnerabilities or invalid format")
        return False

    # 检查第一个漏洞项的结构
    first_vuln = vulnerabilities[0]
    if not isinstance(first_vuln, dict) or "cve" not in first_vuln:
        logger.error("Invalid vulnerability structure in CVE data")
        return False

    cve_data = first_vuln["cve"]
    if not isinstance(cve_data, dict):
        logger.error("Invalid CVE structure in vulnerability data")
        return False

    # 检查CVE基本信息
    cve_required_fields = ["id", "state", "published", "modified"]
    for field in cve_required_fields:
        if field not in cve_data:
            logger.error(f"Missing required CVE field: {field}")
            return False

    logger.debug("CVE data structure validation passed")
    return True


def save_cve_data(cve_id: str, data: Dict[str, Any], save_path: Path) -> Path:
    """
    将CVE数据保存为JSON文件。

    Args:
        cve_id: CVE标识符
        data: 要保存的CVE数据
        save_path: 保存目录路径

    Returns:
        Path: 保存的文件路径

    Raises:
        ValueError: 当参数无效时
        OSError: 当文件操作失败时
    """
    if not cve_id:
        raise ValueError("CVE ID cannot be empty")

    if not data:
        raise ValueError("CVE data cannot be empty")

    if not isinstance(save_path, Path):
        save_path = Path(save_path)

    # 确保保存目录存在
    save_path.mkdir(parents=True, exist_ok=True)

    # 生成文件名
    filename = f"{cve_id.upper()}.json"
    file_path = save_path / filename

    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

        logger.info(f"CVE data saved to: {file_path}")
        return file_path

    except OSError as e:
        logger.error(f"Failed to save CVE data to {file_path}: {e}")
        raise OSError(f"Failed to save CVE data: {e}")


def get_cve_summary(cve_data: Dict[str, Any]) -> str:
    """
    从CVE数据中提取摘要信息。

    Args:
        cve_data: CVE数据字典

    Returns:
        str: CVE摘要信息，如果没有则返回空字符串
    """
    if not isinstance(cve_data, dict):
        return ""

    vulnerabilities = cve_data.get("vulnerabilities", [])
    if not vulnerabilities:
        return ""

    first_vuln = vulnerabilities[0]
    cve = first_vuln.get("cve", {})

    # 获取英文描述
    descriptions = cve.get("descriptions", [])
    for desc in descriptions:
        if desc.get("lang") == "en":
            return desc.get("value", "")

    return ""
