"""路径选择服务使用示例

演示如何使用PathSelectionService从CodeQL查询结果中选择最匹配CVE的路径
"""

from __future__ import annotations

import asyncio
import json
import logging
import textwrap
from pathlib import Path
from typing import Any, Dict

from services.path_selection import PathSelectionService
from config import get_chat_config


SEPARATOR = "=" * 60


class ConsoleStreamPrinter:
    """将 event_callback 流式事件转换为 CLI 友好的输出。"""

    def __init__(self) -> None:
        self._active = False
        self._step_name: str | None = None

    async def __call__(self, event: Dict[str, Any]) -> None:
        event_type = event.get("type")
        message = event.get("message", "")
        data = event.get("data") or {}

        if event_type == "agent_start":
            print()
            print("┌─ PathSelection LLM 精排开始")
            print(f"│  {message}")
            print("└────────────────────────────────────────")
            return

        if event_type == "info":
            if self._active:
                self._finish_stream()
            rank = data.get("candidate_rank")
            det_score = data.get("deterministic_score")
            flow_summary = data.get("flow_summary")
            print()
            header = f"📚 候选 {rank} 点读上下文 (det_score={det_score})" if det_score is not None else f"📚 候选 {rank} 点读上下文"
            print(header)
            if flow_summary:
                print(format_bullet(f"流程: {flow_summary}", indent=2))
            dangerous = data.get("dangerous_apis") or []
            if dangerous:
                print(format_bullet(f"危险API: {', '.join(dangerous)}", indent=2))
            blocks = data.get("blocks") or []
            for block in blocks:
                role = block.get("role", "unknown")
                location = block.get("location", "N/A")
                print(f"\n  ▸ {role.upper()} @ {location}")
                snippet = block.get("snippet") or "_未获取代码片段_"
                for line in snippet.splitlines():
                    print(f"      {line}")
            return

        if event_type == "agent_thinking":
            chunk = message or data.get("stream_chunk", "")
            if not chunk:
                return
            if not self._active:
                self._step_name = event.get("step_name")
                print("\n" + "─" * 80)
                print("🤖 LLM 流式输出", end="")
                if self._step_name:
                    print(f" · {self._step_name}", end="")
                print("\n" + "─" * 80, end="")
                self._active = True
            print(chunk, end="", flush=True)
            if data.get("is_final"):
                self._finish_stream()
            return

        if event_type == "agent_complete":
            if self._active:
                self._finish_stream()
            print()
            print("✅ PathSelection LLM 精排完成")
            if message:
                print(f"   {message}")
            return

    def _finish_stream(self) -> None:
        print("\n" + "─" * 80)
        print("✅ LLM 输出结束")
        print("─" * 80)
        self._active = False
        self._step_name = None


def print_section(title: str, *, leading_blank: bool = True) -> None:
    if leading_blank:
        print()
    print(SEPARATOR)
    print(title)
    print(SEPARATOR)


def format_bullet(text: str, indent: int = 2, width: int = 80) -> str:
    indent_str = " " * indent
    wrapped = textwrap.fill(text, width=width, subsequent_indent=indent_str, initial_indent=indent_str)
    return wrapped


async def demo_basic_usage():
    """基础用法示例"""

    print_section("路径选择服务 - 基础用法示例", leading_blank=False)

    # 1. 准备输入路径
    output_dir = Path("output/analysis_output_20251109_131403")
    output_md = output_dir / "output.md"
    result_json = output_dir / "result_20251109_131312.json"
    source_root = Path("projects/CVE-2025-54381/source_code/BentoML-1.4.10/BentoML-1.4.10")

    # 检查文件是否存在
    if not output_md.exists():
        print(f"❌ output.md不存在: {output_md}")
        return

    if not result_json.exists():
        print(f"❌ result.json不存在: {result_json}")
        return

    print(format_bullet(f"输入 Markdown: {output_md}"))
    print(format_bullet(f"输入 JSON: {result_json}"))
    print(format_bullet(f"源码根目录: {source_root}"))

    # 2. 初始化服务
    print("\n初始化路径选择服务...")
    llm_config = get_chat_config()
    service = PathSelectionService(llm_config, language="python")
    print("✓ 服务初始化完成")

    # 3. 选择最佳路径
    print("\n开始路径选择流程...")
    stream_printer = ConsoleStreamPrinter()
    result = await service.select_best_paths(
        output_md_path=str(output_md),
        result_json_path=str(result_json),
        source_root=str(source_root),
        top_k=3,
        enable_clustering=True,
        event_callback=stream_printer,
    )
    print("✓ 路径选择完成")

    # 4. 输出结果
    print_section("选择结果")
    print(format_bullet(f"CVE: {result.cve_id or 'N/A'}"))
    print(format_bullet(f"语言: {result.language}"))
    print(format_bullet(f"总路径数: {result.all_paths_count}"))
    print(format_bullet(f"选中路径数: {len(result.selected_paths)}"))

    print("\n选择理由:")
    print(format_bullet(result.selection_reasoning, indent=4))

    summary = result.verification_summary
    print("\n验证摘要:")
    print(format_bullet(f"所有路径有效: {summary.get('all_valid')}", indent=4))
    print(format_bullet(
        f"有效路径数: {summary.get('valid_count')}/{summary.get('total_verified')}",
        indent=4,
    ))
    issues = summary.get("issues") or []
    if issues:
        print(format_bullet("发现的问题:", indent=4))
        for issue in issues:
            print(format_bullet(f"- {issue}", indent=6))

    coverage = result.coverage_analysis or {}
    if coverage:
        print("\n覆盖率分析:")
        if coverage.get("sink_files"):
            print(format_bullet(f"Sink文件: {', '.join(coverage['sink_files'])}", indent=4))
        if coverage.get("source_types"):
            print(format_bullet(f"Source类型: {', '.join(coverage['source_types'])}", indent=4))
        if coverage.get("dangerous_apis"):
            print(format_bullet(f"危险API: {', '.join(coverage['dangerous_apis'])}", indent=4))

    # 5. 输出详细路径信息
    print_section("选中的路径详情")
    for idx, path in enumerate(result.selected_paths, 1):
        selection_info = path.get("selection_info", {})
        verification_info = path.get("verification", {})
        source_loc = path.get("source_location", {})
        sink_loc = path.get("sink_location", {})

        print(format_bullet(f"路径 {idx}", indent=0))
        print(format_bullet(f"置信度: {selection_info.get('confidence', selection_info.get('deterministic_score', 'N/A'))}", indent=4))
        print(format_bullet(f"路径长度: {path.get('path_length', 'N/A')} 步", indent=4))
        print(format_bullet(
            f"Source: {source_loc.get('file', 'N/A')}:{source_loc.get('startLine', 'N/A')}",
            indent=4,
        ))
        print(format_bullet(
            f"Sink: {sink_loc.get('file', 'N/A')}:{sink_loc.get('startLine', 'N/A')}",
            indent=4,
        ))
        print(format_bullet(f"选择原因: {selection_info.get('reason', 'N/A')}", indent=4))
        print(format_bullet(
            f"验证状态: {'✅ 有效' if verification_info.get('is_valid') else '❌ 无效'}",
            indent=4,
        ))
        if idx != len(result.selected_paths):
            print()

    # 6. 保存Markdown报告
    report_path = output_dir / "path_selection_report.md"
    with open(report_path, "w", encoding="utf-8") as handler:
        handler.write(result.to_markdown())
    print(f"\n✓ 报告已保存: {report_path}")

    # 7. 保存JSON结果
    json_path = output_dir / "path_selection_result.json"
    with open(json_path, "w", encoding="utf-8") as handler:
        json.dump(result.to_dict(), handler, ensure_ascii=False, indent=2)
    print(f"✓ JSON结果已保存: {json_path}")

    # 8. 保存 CodeQL dataFlowPath 导出
    export_payload = result.to_dataflow_json()
    export_name = (result.cve_id or "CVE-UNKNOWN").upper()
    if not export_name.startswith("CVE-"):
        export_name = f"CVE-{export_name}"
    export_path = output_dir / f"{export_name}.json"
    with open(export_path, "w", encoding="utf-8") as handler:
        json.dump(export_payload, handler, ensure_ascii=False, indent=2)
    print(f"✓ dataFlowPath 导出: {export_path}")


async def demo_different_languages():
    """演示不同语言的路径选择"""

    print_section("多语言支持示例")

    languages = ["python", "java", "c"]

    for lang in languages:
        print(f"\n{lang.upper()} 语言适配器:")

        llm_config = get_chat_config()
        service = PathSelectionService(llm_config, language=lang)

        # 获取危险API列表
        adapter = service.path_enricher.adapter
        dangerous_apis = adapter.get_dangerous_apis()

        print(format_bullet(f"危险API数量: {len(dangerous_apis)}", indent=4))
        print(format_bullet(f"示例API: {', '.join(dangerous_apis[:5])}", indent=4))


async def demo_without_clustering():
    """演示禁用聚类的情况"""

    print_section("禁用聚类示例")

    output_dir = Path("output/analysis_output_20251109_131403")
    output_md = output_dir / "output.md"
    result_json = output_dir / "result_20251109_131312.json"
    source_root = Path("projects/CVE-2025-54381/source_code/BentoML-1.4.10/BentoML-1.4.10")

    if not all([output_md.exists(), result_json.exists()]):
        print("示例文件不存在，跳过")
        return

    llm_config = get_chat_config()
    service = PathSelectionService(llm_config, language="python")

    print("选择路径（禁用聚类）...")
    stream_printer = ConsoleStreamPrinter()
    result = await service.select_best_paths(
        output_md_path=str(output_md),
        result_json_path=str(result_json),
        source_root=str(source_root),
        top_k=3,
        enable_clustering=False,  # 禁用聚类
        event_callback=stream_printer,
    )

    print(f"✓ 完成（处理了 {result.all_paths_count} 条路径）")


async def main():
    """主函数"""

    # 降低示例运行时的日志噪声
    logging.getLogger("services.path_selection").setLevel(logging.WARNING)
    logging.getLogger("httpx").setLevel(logging.WARNING)

    # 运行基础用法示例
    try:
        await demo_basic_usage()
    except Exception as exc:
        print(f"基础用法示例失败: {exc}")
        import traceback

        traceback.print_exc()

    # 运行多语言示例
    try:
        await demo_different_languages()
    except Exception as exc:
        print(f"多语言示例失败: {exc}")

    # 运行禁用聚类示例
    try:
        await demo_without_clustering()
    except Exception as exc:
        print(f"禁用聚类示例失败: {exc}")


if __name__ == "__main__":
    asyncio.run(main())
