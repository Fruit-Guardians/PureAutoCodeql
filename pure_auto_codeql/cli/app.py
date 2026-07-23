import argparse
import asyncio
from pathlib import Path
from typing import Optional, Sequence

# Backward-compat re-export hub: Analyze.py imports its entire public surface
# through pure_auto_codeql.cli.app, so these names stay re-exported here even
# though the module split moved their only internal users into sibling modules.
from pure_auto_codeql.agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from pure_auto_codeql.application import (
    AnalysisValidationError,
    ProjectImportRequest,
    ProjectImportResult,
    import_project_for_workflow,
    validate_analysis_case,
)

# Re-exported from sibling modules so the public CLI surface (and tests that
# patch pure_auto_codeql.cli.app.*) stays stable after the module split.
from pure_auto_codeql.cli.arguments import (
    _normalize_cli_args,
    _resolve_model_args,
    list_providers,
    parse_arguments,
)
from pure_auto_codeql.cli.runners import (
    run_case_analysis,
    run_md_direct_codeql,
    run_md_source_analysis,
)
from pure_auto_codeql.configuration import (
    LLMRole,
    get_llm_config,
    list_available_providers,
    list_siliconflow_models,
)
from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator
from pure_auto_codeql.services.language_detector import LanguageDetector
from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer
from pure_auto_codeql.tools.codeql_compose import CodeQLComposeTool
from pure_auto_codeql.utils.case import discover_cve_assets, resolve_case
from pure_auto_codeql.utils.doctor import run_doctor
from pure_auto_codeql.utils.logger import (
    get_logger,
    print_user_error,
    print_user_info,
    print_user_success,
    print_user_warning,
    setup_logging,
)

# 初始化日志系统
setup_logging(level="INFO")
logger = get_logger(__name__)

__all__ = [
    # entry points and dispatch
    "cli",
    "main",
    "dispatch_command",
    "parse_arguments",
    "_normalize_cli_args",
    "_resolve_model_args",
    # run-* flows
    "run_case_analysis",
    "run_md_direct_codeql",
    "run_md_source_analysis",
    "run_project_import",
    "list_available_cases",
    "validate_case",
    "list_providers",
    # re-export hub for Analyze.py (canonical objects live elsewhere)
    "AnalysisConfig",
    "AnalysisOrchestrator",
    "CodeQLComposeTool",
    "LanguageDetector",
    "LLMRole",
    "MultiAgentAnalyzer",
    "ProjectImportResult",
    "UnifiedSourceAnalysisAgent",
    "discover_cve_assets",
    "get_llm_config",
    "list_available_providers",
    "list_siliconflow_models",
    "resolve_case",
    "run_doctor",
]


def list_available_cases() -> None:
    """列出所有可用的案例"""
    projects_dir = Path("projects")
    if not projects_dir.exists():
        print_user_error("projects目录不存在")
        logger.error("projects目录不存在")
        return

    # 排除特殊文件夹：模板目录和镜像目录
    excluded_dirs = {"case-template", "python_kb"}
    case_dirs = [
        d for d in projects_dir.iterdir()
        if d.is_dir() and d.name not in excluded_dirs
    ]

    if not case_dirs:
        print_user_error("没有找到任何案例")
        logger.warning("没有找到任何案例")
        return

    print("📁 可用的案例:")
    for case_dir in sorted(case_dirs):
        try:
            case_paths = resolve_case(case_dir.name)
            cve_assets = discover_cve_assets(case_paths)
            print(f"  📂 {case_dir.name} -> {cve_assets.cve_id}")
        except Exception as e:
            print_user_warning(f"  📂 {case_dir.name} -> (解析失败: {e})")
            logger.warning(f"解析案例 {case_dir.name} 失败: {e}", exc_info=True)


async def validate_case(case_id: str) -> bool:
    """验证案例是否有效"""
    try:
        case_paths = validate_analysis_case(case_id)
        cve_assets = discover_cve_assets(case_paths)

        print_user_success(f"✅ 案例 {case_id} 验证通过")
        print_user_info(f"   📁 根目录: {case_paths.root}")
        print_user_info(f"   🎯 CVE ID: {cve_assets.cve_id}")
        print_user_info(f"   📄 JSON文件: {cve_assets.json_path}")
        if cve_assets.diff_path:
            file_type = "Diff" if cve_assets.diff_path.suffix == ".diff" else "Patch"
            print_user_info(f"   🔄 {file_type}文件: {cve_assets.diff_path}")
        else:
            print_user_warning("   ⚠️  没有Diff/Patch文件")

        # 检测语言
        detector = LanguageDetector()
        language = detector.detect_language(case_paths)
        print_user_info(f"   💻 检测语言: {language}")
        logger.info(f"案例 {case_id} 验证通过，语言: {language}")

        return True
    except AnalysisValidationError as e:
        print_user_error(f"❌ 案例 {case_id} 验证失败: {e}")
        logger.error(f"案例 {case_id} 验证失败: {e}", exc_info=True)
        return False
    except Exception as e:
        print_user_error(f"❌ 案例 {case_id} 验证失败: {e}")
        logger.error(f"案例 {case_id} 验证失败: {e}", exc_info=True)
        return False


def run_project_import(
    source_path: str,
    case_id: Optional[str] = None,
    overwrite: bool = False,
    language: Optional[str] = None,
    skip_codeql: bool = False,
    build_command: Optional[str] = None,
    build_script: Optional[str] = None,
    build_workdir: Optional[str] = None,
) -> None:
    """导入外部CVE项目目录"""
    print_user_info(f"🚚 开始导入目录: {source_path}")
    if case_id:
        print_user_info(f"🎯 指定案例ID: {case_id}")
    if overwrite:
        print_user_warning("⚠️  已启用覆盖模式，若同名案例存在将被替换")
    if skip_codeql:
        print_user_warning("⏭️  将跳过 CodeQL 数据库创建")
    if build_command:
        print_user_info(f"⚙️  构建命令: {build_command}")
    if build_script:
        print_user_info(f"📜 构建脚本: {build_script}")
    if build_workdir:
        print_user_info(f"📂 构建目录: {build_workdir}")

    try:
        result: ProjectImportResult = import_project_for_workflow(
            ProjectImportRequest(
                source_path=source_path,
                case_id=case_id,
                overwrite=overwrite,
                language=language,
                skip_codeql=skip_codeql,
                build_command=build_command,
                build_script=build_script,
                build_workdir=build_workdir,
            )
        )
    except FileNotFoundError as exc:
        print_user_error(f"❌ 输入路径不存在: {exc}")
        logger.error(f"导入失败: {exc}")
        return
    except ValueError as exc:
        print_user_error(f"❌ 参数错误: {exc}")
        logger.error(f"导入失败: {exc}")
        return
    except Exception as exc:  # pylint: disable=broad-except
        print_user_error(f"❌ 导入失败: {exc}")
        logger.exception("导入失败")
        return

    print_user_success("✅ 导入完成")
    print_user_info(f"   📁 案例ID: {result.case_id}")
    print_user_info(f"   📂 目标路径: {result.target_path}")
    if result.language:
        print_user_info(f"   💻 语言: {result.language}")
    if result.metadata_files:
        print_user_info(f"   🗂️  元数据: {', '.join(result.metadata_files)}")
    if not skip_codeql:
        if result.codeql_created:
            print_user_success("   🧱 CodeQL 数据库创建成功")
        else:
            print_user_warning("   ⚠️ CodeQL 数据库未创建")
            if result.codeql_error:
                print_user_error(f"      原因: {result.codeql_error}")
    if result.build_command:
        print_user_info(f"   ⚙️ 实际构建命令: {result.build_command}")
    if result.build_workdir:
        print_user_info(f"   📂 构建工作目录: {result.build_workdir}")
    print_user_info("📣 现在可以使用 --case 运行分析或调用 API 继续操作")


def _detect_case_directory_input(case_value: str) -> Optional[Path]:
    """
    判断 --case 参数是否提供了目录路径（而非案例ID）。
    仅在字符串包含路径分隔符或为绝对路径且目录存在时返回 Path。
    """
    if not case_value:
        return None

    contains_path_sep = ("\\" in case_value) or ("/" in case_value)
    candidate = Path(case_value).expanduser()
    if not candidate.exists() or not candidate.is_dir():
        return None
    if candidate.is_absolute() or contains_path_sep:
        return candidate.resolve()
    return None


def _auto_import_case_directory(case_dir: Path) -> ProjectImportResult:
    """
    将外部目录自动导入到 projects/ 并返回结果。
    默认启用覆盖与 CodeQL 自动建库。
    """
    print_user_info(f"📦 检测到外部CVE目录: {case_dir}")
    print_user_info("🔄 正在自动导入并创建CodeQL数据库...")
    result = import_project_for_workflow(
        ProjectImportRequest(
            source_path=str(case_dir),
            overwrite=True,
            skip_codeql=False,
        )
    )
    print_user_success(f"✅ 自动导入完成，案例ID: {result.case_id}")
    if result.metadata_files:
        print_user_info(f"   🗂️ 元数据: {', '.join(result.metadata_files)}")
    if result.codeql_created:
        print_user_success("   🧱 CodeQL 数据库已创建")
    else:
        print_user_warning("   ⚠️ 未能自动创建 CodeQL 数据库，可稍后手动重试")
        if result.codeql_error:
            print_user_error(f"      原因: {result.codeql_error}")
    return result





def _handle_project_import(args: argparse.Namespace) -> None:
    run_project_import(
        source_path=args.import_project,
        case_id=args.import_case_id,
        overwrite=args.import_overwrite,
        language=args.import_language,
        skip_codeql=args.import_skip_codeql,
        build_command=args.import_build_command,
        build_script=args.import_build_script,
        build_workdir=args.import_build_workdir,
    )


def _handle_doctor() -> None:
    raise SystemExit(run_doctor(Path.cwd()))


def _handle_serve(args: argparse.Namespace) -> None:
    import uvicorn

    from pure_auto_codeql.api.config import get_config

    api_config = get_config()
    host = args.serve_host or api_config.host
    port = args.serve_port or api_config.port
    reload = args.serve_reload or api_config.reload
    print_user_info(f"🚀 启动 API 服务: http://{host}:{port}")
    uvicorn.run("pure_auto_codeql.api.server:app", host=host, port=port, reload=reload)


async def _handle_case_analysis(args: argparse.Namespace) -> None:
    effective_case_id = args.case
    auto_case_dir = _detect_case_directory_input(args.case)
    if auto_case_dir:
        try:
            auto_import_result = _auto_import_case_directory(auto_case_dir)
            effective_case_id = auto_import_result.case_id
        except FileNotFoundError as exc:
            print_user_error(f"❌ 自动导入失败: 输入目录不存在 - {exc}")
            logger.error("自动导入失败: %s", exc)
            return
        except Exception as exc:  # pylint: disable=broad-except
            print_user_error(f"❌ 自动导入失败: {exc}")
            logger.exception("自动导入失败")
            return

    print("🚀 PureAutoCodeQL 启动")
    print(f"🎯 分析案例: {effective_case_id}")
    print(f"💭 AI思考过程: {'开启' if args.stream else '关闭'}")
    print(f"🔄 刷新情报: {'是' if args.refresh_intel else '否'}")
    print(f"📄 输出文件: {args.output or 'output.md'}")
    if args.provider:
        print(f"🤖 模型提供商: {args.provider} (命令行指定)")
    print("-" * 50)

    think_model, chat_model = _resolve_model_args(args)

    await run_case_analysis(
        case_id=effective_case_id,
        refresh_intel=args.refresh_intel,
        stream=args.stream,
        output_file=args.output,
        provider=args.provider,
        think_model=think_model,
        chat_model=chat_model,
        api_key=args.api_key,
        base_url=args.base_url,
        enable_error_tidy=args.enable_error_tidy,
        language=args.language,
        enable_source_sink_fallback=args.enable_source_sink_fallback,
        enable_sink_source_verification=args.enable_sink_source_verification,
        verification_retry_max=args.verification_retry_max,
        verification_timeout=args.verification_timeout,
        requirement=args.requirement,
        max_codeql_rounds=args.max_codeql_rounds,
        task_timeout=args.task_timeout,
        enable_cve_analysis=args.enable_cve_analysis,
        enable_sink_analysis=args.enable_sink_analysis,
        enable_source_analysis=args.enable_source_analysis,
        enable_path_analysis=args.enable_path_analysis,
        enable_codeql_generation=args.enable_codeql_generation,
        enable_path_selection=args.enable_path_selection,
        enable_breakpoint_recovery=args.enable_breakpoint_recovery,
    )


async def _handle_md_source_analysis(args: argparse.Namespace) -> None:
    print(f"📁 源代码路径: {args.src_path}")
    print("🔍 运行模式: Source点分析报告生成")
    if args.language:
        print(f"💻 编程语言: {args.language}")
    if args.provider:
        print(f"🤖 模型提供商: {args.provider} (命令行指定)")
    if args.output:
        print(f"📄 输出文件: {args.output}")
    print("-" * 50)

    think_model, chat_model = _resolve_model_args(args)

    await run_md_source_analysis(
        md_file_path=args.md_file,
        src_path=args.src_path,
        stream=args.stream,
        provider=args.provider,
        think_model=think_model,
        chat_model=chat_model,
        api_key=args.api_key,
        base_url=args.base_url,
        language=args.language,
        output_file=args.output,
    )


async def _handle_md_direct_codeql(args: argparse.Namespace) -> None:
    if args.source_sink_only:
        print("🔍 运行模式: Source-Sink 回退查询（测试模式）")
    else:
        print("🔍 运行模式: CodeQL查询生成")
    print(f"💻 编程语言: {args.language}")
    if args.database_path:
        print(f"📁 数据库路径: {args.database_path}")
    if args.provider:
        print(f"🤖 模型提供商: {args.provider} (命令行指定)")
    print("-" * 50)

    think_model, chat_model = _resolve_model_args(args)
    exec_mode = "fallback_only" if args.source_sink_only else "analyze"

    await run_md_direct_codeql(
        md_file_path=args.md_file,
        stream=args.stream,
        provider=args.provider,
        think_model=think_model,
        chat_model=chat_model,
        api_key=args.api_key,
        base_url=args.base_url,
        database_path=args.database_path,
        language=args.language or "java",
        enable_error_tidy=args.enable_error_tidy,
        enable_source_sink_fallback=(
            args.enable_source_sink_fallback or args.source_sink_only
        ),
        exec_mode=exec_mode,
        prev_ql_file=args.prev_ql_file,
    )


async def _handle_md_workflow(args: argparse.Namespace) -> None:
    print("🚀 PureAutoCodeQL 启动")
    print(f"📄 MD文件: {args.md_file}")
    print(f"💭 AI思考过程: {'开启' if args.stream else '关闭'}")

    if args.src_path:
        await _handle_md_source_analysis(args)
    else:
        await _handle_md_direct_codeql(args)


async def dispatch_command(args: argparse.Namespace) -> None:
    """Dispatch parsed CLI arguments to a testable command handler."""
    if args.list_providers:
        list_providers()
    elif args.list_models:
        list_siliconflow_models()
    elif args.list:
        list_available_cases()
    elif args.validate:
        await validate_case(args.validate)
    elif args.import_project:
        _handle_project_import(args)
    elif args.doctor:
        _handle_doctor()
    elif args.serve:
        _handle_serve(args)
    elif args.case:
        await _handle_case_analysis(args)
    elif args.md_file:
        await _handle_md_workflow(args)


async def main(argv: Optional[Sequence[str]] = None) -> None:
    """CLI coroutine entry point."""
    args = parse_arguments(argv)

    try:
        await dispatch_command(args)
    except KeyboardInterrupt:
        print_user_warning("\n⚠️  分析被用户中断")
        logger.warning("分析被用户中断")
    except Exception as e:
        print_user_error(f"\n❌ 执行出错: {e}")
        logger.exception(f"执行出错: {e}")


def cli(argv: Optional[Sequence[str]] = None) -> None:
    asyncio.run(main(argv))


if __name__ == "__main__":
    cli()
