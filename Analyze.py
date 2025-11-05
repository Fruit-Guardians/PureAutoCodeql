""""
入口文件
用于协调多Agent漏洞分析工作流。
"""

import argparse
import asyncio
import json
import requests
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_openai import ChatOpenAI

# 导入集中化配置
from config import get_chat_config, LLMConfig, get_resilient_llm_config, LLMRole

# Import agents and utilities
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.unified_sink_path_agent import UnifiedSinkPathAgent
from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
from tools.codeql_compose import CodeQLComposeTool
from utils.case import (
    CasePaths,
    CveAssets,
    default_language_db,
    discover_cve_assets,
    resolve_case,
)
from utils.intel import IntelBundle, collect_intel
from utils.io import write_analysis_output


class CodeQLLSPService:
    """CodeQL LSP语法检查服务管理器。"""
    
    def __init__(self, pack_root: str = None):
        self.pack_root = pack_root or str(Path.cwd())
        self.process = None
        self.port = 8766
        self.base_url = f"http://127.0.0.1:{self.port}"
        self.init_timeout = 60  # 增加初始化超时时间到60秒
    
    def start(self) -> bool:
        """启动LSP服务。"""
        try:
            # 检查codeql是否可用
            #print(f"检查CodeQL命令: {self.codeql_path}")
            #result = subprocess.run([self.codeql_path, "version"], check=True, 
            #              capture_output=True, text=True)
            #print(f"CodeQL版本检查成功: {result.stdout.strip()}")
            
            # 启动LSP服务
            cmd = [
                sys.executable, "-m", "tools.lsp_codeql",
                "--pack-root", self.pack_root,
                "--port", str(self.port),
                "--quiet-logs"
            ]
            
            print(f"启动LSP服务命令: {' '.join(cmd)}")
            self.process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            # 等待服务启动
            print(f"等待LSP服务启动... (超时时间: {self.init_timeout}秒)")
            for i in range(self.init_timeout):  # 最多等待指定秒数
                try:
                    response = requests.get(f"{self.base_url}/health", timeout=1)
                    if response.status_code == 200:
                        print(f"✅ LSP服务在第{i+1}秒启动成功")
                        return True
                except Exception as e:
                    if i % 5 == 0:  # 每5秒显示一次等待状态
                        print(f"等待LSP服务启动... ({i+1}/{self.init_timeout}秒)")
                time.sleep(1)
            
            # 如果服务启动超时，检查进程输出
            print("❌ LSP服务启动超时")
            if self.process and self.process.poll() is not None:
                stdout, stderr = self.process.communicate()
                print(f"LSP进程退出码: {self.process.returncode}")
                if stdout:
                    print(f"LSP进程标准输出: {stdout}")
                if stderr:
                    print(f"LSP进程错误输出: {stderr}")
            
            return False
            
        except Exception as e:
            print(f"❌ LSP服务启动失败: {e}")
            import traceback
            traceback.print_exc()
            return False
    
    def check_syntax(self, codeql_code: str) -> Dict[str, Any]:
        """检查CodeQL代码语法。"""
        try:
            response = requests.post(
                f"{self.base_url}/check",
                json={"code": codeql_code},
                timeout=10
            )
            
            if response.status_code == 200:
                return response.json()
            else:
                return {"error": f"HTTP {response.status_code}"}
                
        except Exception as e:
            return {"error": str(e)}
    
    def stop(self):
        """停止LSP服务。"""
        if self.process:
            try:
                # 发送关闭请求
                requests.post(f"{self.base_url}/shutdown", timeout=5)
            except:
                pass
            
            # 等待进程结束
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.terminate()
            
            self.process = None


@dataclass
class AgentResult:
    """Agent执行的结果。"""

    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """用于漏洞分析工作流的多Agent分析器。"""

    def __init__(self, config: LLMConfig = None):
        """初始化多Agent分析器。"""
        # 优先使用外部传入；否则采用具备自动切换的配置（网络不好时自动换服务商）
        self.config = config or get_resilient_llm_config(LLMRole.CHAT)
        self.llm = None
        self.mcp_client = None
        self.tools = None

    async def initialize(self) -> None:
        """初始化LLM和MCP客户端以便在多个Agent之间复用。"""
        self.llm = ChatOpenAI(
            model=self.config.model,
            api_key=self.config.api_key,
            base_url=self.config.base_url,
            temperature=self.config.temperature,
            streaming=self.config.streaming,
            max_tokens=self.config.max_tokens,
            max_retries=self.config.max_retries,
        )

        self.mcp_client = MultiServerMCPClient(
            {
                "filesystem": {
                    "command": "npx",
                    "args": [
                        "-y",
                        "@modelcontextprotocol/server-filesystem",
                        str(Path.cwd() / "projects"),
                    ],
                    "transport": "stdio",
                }
            }
        )

        self.tools = await self.mcp_client.get_tools()

    async def run_agent(self, prompt: str, show_thinking: bool = True) -> AgentResult:
        """使用给定的提示词运行单个Agent，可选择显示思考过程。"""
        try:
            if not self.llm or not self.tools:
                await self.initialize()

            agent = create_agent(self.llm, self.tools)
            content_parts = []

            async for event in agent.astream_events(
                {"messages": [("user", prompt)]}, version="v1", config={"recursion_limit": 100}
            ):
                event_name = event.get("event")

                # 显示AI的思考过程
                if show_thinking:
                    if event_name == "on_agent_action":
                        # AI决定使用工具
                        action = event.get("data", {}).get("action")
                        if action and hasattr(action, "tool"):
                            print(f"🤔 AI思考: 决定使用工具 '{action.tool}'")
                            if hasattr(action, "tool_input") and action.tool_input:
                                print(f"   工具输入: {action.tool_input}")

                    elif event_name == "on_tool_start":
                        # 工具开始执行
                        tool_name = event.get("name", "")
                        print(f"🔧 工具执行: {tool_name}")

                    elif event_name == "on_tool_end":
                        # 工具执行完成
                        tool_name = event.get("name", "")
                        output = event.get("data", {}).get("output", "")
                        if output:
                            # 截断过长的输出
                            output_preview = str(output)[:200] + ("..." if len(str(output)) > 200 else "")
                            print(f"✅ 工具完成: {tool_name} - 输出: {output_preview}")
                        else:
                            print(f"✅ 工具完成: {tool_name}")

                    elif event_name == "on_agent_step":
                        # AI完成一步思考
                        step_output = event.get("data", {}).get("output", "")
                        if step_output and hasattr(step_output, "intermediate_steps"):
                            print("💭 AI完成一步推理")

                # 捕获最终的模型输出
                if event_name == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join(
                                    [
                                        c.get("text", "")
                                        for c in chunk.content
                                        if isinstance(c, dict)
                                    ]
                                )
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            content_parts.append(text)
                            # 实时显示AI的最终回答
                            if show_thinking:
                                print(text, end="", flush=True)

            final_content = "".join(content_parts)
            if show_thinking:
                print("\n🎯 AI推理完成")

            return AgentResult(content=final_content, success=True)

        except Exception as e:
            if show_thinking:
                print(f"❌ 推理出错: {e}")
            return AgentResult(content="", success=False, error=str(e))


async def run_multi_agent_analysis(
    json_path: str = "",
    diff_path: str = "",
    source_root: str = "",
    db_path: str = "",
    intel_bundle: Optional[IntelBundle] = None,
    stream: bool = False,
    language: str = "java",
) -> None:
    """运行完整的多Agent分析工作流，包括CVE、Sink、Source和CodeQL生成器Agent。

    Args:
        json_path: CVE JSON文件的路径
        diff_path: diff文件的路径
        source_root: 源文件的根目录
        db_path: CodeQL数据库路径
        intel_bundle: 可选的智能包用于增强分析
        stream: 是否显示AI思考过程
        language: 编程语言 (java, python, cpp)
    """
    import time

    start_time = time.time()

    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()

        resolved_db_path = db_path
        try:
            if db_path:
                db_root = Path(db_path)
                if not db_root.is_absolute():
                    db_root = db_root.resolve()
                if not db_root.exists():
                    potential = (Path.cwd() / db_path).resolve()
                    if potential.exists():
                        db_root = potential
                lang_key = (language or "").lower()
                candidate_paths = []
                if lang_key and db_root.name.lower() != lang_key:
                    candidate_paths.append(db_root / lang_key)
                    if lang_key == "cpp":
                        candidate_paths.extend([db_root / "c", db_root / "cpp"])
                for candidate in candidate_paths:
                    if candidate.exists():
                        resolved_db_path = str(candidate)
                        break
                else:
                    if db_root.exists():
                        resolved_db_path = str(db_root)
        except Exception:
            resolved_db_path = db_path
        db_path = resolved_db_path

        cve_agent = CVEAnalysisAgent(analyzer)

        # 使用统一的sink agent替代三个独立的agent
        from agents.unified_sink_path_agent import UnifiedSinkPathAgent
        sink_agent = UnifiedSinkPathAgent(analyzer, source_root)
        sink_analysis_name = f"{language.title()} Sink Path Analysis"

        # 使用统一的source分析器替代三个独立的agent
        from agents.unified_source_analysis_agent import UnifiedSourceAnalysisAgent
        source_agent = UnifiedSourceAnalysisAgent(analyzer, source_root, db_path)
        source_analysis_name = f"{language.title()} Source Analysis"

        codeql_tool = CodeQLComposeTool(
            analyzer=analyzer,
            database_path=db_path,
            language=language,
        )

        print("=== CVE Analysis ===")
        # 将IntelBundle转换为intel_prompt字符串
        intel_prompt = intel_bundle.prompt_block() if intel_bundle else None
        cve_result = await cve_agent.analyze_cve(
            Path(json_path), intel_prompt=intel_prompt, show_thinking=stream
        )
        if not cve_result.success:
            print(f"CVE analysis failed: {cve_result.error}")
        else:
            print(cve_result.content)
        print()

        print(f"=== {sink_analysis_name} ===")
        # 使用统一的sink分析方法
        sink_result = await sink_agent.analyze_paths(
            language, cve_result.content if cve_result.success else "", diff_path, show_thinking=stream
        )

        if not sink_result.success:
            print(f"{language.title()} sink analysis failed: {sink_result.error}")
        else:
            print(sink_result.content)
        print()

        print(f"=== {source_analysis_name} ===")
        # 使用统一的source分析方法
        source_result = await source_agent.analyze_sources(
            language, sink_result.content if sink_result.success else "", show_thinking=stream
        )

        if not source_result.success:
            print(f"{language.title()} source analysis failed: {source_result.error}")
        else:
            print(source_result.content)

        print("=== CodeQL Query Generation ===")
        # 构建CodeQL生成需求，包含sink和source分析结果
        codeql_requirement = f"""
        基于以下分析结果生成CodeQL查询：
        CVE路径分析结果：
        {cve_result.content if cve_result.success else "CVE分析失败"}

        Sink路径分析结果：
        {sink_result.content if sink_result.success else "Sink分析失败"}

        Source分析结果：
        {source_result.content if source_result.success else "Source分析失败"}

        请基于上述分析生成一个完整的CodeQL查询，用于检测相关的安全漏洞。
        """

        # CodeQL 生成使用推理模型
        from config import get_think_config
        codeql_analyzer = MultiAgentAnalyzer(get_think_config())
        await codeql_analyzer.initialize()

        # 这里直接调用CodeQLComposeTool的run方法
        codeql_tool = CodeQLComposeTool(
            analyzer=codeql_analyzer,
            database_path=db_path,
            language=language,
        )

        # 调用CodeQLComposeTool的_arun方法，该方法内部已包含LSP语法检查
        print("🔍 调用CodeQLComposeTool进行查询生成和语法检查...")
        compose_output = await codeql_tool._arun(codeql_requirement, show_thinking=stream)
        print(compose_output)
        
        # Wrap compose output in AgentResult-like object
        codeql_result = AgentResult(
            content=compose_output,
            success=not str(compose_output).startswith("Error"),
            error=None
            if not str(compose_output).startswith("Error")
            else compose_output,
        )
        print("Generated and validated CodeQL (compose)")

        # Compose already executed the query for validation; skip separate runner
        codeql_execution_result = None

        write_analysis_output(
            cve_result,
            sink_result,
            source_result,
            Path("output.md"),
            codeql_result=codeql_result,
            codeql_execution_result=codeql_execution_result,
        )

        # 计算总耗时
        end_time = time.time()
        total_time = end_time - start_time
        print(f"\n=== 分析完成 ===")
        print(f"总耗时: {total_time:.2f} 秒")
        print(
            f"开始时间: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(start_time))}"
        )
        print(
            f"结束时间: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(end_time))}"
        )

    except Exception as e:
        print(f"Multi-agent analysis error: {e}")
        # 即使出错也显示已用时间
        end_time = time.time()
        total_time = end_time - start_time
        print(f"分析失败，已用时间: {total_time:.2f} 秒")


def parse_arguments() -> argparse.Namespace:
    """解析命令行参数。"""
    parser = argparse.ArgumentParser(description="多Agent漏洞分析工具")

    # 主要参数组
    parser.add_argument(
        "--case", type=str, help="案例ID (例如: CVE-2021-21985)", metavar="CASE_ID"
    )

    # 可选参数组

    parser.add_argument("--refresh-intel", action="store_true", help="强制刷新情报数据")
    parser.add_argument(
        "--no-stream",
        dest="stream",
        action="store_false",
        help="禁用AI思考过程显示"
    )
    parser.set_defaults(stream=True)

    return parser.parse_args()


async def run_case_analysis(
    case_id: str, cve_id: Optional[str] = None, refresh_intel: bool = False, stream: bool = False
) -> None:
    """运行基于案例的分析工作流。"""
    try:
        # 解析案例结构
        case_paths = resolve_case(case_id)
        print(f"正在分析案例: {case_id}")
        print(f"案例根目录: {case_paths.root}")
        if stream:
            print("🔍 启用AI思考过程显示模式")

        # 发现CVE资产
        cve_assets = discover_cve_assets(case_paths)
        print(f"🎯 分析CVE: {cve_assets.cve_id}")
        if cve_assets.json_path.exists():
            print(f"📁 JSON文件: {cve_assets.json_path} (本地)")
        else:
            print(f"🌐 JSON文件: {cve_assets.json_path} (网络获取)")
        if cve_assets.diff_path:
            print(f"📄 Diff文件: {cve_assets.diff_path} (本地)")
        else:
            print(f"⚠️  Diff文件: 无 (将以无diff模式分析)")

        # 收集情报数据
        print("正在收集漏洞情报...")
        intel_bundle = collect_intel(
            case_paths, cve_assets, use_cache=not refresh_intel
        )

        if intel_bundle.used_cache:
            print("✓ 使用缓存的情报数据")
        else:
            print("✓ 已获取最新情报数据")

        # 检测语言并选择相应的分析器
        language = detect_language(case_paths)
        print(f"检测到语言: {language}")

        # 根据语言选择分析器
        if language == "java":
            await run_java_analysis(case_paths, cve_assets, intel_bundle, stream)
        elif language == "python":
            await run_python_analysis(case_paths, cve_assets, intel_bundle, stream)
        elif language == "cpp":
            await run_c_analysis(case_paths, cve_assets, intel_bundle, stream)
        else:
            print(f"警告: 不支持的语言 '{language}'，使用默认分析器")
            await run_default_analysis(case_paths, cve_assets, intel_bundle, stream)

    except Exception as e:
        print(f"案例分析错误: {e}")


def detect_language(case_paths: CasePaths) -> str:
    """检测案例使用的编程语言。"""
    # 检查数据库目录中的语言子目录
    if (case_paths.db / "java").exists():
        return "java"
    elif (case_paths.db / "python").exists():
        return "python"
    elif (case_paths.db / "cpp").exists():
        return "cpp"

    # 检查源码目录中的文件类型
    java_files = list(case_paths.source_code.rglob("*.java"))
    python_files = list(case_paths.source_code.rglob("*.py"))
    c_files = list(case_paths.source_code.rglob("*.c"))
    cpp_files = list(case_paths.source_code.rglob("*.cpp"))
    h_files = list(case_paths.source_code.rglob("*.h"))

    if java_files:
        return "java"
    elif python_files:
        return "python"
    elif cpp_files:
        return "cpp"
    elif c_files or h_files:
        return "cpp"

    # 无法检测到语言时抛出异常
    raise ValueError(
        "无法检测到编程语言。请确保数据库目录包含有效的语言子目录或源码目录包含可识别的源文件。"
    )


async def run_java_analysis(
    case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False
) -> None:
    """运行Java分析工作流。"""
    print("☕ 使用Java分析器...")

    # 使用案例中的路径
    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)
    db_candidate = default_language_db(case_paths, "java")
    db_path = str(db_candidate or case_paths.db)

    await run_multi_agent_analysis(
        json_path, diff_path, source_root, db_path, intel_bundle, stream, "java"
    )


async def run_python_analysis(
    case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False
) -> None:
    """运行Python分析工作流。"""
    print("🐍 使用Python分析器...")

    # 使用案例中的路径
    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)
    db_candidate = default_language_db(case_paths, "python")
    db_path = str(db_candidate or case_paths.db)

    await run_multi_agent_analysis(
        json_path, diff_path, source_root, db_path, intel_bundle, stream, "python"
    )


async def run_c_analysis(
    case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False
) -> None:
    """运行C语言分析工作流。"""
    print("⚙️ 使用C语言分析器...")

    # 使用案例中的路径
    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)
    db_candidate = default_language_db(case_paths, "cpp")
    db_path = str(db_candidate or case_paths.db)

    await run_multi_agent_analysis(
        json_path, diff_path, source_root, db_path, intel_bundle, stream, "cpp"
    )


async def run_default_analysis(
    case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle, stream: bool = False
) -> None:
    """运行默认分析工作流。"""
    print("🔧 使用默认分析器...")

    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)

    language = detect_language(case_paths)
    db_candidate = default_language_db(case_paths, language)
    db_path = str(db_candidate or case_paths.db)

    await run_multi_agent_analysis(
        json_path, diff_path, source_root, db_path, intel_bundle, stream, language
    )


async def main() -> None:
    """主函数，支持命令行参数。"""
    args = parse_arguments()

    if not args.case:
        print("错误: 必须提供 --case 参数指定要分析的案例ID")
        print(
            "用法: python Analyze.py --case <CASE_ID> [--refresh-intel] [--no-stream]"
        )
        return
    await run_case_analysis(args.case, None, args.refresh_intel, args.stream)


if __name__ == "__main__":
    asyncio.run(main())
