import argparse
import asyncio
import sys
from pathlib import Path
from typing import Optional, Sequence
from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator
from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.services.language_detector import LanguageDetector
from pure_auto_codeql.utils.case import resolve_case, discover_cve_assets
from pure_auto_codeql.utils.logger import setup_logging, get_logger, print_user_success, print_user_error, print_user_warning, print_user_info
from pure_auto_codeql.configuration import list_available_providers, get_llm_config_by_provider, LLMRole, list_siliconflow_models, get_llm_config
from pure_auto_codeql.tools.codeql_compose import CodeQLComposeTool
from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer
from pure_auto_codeql.application import (
    AnalysisValidationError,
    ProjectImportRequest,
    ProjectImportResult,
    import_project_for_workflow,
    validate_analysis_case,
)
from pure_auto_codeql.agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from pure_auto_codeql.utils.doctor import run_doctor

# 初始化日志系统
setup_logging(level="INFO")
logger = get_logger(__name__)



async def run_case_analysis(
    case_id: str,
    refresh_intel: bool = False,
    stream: bool = False,
    output_file: Optional[str] = None,
    provider: Optional[str] = None,
    think_model: Optional[str] = None,
    chat_model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    enable_error_tidy: bool = False,
    language: Optional[str] = None,
    enable_source_sink_fallback: bool = False,
    enable_sink_source_verification: bool = False,
    verification_retry_max: int = 3,
    verification_timeout: int = 30,
) -> None:
    """
    运行完整的案例分析

    Args:
        case_id: 案例ID，如 "CVE-2021-21985"
        refresh_intel: 是否强制刷新情报数据
        stream: 是否显示AI思考过程
        output_file: 自定义输出文件名
        provider: 模型提供商名称（deepseek/siliconflow/zhipu/kimi/gemini）
        think_model: 推理模型名称（覆盖默认模型）
        chat_model: 对话模型名称（覆盖默认模型）
        api_key: API Key（覆盖环境变量）
        base_url: Base URL（覆盖环境变量）
        enable_error_tidy: 是否启用错误整理功能
        language: 强制指定编程语言（跳过自动检测）
    """
    # 创建配置
    config = AnalysisConfig(
        show_thinking=stream,
        refresh_intel=refresh_intel,
        output_file=output_file,  # None 表示使用时间戳目录
        llm_provider=provider,
        think_model=think_model,
        chat_model=chat_model,
        api_key=api_key,
        base_url=base_url,
        enable_error_tidy=enable_error_tidy,
        enable_source_sink_fallback=enable_source_sink_fallback,
        enable_sink_source_verification=enable_sink_source_verification,
        verification_retry_max=verification_retry_max,
        verification_timeout=verification_timeout,
    )

    # 显示模型提供商信息
    try:
        # 使用配置中的参数获取模型配置
        chat_config = get_llm_config(
            LLMRole.CHAT,
            provider_name=provider,
            model_name=chat_model,
            api_key=api_key,
            base_url=base_url
        )
        think_config = get_llm_config(
            LLMRole.THINK,
            provider_name=provider,
            model_name=think_model,
            api_key=api_key,
            base_url=base_url
        )

        provider_display = provider or chat_config.provider or "环境变量配置"
        print_user_info(f"🤖 使用模型提供商: {provider_display}")
        print_user_info(f"   💭 推理模型: {think_config.model}")
        print_user_info(f"   💬 对话模型: {chat_config.model}")
        if base_url:
            print_user_info(f"   🔗 Base URL: {base_url}")
        logger.info(f"使用模型提供商: {provider_display}, 推理模型: {think_config.model}, 对话模型: {chat_config.model}, Base URL: {chat_config.base_url}")
    except Exception as e:
        print_user_error(f"❌ 无法配置模型: {e}")
        logger.error(f"模型配置失败: {e}", exc_info=True)
        return

    # 创建并运行编排器
    orchestrator = AnalysisOrchestrator(config)
    result = await orchestrator.analyze_case(case_id, language=language)

    # 显示结果摘要
    if result.success:
        print_user_success(f"\n🎉 分析成功完成！")
        print_user_info(f"📋 案例ID: {result.case_id}")
        print_user_info(f"💻 编程语言: {result.language}")
        if result.execution_time:
            print_user_info(f"⏱️  执行时间: {result.execution_time:.2f}秒")

        if result.is_complete():
            print_user_success("✅ 所有分析步骤都成功完成")
        else:
            print_user_warning("⚠️  部分分析步骤未完成")

        if config.output_file:
            print_user_info(f"📄 分析报告已保存到: {config.output_file}")
        else:
            logger.info(f"分析结果已保存到时间戳目录")
    else:
        print_user_error(f"\n❌ 分析失败: {result.error_message}")
        logger.error(f"案例分析失败: {result.error_message}")


async def run_md_direct_codeql(
    md_file_path: str,
    stream: bool = False,
    provider: Optional[str] = None,
    think_model: Optional[str] = None,
    chat_model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    database_path: Optional[str] = None,
    language: str = "java",
    enable_error_tidy: bool = False,
    enable_source_sink_fallback: bool = False,
    fallback_empty_retry_max: int = 5,
    exec_mode: str = "analyze",
    prev_ql_file: Optional[str] = None,
) -> None:
    """
    从MD文件直接生成CodeQL查询

    Args:
        md_file_path: MD文件路径
        stream: 是否显示AI思考过程
        provider: 模型提供商名称
        think_model: 推理模型名称
        chat_model: 对话模型名称
        api_key: API Key
        base_url: Base URL
        database_path: CodeQL数据库路径
        language: 编程语言
        enable_error_tidy: 是否启用错误整理功能
        prev_ql_file: 上一次的QL语句文件路径（仅用于fallback_only模式测试）
    """
    # 验证MD文件存在性
    md_path = Path(md_file_path)
    if not md_path.exists():
        print_user_error(f"❌ MD文件不存在: {md_file_path}")
        logger.error(f"MD文件不存在: {md_file_path}")
        return

    if not md_path.suffix.lower() == '.md':
        print_user_error(f"❌ 文件格式错误，需要.md文件: {md_file_path}")
        logger.error(f"文件格式错误: {md_file_path}")
        return

    # 读取MD文件内容
    try:
        md_content = md_path.read_text(encoding='utf-8')
        print_user_info(f"📄 成功读取MD文件: {md_file_path}")
        logger.info(f"读取MD文件: {md_file_path}, 内容长度: {len(md_content)}")
    except Exception as e:
        print_user_error(f"❌ 读取MD文件失败: {e}")
        logger.error(f"读取MD文件失败: {e}", exc_info=True)
        return

    prev_ql = None
    if prev_ql_file:
        prev_ql_path = Path(prev_ql_file)
        if not prev_ql_path.exists():
            print_user_error(f"❌ 指定的QL文件不存在: {prev_ql_file}")
            return
        try:
            prev_ql = prev_ql_path.read_text(encoding='utf-8')
            print_user_info(f"📄 成功读取上一次QL文件: {prev_ql_file}")
        except Exception as e:
            print_user_error(f"❌ 读取上一次QL文件失败: {e}")
            return

    # 如果没有指定数据库路径，尝试从当前目录查找
    if not database_path:
        current_dir = Path.cwd()
        for db_dir in current_dir.glob("*.db"):
            database_path = str(db_dir)
            break
        if not database_path:
            print_user_error("❌ 未找到CodeQL数据库，请使用 --database-path 参数指定")
            logger.error("未找到CodeQL数据库")
            return

    # 显示模型配置信息
    try:
        chat_config = get_llm_config(
            LLMRole.CHAT,
            provider_name=provider,
            model_name=chat_model,
            api_key=api_key,
            base_url=base_url
        )
        think_config = get_llm_config(
            LLMRole.THINK,
            provider_name=provider,
            model_name=think_model,
            api_key=api_key,
            base_url=base_url
        )

        provider_display = provider or chat_config.provider or "环境变量配置"
        print_user_info(f"🤖 使用模型提供商: {provider_display}")
        print_user_info(f"   💭 推理模型: {think_config.model}")
        print_user_info(f"   💬 对话模型: {chat_config.model}")
        if base_url:
            print_user_info(f"   🔗 Base URL: {base_url}")
    except Exception as e:
        print_user_error(f"❌ 无法配置模型: {e}")
        logger.error(f"模型配置失败: {e}", exc_info=True)
        return

    # 创建MultiAgentAnalyzer
    try:
        # 使用聊天模型配置作为主要配置
        analyzer = MultiAgentAnalyzer(config=chat_config)
        print_user_info(f"🔧 成功创建AI分析器")
    except Exception as e:
        print_user_error(f"❌ 创建AI分析器失败: {e}")
        logger.error(f"创建AI分析器失败: {e}", exc_info=True)
        return

    # 创建CodeQLComposeTool
    try:
        codeql_tool = CodeQLComposeTool(
            analyzer=analyzer,
            database_path=database_path,
            language=language,
            max_rounds=5,
            enable_error_tidy=enable_error_tidy,
            enable_source_sink_fallback=enable_source_sink_fallback,
            fallback_empty_retry_max=fallback_empty_retry_max,
        )
        print_user_info(f"🛠️  成功创建CodeQL生成工具")
        print_user_info(f"   📁 数据库路径: {database_path}")
        print_user_info(f"   💻 编程语言: {language}")
    except Exception as e:
        print_user_error(f"❌ 创建CodeQL工具失败: {e}")
        logger.error(f"创建CodeQL工具失败: {e}", exc_info=True)
        return

    # 执行CodeQL生成
    try:
        print_user_info(f"🚀 开始从MD文件生成CodeQL查询...")

        # 使用MD文件内容作为需求描述
        requirement = f"根据以下漏洞描述生成CodeQL查询:\n\n{md_content}"

        result = await codeql_tool._arun(
            requirement=requirement,
            exec_mode=exec_mode,
            show_thinking=stream,
            cve_analysis_report=requirement if exec_mode == "fallback_only" else None,
            source_analysis_report=None,
            sink_analysis_report=None,
            prev_ql=prev_ql,
        )

        # 输出结果
        print_user_success(f"\n🎉 CodeQL生成完成！")
        print_user_info(f"📋 查询结果:")
        print(result)

        logger.info(f"CodeQL生成成功完成")

    except Exception as e:
        print_user_error(f"❌ CodeQL生成失败: {e}")
        logger.error(f"CodeQL生成失败: {e}", exc_info=True)
        return

# Debug功能
async def run_md_source_analysis(
    md_file_path: str,
    src_path: str,
    stream: bool = False,
    provider: Optional[str] = None,
    think_model: Optional[str] = None,
    chat_model: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    language: Optional[str] = None,
    output_file: Optional[str] = None
) -> None:
    """
    从MD文件和源代码路径生成source点分析报告

    Args:
        md_file_path: MD文件路径
        src_path: 源代码路径
        stream: 是否显示AI思考过程
        provider: 模型提供商名称
        think_model: 推理模型名称
        chat_model: 对话模型名称
        api_key: API Key
        base_url: Base URL
        language: 编程语言（可选，不指定则自动检测）
        output_file: 输出文件路径
    """
    # 验证MD文件存在性
    md_path = Path(md_file_path)
    if not md_path.exists():
        print_user_error(f"❌ MD文件不存在: {md_file_path}")
        logger.error(f"MD文件不存在: {md_file_path}")
        return

    if not md_path.suffix.lower() == '.md':
        print_user_error(f"❌ 文件格式错误，需要.md文件: {md_file_path}")
        logger.error(f"文件格式错误: {md_file_path}")
        return

    # 验证源代码路径存在性
    src_path_obj = Path(src_path)
    if not src_path_obj.exists():
        print_user_error(f"❌ 源代码路径不存在: {src_path}")
        logger.error(f"源代码路径不存在: {src_path}")
        return

    # 读取MD文件内容
    try:
        md_content = md_path.read_text(encoding='utf-8')
        print_user_info(f"📄 成功读取MD文件: {md_file_path}")
        logger.info(f"读取MD文件: {md_file_path}, 内容长度: {len(md_content)}")
    except Exception as e:
        print_user_error(f"❌ 读取MD文件失败: {e}")
        logger.error(f"读取MD文件失败: {e}", exc_info=True)
        return

    # 自动检测编程语言（如果未指定）
    if not language:
        try:
            detector = LanguageDetector()
            # 创建临时路径对象用于检测
            temp_case_paths = type('CasePaths', (), {
                'root': src_path_obj,
                'source_dir': src_path_obj
            })()
            language = detector.detect_language(temp_case_paths)
            print_user_info(f"🔍 自动检测编程语言: {language}")
        except Exception as e:
            print_user_warning(f"⚠️  无法自动检测语言，使用默认语言 'java': {e}")
            language = "java"

    # 显示模型配置信息
    try:
        chat_config = get_llm_config(
            LLMRole.CHAT,
            provider_name=provider,
            model_name=chat_model,
            api_key=api_key,
            base_url=base_url
        )
        think_config = get_llm_config(
            LLMRole.THINK,
            provider_name=provider,
            model_name=think_model,
            api_key=api_key,
            base_url=base_url
        )

        provider_display = provider or chat_config.provider or "环境变量配置"
        print_user_info(f"🤖 使用模型提供商: {provider_display}")
        print_user_info(f"   💭 推理模型: {think_config.model}")
        print_user_info(f"   💬 对话模型: {chat_config.model}")
        if base_url:
            print_user_info(f"   🔗 Base URL: {base_url}")
    except Exception as e:
        print_user_error(f"❌ 无法配置模型: {e}")
        logger.error(f"模型配置失败: {e}", exc_info=True)
        return

    # 创建MultiAgentAnalyzer
    try:
        analyzer = MultiAgentAnalyzer(config=chat_config)
        print_user_info(f"🔧 成功创建AI分析器")
    except Exception as e:
        print_user_error(f"❌ 创建AI分析器失败: {e}")
        logger.error(f"创建AI分析器失败: {e}", exc_info=True)
        return

    # 创建UnifiedSourceAnalysisAgent
    try:
        source_agent = UnifiedSourceAnalysisAgent(
            analyzer=analyzer,
            source_root=src_path,
            database_path=None  # 不需要CodeQL数据库
        )
        print_user_info(f"🔍 成功创建Source点分析工具")
        print_user_info(f"   📁 源代码路径: {src_path}")
        print_user_info(f"   💻 编程语言: {language}")
    except Exception as e:
        print_user_error(f"❌ 创建Source分析工具失败: {e}")
        logger.error(f"创建Source分析工具失败: {e}", exc_info=True)
        return

    # 执行Source点分析
    try:
        print_user_info(f"🚀 开始从MD文件生成Source点分析报告...")

        # 使用MD文件内容作为sink分析结果（模拟）
        sink_analysis = f"根据以下漏洞描述分析Source点:\n\n{md_content}"

        result = await source_agent.analyze_sources(
            language=language,
            sink_analysis=sink_analysis,
            show_thinking=stream
        )
        if result.success:
            print_user_success(f"\n🎉 Source点分析完成！")
            report_content = f"""# Source点分析报告

## 分析配置
- **MD文件**: {md_file_path}
- **源代码路径**: {src_path}
- **编程语言**: {language}
- **分析时间**: {__import__('datetime').datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## 漏洞描述
{md_content}

## Source点分析结果
{result.content}

---
*报告由 PureAutoCodeQL 自动生成*
"""

            # 保存到文件
            if output_file:
                output_path = Path(output_file)
            else:
                timestamp = __import__('datetime').datetime.now().strftime('%Y%m%d_%H%M%S')
                output_path = Path(f"source_analysis_report_{timestamp}.md")

            try:
                output_path.write_text(report_content, encoding='utf-8')
                print_user_info(f"📄 分析报告已保存到: {output_path}")
            except Exception as e:
                print_user_error(f"❌ 保存报告失败: {e}")
                logger.error(f"保存报告失败: {e}", exc_info=True)
                # 即使保存失败，也显示结果到控制台
                print_user_info(f"📋 分析结果:")
                print(report_content)
        else:
            print_user_error(f"\n❌ Source点分析失败: {result.error}")
            logger.error(f"Source点分析失败: {result.error}")

    except Exception as e:
        print_user_error(f"❌ Source点分析失败: {e}")
        logger.error(f"Source点分析失败: {e}", exc_info=True)
        return


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
            print_user_warning(f"   ⚠️  没有Diff/Patch文件")

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



SUBCOMMANDS = {
    "analyze",
    "doctor",
    "import",
    "list",
    "md",
    "models",
    "providers",
    "serve",
    "validate",
}


def _normalize_cli_args(argv: Sequence[str]) -> list[str]:
    """Translate new subcommands to the existing flag-based parser."""
    args = list(argv)
    if not args or args[0] not in SUBCOMMANDS:
        return args

    command, rest = args[0], args[1:]
    if command == "analyze":
        if rest and not rest[0].startswith("-"):
            return ["--case", rest[0], *rest[1:]]
        return rest
    if command == "doctor":
        return ["--doctor", *rest]
    if command == "import":
        if rest and not rest[0].startswith("-"):
            return ["--import-project", rest[0], *rest[1:]]
        return rest
    if command == "list":
        return ["--list", *rest]
    if command == "md":
        if rest and not rest[0].startswith("-"):
            return ["--md-file", rest[0], *rest[1:]]
        return rest
    if command == "models":
        return ["--list-models", *rest]
    if command == "providers":
        return ["--list-providers", *rest]
    if command == "serve":
        return ["--serve", *rest]
    if command == "validate":
        if rest and not rest[0].startswith("-"):
            return ["--validate", rest[0], *rest[1:]]
        return rest
    return args


def parse_arguments(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    """解析命令行参数"""
    normalized_argv = _normalize_cli_args(sys.argv[1:] if argv is None else argv)
    parser = argparse.ArgumentParser(
        description="PureAutoCodeQL - 基于AI的自动化漏洞分析工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s --case CVE-2021-21985                    # 分析已导入的案例
  %(prog)s analyze --case CVE-2021-21985            # 子命令形式（兼容新CLI）
  %(prog)s --case "C:\\Targets\\java\\CVE-2023-51444"  # Java项目：自动导入+建库+分析
  %(prog)s --case CVE-2021-21985 --no-stream       # 禁用AI思考过程显示
  %(prog)s --doctor                                # 检查本机运行环境
  %(prog)s serve                                   # 启动本地 API 服务
  %(prog)s --import-project "C:\\Targets\\CVE-2023-51444" --import-language java  # 仅导入Java项目
  %(prog)s --md-file vulnerability.md              # 从MD文件直接生成CodeQL
  %(prog)s --md-file vulnerability.md --src-path /path/to/source  # 从MD文件生成source点分析报告
  %(prog)s --md-file vulnerability.md --provider deepseek  # 指定模型提供商
  %(prog)s --list                                  # 列出所有可用案例
  %(prog)s --validate CVE-2021-21985               # 验证案例有效性
        """
    )

    # 主要参数组
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument(
        "--case",
        type=str,
        help="分析单个案例 (例如: CVE-2021-21985)"
    )
    group.add_argument(
        "--md-file",
        type=str,
        metavar="FILE",
        help="从指定的MD文件直接生成CodeQL查询 (例如: vulnerability.md)"
    )
    group.add_argument(
        "--list",
        action="store_true",
        help="列出所有可用的案例"
    )
    group.add_argument(
        "--validate",
        type=str,
        metavar="CASE_ID",
        help="验证指定案例是否有效"
    )
    group.add_argument(
        "--list-providers",
        action="store_true",
        help="列出所有可用的模型提供商及其状态"
    )
    group.add_argument(
        "--list-models",
        action="store_true",
        help="列出硅基流动的所有可用模型"
    )
    group.add_argument(
        "--import-project",
        type=str,
        metavar="PATH",
        help="导入外部CVE目录 (例如: C:\\Targets\\CVE-2025-54381)"
    )
    group.add_argument(
        "--doctor",
        action="store_true",
        help="检查本机运行环境、依赖工具和模型配置"
    )
    group.add_argument(
        "--serve",
        action="store_true",
        help="启动 FastAPI 服务"
    )

    # 可选参数
    parser.add_argument(
        "--refresh-intel",
        action="store_true",
        help="强制刷新情报数据（不使用缓存）"
    )
    parser.add_argument(
        "--no-stream",
        dest="stream",
        action="store_false",
        help="禁用AI思考过程显示"
    )
    parser.add_argument(
        "--output",
        type=str,
        metavar="FILE",
        help="指定输出文件名 (默认: output.md)"
    )
    parser.add_argument(
        "--provider",
        type=str,
        metavar="PROVIDER",
        help="指定模型提供商 (deepseek/siliconflow/zhipu/kimi/gemini 或 keys.toml 中定义的自定义提供商)，覆盖环境变量 LLM_PROVIDER"
    )
    parser.add_argument(
        "--model",
        type=str,
        metavar="MODEL",
        help="指定模型名称（同时用于推理和对话），覆盖默认模型和环境变量"
    )
    parser.add_argument(
        "--think-model",
        type=str,
        metavar="MODEL",
        dest="think_model",
        help="指定推理模型名称，覆盖默认模型和环境变量"
    )
    parser.add_argument(
        "--chat-model",
        type=str,
        metavar="MODEL",
        dest="chat_model",
        help="指定对话模型名称，覆盖默认模型和环境变量"
    )
    parser.add_argument(
        "--api-key",
        type=str,
        metavar="KEY",
        dest="api_key",
        help="指定API Key，覆盖环境变量"
    )
    parser.add_argument(
        "--base-url",
        type=str,
        metavar="URL",
        dest="base_url",
        help="指定Base URL，覆盖环境变量（例如: https://api.siliconflow.cn/v1）"
    )
    parser.add_argument(
        "--database-path",
        type=str,
        metavar="PATH",
        dest="database_path",
        help="指定CodeQL数据库路径（用于--md-file模式）"
    )
    parser.add_argument(
        "--language",
        type=str,
        metavar="LANG",
        dest="language",
        default=None,
        help="指定编程语言（默认: java 或自动检测）"
    )
    parser.add_argument(
        "--src-path",
        type=str,
        metavar="PATH",
        dest="src_path",
        help="指定源代码路径（用于--md-file模式生成source点分析报告）"
    )
    parser.add_argument(
        "--enable-error-tidy",
        action="store_true",
        help="启用错误整理功能（实验性）"
    )
    parser.add_argument(
        "--enable-source-sink-fallback",
        action="store_true",
        help="启用 Source-Sink 回退查询（所有常规 CodeQL 重试失败后）",
    )
    parser.add_argument(
        "--source-sink-only",
        action="store_true",
        help="仅使用 Source-Sink 回退代理生成不含中间路径的查询（测试模式，仅用于 --md-file）",
    )
    parser.add_argument(
        "--enable-sink-source-verification",
        action="store_true",
        help="启用 Sink/Source 验证功能（实验性）",
    )
    parser.add_argument(
        "--verification-retry-max",
        type=int,
        metavar="N",
        dest="verification_retry_max",
        default=3,
        help="Sink/Source 验证失败时的最大重试次数（默认: 3）",
    )
    parser.add_argument(
        "--verification-timeout",
        type=int,
        metavar="SECONDS",
        dest="verification_timeout",
        default=30,
        help="单个验证查询的超时时间（秒，默认: 30）",
    )
    parser.add_argument(
        "--import-case-id",
        type=str,
        dest="import_case_id",
        help="搭配 --import-project 使用，自定义案例ID"
    )
    parser.add_argument(
        "--import-overwrite",
        action="store_true",
        dest="import_overwrite",
        help="搭配 --import-project 使用，若案例已存在则覆盖"
    )
    parser.add_argument(
        "--import-language",
        type=str,
        dest="import_language",
        help="搭配 --import-project 使用，指定语言：python/java/cpp（默认自动检测）"
    )
    parser.add_argument(
        "--import-skip-codeql",
        action="store_true",
        dest="import_skip_codeql",
        help="搭配 --import-project 使用，跳过 CodeQL 数据库创建"
    )
    parser.add_argument(
        "--import-build-command",
        type=str,
        dest="import_build_command",
        help="搭配 --import-project 使用，指定 C/C++ 构建命令（Java/Python项目不需要）"
    )
    parser.add_argument(
        "--import-build-script",
        type=str,
        dest="import_build_script",
        help="搭配 --import-project 使用，指定 C/C++ 构建脚本路径（Java/Python项目不需要）"
    )
    parser.add_argument(
        "--import-build-dir",
        type=str,
        dest="import_build_workdir",
        help="搭配 --import-project 使用，设置 C/C++ 构建命令工作目录（Java/Python项目不需要）"
    )

    parser.add_argument(
        "--prev-ql-file",
        type=str,
        metavar="FILE",
        dest="prev_ql_file",
        help="指定上一次生成的 CodeQL 查询文件（用于测试 Source-Sink Fallback）"
    )
    parser.add_argument(
        "--host",
        type=str,
        dest="serve_host",
        help="搭配 serve/--serve 使用，覆盖 API_HOST"
    )
    parser.add_argument(
        "--port",
        type=int,
        dest="serve_port",
        help="搭配 serve/--serve 使用，覆盖 API_PORT"
    )
    parser.add_argument(
        "--reload",
        action="store_true",
        dest="serve_reload",
        help="搭配 serve/--serve 使用，启用 uvicorn reload"
    )

    parser.set_defaults(stream=True)

    return parser.parse_args(normalized_argv)


def list_providers() -> None:
    """列出所有可用的模型提供商"""
    providers = list_available_providers()

    print("\n" + "=" * 80)
    print("📋 可用的模型提供商:")
    print("=" * 80)

    for provider in providers:
        print(f"\n🔹 {provider['display_name']} ({provider['name']})")
        print(f"   状态: {provider['status']}")
        print(f"   推理模型: {provider['think_model']}")
        print(f"   对话模型: {provider['chat_model']}")
        print(f"   Base URL: {provider['base_url']}")
        if not provider['has_api_key']:
            print(f"   ⚠️  需要设置 API Key 环境变量")

    print("\n" + "=" * 80)
    print("💡 使用方式:")
    print("   python Analyze.py --case CVE-2021-21985 --provider deepseek")
    print("   export LLM_PROVIDER=deepseek  # 或通过环境变量设置")
    print("=" * 80 + "\n")


def _resolve_model_args(args: argparse.Namespace) -> tuple[Optional[str], Optional[str]]:
    """Return the effective think/chat model names for a parsed CLI command."""
    return args.think_model or args.model, args.chat_model or args.model


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

    print(f"🚀 PureAutoCodeQL 启动")
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
