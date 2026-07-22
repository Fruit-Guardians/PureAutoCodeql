import json
import logging
from pathlib import Path
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from dataclasses import dataclass

    @dataclass
    class AgentResult:
        content: str
        success: bool
        error: str = None

    from .intel import IntelBundle

logger = logging.getLogger(__name__)


def read_json_text(path: Path) -> str:
    """读取JSON文件，验证其语法，并返回原始文本。"""
    if not path.exists():
        raise FileNotFoundError(f"Missing file: {path}")
    text = path.read_text(encoding="utf-8")
    try:
        json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Invalid JSON: {e}")
    return text


def _format_result_content(result: Optional["AgentResult"]) -> str:
    """统一格式化结果内容。"""
    if not result:
        return "❌ 无结果"
    if result.success:
        return result.content
    error_msg = result.error if result.error else "未知错误"
    return f"❌ 分析失败: {error_msg}"


def write_analysis_output(
    cve_result: "AgentResult",
    sink_result: "AgentResult",
    source_result: "AgentResult",
    output_path: Path = Path("output.md"),
    *,
    intel_bundle: Optional["IntelBundle"] = None,
    path_analysis_result: Optional["AgentResult"] = None,  # 新增
    codeql_result: Optional["AgentResult"] = None,
    codeql_execution_result: Optional["AgentResult"] = None,
    language: Optional[str] = None,
    encoding: str = "utf-8",
) -> None:
    """将合并的Source和Sink分析结果以及CodeQL查询和执行结果写入Markdown文件。"""
    try:
        # 确保输出目录存在
        output_path.parent.mkdir(parents=True, exist_ok=True)

        # 确定语言显示名称
        language_display = language.title() if language else "Code"

        sections = [
            "# Multi-Agent Analysis Output\n",
        ]
        if intel_bundle:
            sections.extend(
                [
                    "\n## 情报采集\n",
                    intel_bundle.prompt_block(),
                ]
            )
        sections.extend(
            [
                "\n## CVE Analysis\n",
                _format_result_content(cve_result),
                f"\n\n## {language_display} Sink Analysis\n",
                _format_result_content(sink_result),
                f"\n\n## {language_display} Source Analysis\n",
                _format_result_content(source_result),
            ]
        )

        # 添加Path Analysis结果
        if path_analysis_result:
            sections.extend(
                [
                    f"\n\n## {language_display} Path Analysis\n",
                    _format_result_content(path_analysis_result),
                ]
            )

        # 添加CodeQL查询结果
        if codeql_result:
            sections.extend(
                [
                    "\n\n## Generated CodeQL Query\n",
                    _format_result_content(codeql_result),
                ]
            )

        # 添加CodeQL执行结果
        if codeql_execution_result:
            sections.extend(
                [
                    "\n\n## CodeQL Execution Results\n",
                    _format_result_content(codeql_execution_result),
                ]
            )

        sections.append("\n")

        output = "".join(sections)

        # 写入文件
        output_path.write_text(output, encoding=encoding)

        # 验证文件写入成功
        if not output_path.exists():
            raise OSError(f"文件写入失败: {output_path} 不存在")

        file_size = output_path.stat().st_size
        if file_size == 0:
            raise ValueError(f"文件写入失败: {output_path} 大小为0")

        logger.info(f"✅ 分析结果已写入: {output_path} (大小: {file_size} 字节)")

    except PermissionError as e:
        error_msg = f"文件权限错误，无法写入 {output_path}: {e}"
        logger.error(error_msg)
        raise PermissionError(error_msg) from e
    except OSError as e:
        error_msg = f"文件系统错误，无法写入 {output_path}: {e}"
        logger.error(error_msg)
        raise OSError(error_msg) from e
    except UnicodeEncodeError as e:
        error_msg = f"编码错误，无法使用 {encoding} 编码写入文件: {e}"
        logger.error(error_msg)
        raise
    except Exception as e:
        error_msg = f"写入分析输出失败: {e}"
        logger.exception(error_msg)
        raise RuntimeError(error_msg) from e
