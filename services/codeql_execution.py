"""CodeQL 查询执行与结果整理服务。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from config import get_sarif2json_config
from utils.sarif_utils import write_paths_json


@dataclass
class CodeQLExecutionResult:
    success: bool
    output: str
    sarif_path: Optional[str] = None
    json_path: Optional[str] = None
    paths_count: Optional[int] = None
    result_file: Optional[str] = None
    preview: Optional[str] = None
    
    @property
    def has_results(self) -> bool:
        """检测查询结果是否为空。"""
        # 检查paths_count（analyze模式）
        if self.paths_count is not None:
            return self.paths_count > 0
        
        # 检查output内容（run模式）
        if self.output and self.output.strip():
            # 检查常见的空结果模式
            empty_indicators = [
                'No results.',
                'No results found',
                '0 results',
                'Empty result set',
                '查询结果为空',
                '未找到结果'
            ]
            
            output_lower = self.output.lower()
            for indicator in empty_indicators:
                if indicator.lower() in output_lower:
                    return False
            
            # 检查是否有实际的数据行（非表头、非空行）
            lines = [line.strip() for line in self.output.splitlines() if line.strip()]
            if len(lines) <= 2:  # 只有表头或很少的行
                return False
                
            # 检查是否有数据行（包含实际数据）
            data_lines = [line for line in lines if not line.startswith('|') or '---' not in line]
            return len(data_lines) > 1
        
        return False


class CodeQLExecutionService:
    """包装 CodeQL CLI 调用及 SARIF 后处理。"""

    def __init__(
        self,
        *,
        database_path: str,
        language: str,
        execute_fn: Callable[[str, str, Optional[str]], dict],
        decode_fn: Optional[Callable[[str, str, Optional[str]], dict]] = None,
    ) -> None:
        self._database_path = database_path
        self._language = language
        self._execute_fn = execute_fn
        self._decode_fn = decode_fn

    def execute(self, query: str, exec_mode: str = "analyze") -> CodeQLExecutionResult:
        mode = (exec_mode or "analyze").lower()

        if mode == "run" and self._decode_fn:
            return self._execute_run_mode(query)

        return self._execute_analyze_mode(query)

    def _execute_analyze_mode(self, query: str) -> CodeQLExecutionResult:
        try:
            raw_result = self._execute_fn(query, self._database_path, self._language)
        except Exception as exc:  # pylint: disable=broad-except
            return CodeQLExecutionResult(success=False, output=f"Execution failed: {exc}")

        if not raw_result.get("success"):
            return CodeQLExecutionResult(
                success=False,
                output=raw_result.get("output", "Unknown execution error"),
            )

        sarif_path = raw_result.get("sarif_path")
        json_path: Optional[str] = None
        paths_count: Optional[int] = None

        if sarif_path:
            try:
                config = get_sarif2json_config()
                json_file = Path(sarif_path).with_suffix(".json")
                paths_count = write_paths_json(
                    sarif_path,
                    str(json_file),
                    max_results=config.max_results,
                    threadflow_index=config.threadflow_index,
                    rule_filter=config.rule_filter,
                    relative_to=None,
                )
                json_path = str(json_file)
            except Exception:
                # SARIF 转换失败时继续返回原结果
                json_path = None
                paths_count = None

        result = CodeQLExecutionResult(
            success=True,
            output=raw_result.get("output", ""),
            sarif_path=sarif_path,
            json_path=json_path,
            paths_count=paths_count,
        )
        
        # 检测空结果并添加提示
        result = self._handle_empty_results(result)
        
        return result

    def _handle_empty_results(self, result: CodeQLExecutionResult) -> CodeQLExecutionResult:
        """处理空结果检测和用户交互提示。"""
        if not result.has_results:
            result.output = f"⚠️ 查询执行成功，但未找到匹配结果。\n\n原始输出:\n{result.output}\n\n💡 请检查查询条件或选择是否继续优化查询。"
        return result

    def _execute_run_mode(self, query: str) -> CodeQLExecutionResult:
        try:
            raw_result = self._decode_fn(query, self._database_path, self._language)  # type: ignore[misc]
        except Exception as exc:  # pylint: disable=broad-except
            return CodeQLExecutionResult(success=False, output=f"Execution failed: {exc}")

        if not raw_result.get("success"):
            return CodeQLExecutionResult(
                success=False,
                output=raw_result.get("output", "Unknown execution error"),
            )

        full_text = raw_result.get("output", "") or ""
        result_file = raw_result.get("result_file")
        lines = full_text.splitlines()
        preview = "\n".join(lines[:40]).strip()
        if len(lines) > 40:
            preview = f"{preview}\n..."

        result = CodeQLExecutionResult(
            success=True,
            output=full_text,
            result_file=result_file,
            preview=preview if preview else None,
        )
        
        # 检测空结果并添加提示
        result = self._handle_empty_results(result)
        
        return result


__all__ = ["CodeQLExecutionService", "CodeQLExecutionResult"]
