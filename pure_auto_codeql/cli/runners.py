"""CLI run-* entry points for case, direct-CodeQL, and source analyses."""

from pathlib import Path
from typing import Optional

from pure_auto_codeql.agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from pure_auto_codeql.configuration import LLMRole, get_llm_config
from pure_auto_codeql.core.context import AnalysisConfig
from pure_auto_codeql.core.orchestrator import AnalysisOrchestrator
from pure_auto_codeql.services.language_detector import LanguageDetector
from pure_auto_codeql.services.llm_service import MultiAgentAnalyzer
from pure_auto_codeql.tools.codeql_compose import CodeQLComposeTool
from pure_auto_codeql.utils.logger import (
    get_logger,
    print_user_error,
    print_user_info,
    print_user_success,
    print_user_warning,
)

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
        print_user_success("\n🎉 分析成功完成！")
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
            logger.info("分析结果已保存到时间戳目录")
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
        print_user_info("🔧 成功创建AI分析器")
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
        print_user_info("🛠️  成功创建CodeQL生成工具")
        print_user_info(f"   📁 数据库路径: {database_path}")
        print_user_info(f"   💻 编程语言: {language}")
    except Exception as e:
        print_user_error(f"❌ 创建CodeQL工具失败: {e}")
        logger.error(f"创建CodeQL工具失败: {e}", exc_info=True)
        return

    # 执行CodeQL生成
    try:
        print_user_info("🚀 开始从MD文件生成CodeQL查询...")

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
        print_user_success("\n🎉 CodeQL生成完成！")
        print_user_info("📋 查询结果:")
        print(result)

        logger.info("CodeQL生成成功完成")

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
        print_user_info("🔧 成功创建AI分析器")
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
        print_user_info("🔍 成功创建Source点分析工具")
        print_user_info(f"   📁 源代码路径: {src_path}")
        print_user_info(f"   💻 编程语言: {language}")
    except Exception as e:
        print_user_error(f"❌ 创建Source分析工具失败: {e}")
        logger.error(f"创建Source分析工具失败: {e}", exc_info=True)
        return

    # 执行Source点分析
    try:
        print_user_info("🚀 开始从MD文件生成Source点分析报告...")

        # 使用MD文件内容作为sink分析结果（模拟）
        sink_analysis = f"根据以下漏洞描述分析Source点:\n\n{md_content}"

        result = await source_agent.analyze_sources(
            language=language,
            sink_analysis=sink_analysis,
            show_thinking=stream
        )
        if result.success:
            print_user_success("\n🎉 Source点分析完成！")
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
                print_user_info("📋 分析结果:")
                print(report_content)
        else:
            print_user_error(f"\n❌ Source点分析失败: {result.error}")
            logger.error(f"Source点分析失败: {result.error}")

    except Exception as e:
        print_user_error(f"❌ Source点分析失败: {e}")
        logger.error(f"Source点分析失败: {e}", exc_info=True)
        return
