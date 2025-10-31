import asyncio
import argparse
from pathlib import Path
from typing import Optional, Dict, Any, List
from dataclasses import dataclass

from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent
from langchain_openai import ChatOpenAI

# Import agents and utilities
from agents.cve_analysis_agent import CVEAnalysisAgent
from agents.java_sink_path_agent import JavaPathAnalysisAgent
from agents.java_source_analysis_agent import JavaSourceAnalysisAgent
from agents.codeql_generator_agent import CodeQLGeneratorAgent
from agents.codeql_runner_agent import CodeQLRunnerAgent
from utils.io import write_analysis_output
from utils.case import resolve_case, discover_cve_assets, default_language_db, CasePaths, CveAssets
from utils.intel import collect_intel, IntelBundle


@dataclass
class AgentConfig:
    """用于创建具有一致设置的Agent的配置。"""
    # model: str = "gpt-5-chat-latest"
    # api_key: str = "sk-34hrOV0eZWNgcNTGPXXpLJ086uRoXmA7aCPTVICu2gAZQ7tu"
    # base_url: str = "https://yunwu.ai/v1"
    model: str = "deepseek-reasoner"
    api_key: str = "sk-a2d1b4e295d6404694f45f45cb236c91"
    base_url: str = "https://api.deepseek.com/v1"
    temperature: float = 0
    streaming: bool = True
    max_tokens: Optional[int] = None  # 最大输出token数量
    max_retries: int = 3  # 最大重试次数



@dataclass
class AgentResult:
    """Agent执行的结果。"""
    content: str
    success: bool
    error: Optional[str] = None


class MultiAgentAnalyzer:
    """用于漏洞分析工作流的多Agent分析器。"""
    
    def __init__(self, config: AgentConfig = None):
        """初始化多Agent分析器。"""
        self.config = config or AgentConfig()
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
                        str(Path.cwd()),
                    ],
                    "transport": "stdio",
                }
            }
        )
        
        self.tools = await self.mcp_client.get_tools()
    
    async def run_agent(self, prompt: str) -> AgentResult:
        """使用给定的提示词运行单个Agent。"""
        try:
            if not self.llm or not self.tools:
                await self.initialize()
            
            agent = create_agent(self.llm, self.tools)
            content_parts = []
            
            async for event in agent.astream_events({"messages": [("user", prompt)]}, version="v1"):
                if event.get("event") == "on_chat_model_stream":
                    chunk = event.get("data", {}).get("chunk")
                    if hasattr(chunk, "content") and chunk.content:
                        try:
                            text = (
                                "".join([c.get("text", "") for c in chunk.content if isinstance(c, dict)])
                                if isinstance(chunk.content, list)
                                else str(chunk.content)
                            )
                        except Exception:
                            text = str(chunk.content)
                        if text:
                            content_parts.append(text)
            
            return AgentResult(content="".join(content_parts), success=True)
        
        except Exception as e:
            return AgentResult(content="", success=False, error=str(e))


async def run_multi_agent_analysis(json_path: str = "", 
                                 diff_path: str = "",
                                 source_root: str = "",
                                 db_path: str = "",
                                 intel_bundle: Optional[IntelBundle] = None) -> None:
    """运行完整的多Agent分析工作流，包括CVE、Sink、Source和CodeQL生成器Agent。
    
    Args:
        json_path: CVE JSON文件的路径
        diff_path: diff文件的路径
        source_root: Java源文件的根目录
        db_path: CodeQL数据库路径
        intel_bundle: 可选的智能包用于增强分析
    """
    import time
    start_time = time.time()
    
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        cve_agent = CVEAnalysisAgent(analyzer)
        sink_agent = JavaPathAnalysisAgent(analyzer, source_root)
        source_agent = JavaSourceAnalysisAgent(analyzer, source_root)
        codeql_agent = CodeQLGeneratorAgent(analyzer)
        
        print("=== CVE Analysis ===")
        # 将IntelBundle转换为intel_prompt字符串
        intel_prompt = intel_bundle.prompt_block() if intel_bundle else None
        cve_result = await cve_agent.analyze_cve(Path(json_path), intel_prompt=intel_prompt)
        if not cve_result.success:
            print(f"CVE analysis failed: {cve_result.error}")
        else:
            print(cve_result.content)
        print()
        
        print("=== Java Sink Path Analysis ===")
        sink_result = await sink_agent.analyze_java_paths(cve_result.content if cve_result.success else "", diff_path)
        if not sink_result.success:
            print(f"Java sink analysis failed: {sink_result.error}")
        else:
            print(sink_result.content)
        print()
        
        print("=== Java Source Analysis ===")
        source_result = await source_agent.analyze_java_sources(sink_result.content if sink_result.success else "")
        if not source_result.success:
            print(f"Java source analysis failed: {source_result.error}")
        else:
            print(source_result.content)
        
        print("=== CodeQL Query Generation ===")
        # 构建CodeQL生成需求，包含sink和source分析结果
        codeql_requirement = f"""
        基于以下漏洞分析：
        
        CVE分析: {cve_result.content if cve_result.success else "N/A"}
        
        Sink点分析: {sink_result.content if sink_result.success else "N/A"}
        
        Source点分析: {source_result.content if source_result.success else "N/A"}
        
        生成一个全面的CodeQL查询，要求：
        1. 识别source点（不受信任数据进入系统的位置）
        2. 识别sink点（数据以潜在危险方式使用的位置）
        3. 跟踪从source到sink的数据流
        4. 基于以上分析检测潜在的安全漏洞
        5. 严格遵守以下规则生成codeql
        参考这个，poc版本，规范
---
# JAVA Path Query 编写规范

用于生成 JAVA CodeQL `path-problem` 查询时的强制模板与语法约束。写入前请仔细阅读，所有生成的查询必须遵循此文档。

## 1. 固定骨架（禁止改动）

```ql
/**
 * @kind path-problem
 * @name <NAME>
 * @description <DESCRIPTION>
 * @id <ID>
 * @tags <TAG-LIST>
 * @severity <SEVERITY>
 * @precision <PRECISION>
 */

import semmle.code.java.dataflow.FlowSources
private import semmle.code.java.dataflow.TaintTracking

module VulnConfig implements DataFlow::ConfigSig"""+""" {
  predicate isSource(DataFlow::Node src) { /* TODO: sources */ }
  predicate isSink(DataFlow::Node sink)   { /* TODO: sinks */ }

  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { /* optional */（默认为none()） }
}

module Flow = TaintTracking::Global<VulnConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "<diagnostic message>",
  src, "source", sink, "sink"
```

- 只允许在 `VulnConfig` 内补充逻辑。骨架中的导入、模块、`select` 语句不可删改或重排。
- 占位字符串（`<NAME>`、`<ID>` 等）由业务逻辑填充，但字段本身必须保留。

## 2. 编写约束

- `isSource`、`isSink`、`isAdditionalFlowStep`、`DataFlow::Node`。
- 组合逻辑使用 `and`、`or`、`not`；返回布尔表达式。
- 不许再使用MethodAccess，必须使用MethodCall，MethodAccess已经被废弃。
- 不要随意import模块，必须百分百确定在新版本的codeql中这个模块确实存在且有用

## 3. 常用类型与方法

- `Flow::PathNode`
  - `Flow::flowPath(src, sink)` 判断是否存在污点路径
  - `src.getNode()`、`sink.getNode()` 获取真实位置
- `sink或者source定位使用asExpr()或者asParameter()`
  - `sink.asExpr() = mdc.getArgument(0)`表示sink点位于某个函数调用处的第一个参数
  - `sink.asParameter() = md.getParameter(0)`表示sink点位于某个函数传入的第一个参数
  - source点同理
- `Annotation`
  - 直接使用hasAnnotation是不对的，必须使用getAnAnnotation()
  - `exists(Annotation a | a = m.getAnAnnotation() and a.getType().hasQualifiedName("org.springframework.web.bind.annotation", "RequestMapping"))`For method annotations
  

## 4. Do & Don't

Do:
- 使用 `exists(... | ...)` 限定辅助变量范围。
- 元数据字段保持完整，诊断消息描述清晰。

Don't:
- 不要引用旧版 API。
- 不要调用 `internal/` 下的模块或类。
- 不要改变 `select` 参数顺序与数量。

## 5. 验证流程

1. 保存生成的 `.ql` 文件。
2. 执行 `codeql query compile path/to/query.ql` 做语法检查。
3. 编译失败时，记录错误信息与相关谓词片段，反馈给生成模型做最小修复。

## 6. 参考库清单

常用导入：
- `semmle.code.java.dataflow.FlowSources`
- `semmle.code.java.dataflow.TaintTracking`
保持此清单与本地 SDK 同步，更新后及时调整提示词和骨架。
        
        The query should be written for Java language analysis.
        """
        
        codeql_result = await codeql_agent.generate_codeql(codeql_requirement, language="java")
        if not codeql_result.success:
            print(f"CodeQL generation failed: {codeql_result.error}")
        else:
            print("Generated CodeQL Query:")
           #print(codeql_result.content)
        
        # 创建CodeQL执行agent并执行查询
        print("=== CodeQL Query Execution ===")
        codeql_runner_agent = CodeQLRunnerAgent(analyzer, database_path=db_path)
        
        # 提取CodeQL代码（如果包含在<codeql>标签中）
        import re
        codeql_content = codeql_result.content
        print(codeql_content)
        codeql_match = re.search(r'<codeql>(.*?)</codeql>', codeql_content, re.DOTALL)
        if codeql_match:
            codeql_content = codeql_match.group(1).strip()
        
        # 执行CodeQL查询并分析结果
        codeql_execution_result = await codeql_runner_agent.execute_and_analyze(codeql_content)
        if not codeql_execution_result.success:
            print(f"CodeQL execution failed: {codeql_execution_result.error}")
        else:
            print("CodeQL Execution Analysis:")
            print(codeql_execution_result.content)
        
        write_analysis_output(cve_result, sink_result, source_result, Path("output.md"), codeql_result=codeql_result, codeql_execution_result=codeql_execution_result)
        
        # 计算总耗时
        end_time = time.time()
        total_time = end_time - start_time
        print(f"\n=== 分析完成 ===")
        print(f"总耗时: {total_time:.2f} 秒")
        print(f"开始时间: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(start_time))}")
        print(f"结束时间: {time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(end_time))}")
        
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
        "--case", 
        type=str,
        help="案例ID (例如: CVE-2021-21985)",
        metavar="CASE_ID"
    )
    
    # 可选参数组
    parser.add_argument(
        "--cve",
        type=str,
        help="指定要分析的CVE ID (当案例中有多个CVE时使用)",
        metavar="CVE_ID"
    )
    
    parser.add_argument(
        "--refresh-intel",
        action="store_true",
        help="强制刷新情报数据"
    )
    
    
    return parser.parse_args()


async def run_case_analysis(case_id: str, cve_id: Optional[str] = None, refresh_intel: bool = False) -> None:
    """运行基于案例的分析工作流。"""
    try:
        # 解析案例结构
        case_paths = resolve_case(case_id)
        print(f"正在分析案例: {case_id}")
        print(f"案例根目录: {case_paths.root}")
        
        # 发现CVE资产
        cve_assets = discover_cve_assets(case_paths, preferred_cve=cve_id)
        print(f"分析CVE: {cve_assets.cve_id}")
        print(f"JSON文件: {cve_assets.json_path}")
        if cve_assets.diff_path:
            print(f"Diff文件: {cve_assets.diff_path}")
        
        # 收集情报数据
        print("正在收集漏洞情报...")
        intel_bundle = collect_intel(
            case_paths,
            cve_assets,
            use_cache=not refresh_intel
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
            await run_java_analysis(case_paths, cve_assets, intel_bundle)
        elif language == "python":
            await run_python_analysis(case_paths, cve_assets, intel_bundle)
        else:
            print(f"警告: 不支持的语言 '{language}'，使用默认分析器")
            await run_default_analysis(case_paths, cve_assets, intel_bundle)
            
    except Exception as e:
        print(f"案例分析错误: {e}")


def detect_language(case_paths: CasePaths) -> str:
    """检测案例使用的编程语言。"""
    # 检查数据库目录中的语言子目录
    if (case_paths.db / "java").exists():
        return "java"
    elif (case_paths.db / "python").exists():
        return "python"
    
    # 检查源码目录中的文件类型
    java_files = list(case_paths.source_code.rglob("*.java"))
    python_files = list(case_paths.source_code.rglob("*.py"))
    
    if java_files:
        return "java"
    elif python_files:
        return "python"
    
    # 无法检测到语言时抛出异常
    raise ValueError("无法检测到编程语言。请确保数据库目录包含有效的语言子目录或源码目录包含可识别的源文件。")


async def run_java_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle) -> None:
    """运行Java分析工作流。"""
    print("使用Java分析器...")
    
    # 使用案例中的路径
    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)
    db_path = str(case_paths.db)
    
    await run_multi_agent_analysis(json_path, diff_path, source_root, db_path, intel_bundle)


async def run_python_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle) -> None:
    """运行Python分析工作流。"""
    print("使用Python分析器...")
    
    # 导入Python分析器
    from agents.python_sink_path_agent import PythonPathAnalysisAgent
    from agents.python_source_analysis_agent import PythonSourceAnalysisAgent
    from Analyze_Python import write_python_analysis_output
    
    try:
        analyzer = MultiAgentAnalyzer()
        await analyzer.initialize()
        
        cve_agent = CVEAnalysisAgent(analyzer)
        sink_agent = PythonPathAnalysisAgent(analyzer, str(case_paths.source_code))
        source_agent = PythonSourceAnalysisAgent(analyzer, str(case_paths.source_code))
        
        print("=== CVE Analysis ===")
        # 将IntelBundle转换为intel_prompt字符串
        intel_prompt = intel_bundle.prompt_block() if intel_bundle else None
        cve_result = await cve_agent.analyze_cve(cve_assets.json_path, intel_prompt=intel_prompt)
        if not cve_result.success:
            print(f"CVE analysis failed: {cve_result.error}")
        else:
            print(cve_result.content)
        print()
        
        print("=== Python Sink Path Analysis ===")
        sink_result = await sink_agent.analyze_python_paths(cve_result.content if cve_result.success else "")
        if not sink_result.success:
            print(f"Python sink analysis failed: {sink_result.error}")
        else:
            print(sink_result.content)
        print()
        
        print("=== Python Source Analysis ===")
        source_result = await source_agent.analyze_python_sources(cve_result.content if cve_result.success else "")
        if not source_result.success:
            print(f"Python source analysis failed: {source_result.error}")
        else:
            print(source_result.content)
        
        # 使用Python专用的输出函数
        output_path = case_paths.root / "output_python.md"
        write_python_analysis_output(cve_result, sink_result, source_result, output_path)
        
    except Exception as e:
        print(f"Python analysis error: {e}")


async def run_default_analysis(case_paths: CasePaths, cve_assets: CveAssets, intel_bundle: IntelBundle) -> None:
    """运行默认分析工作流。"""
    print("使用默认分析器...")
    
    json_path = str(cve_assets.json_path)
    diff_path = str(cve_assets.diff_path) if cve_assets.diff_path else ""
    source_root = str(case_paths.source_code)
    
    await run_multi_agent_analysis(json_path, diff_path, source_root, intel_bundle)


async def main() -> None:
    """主函数，支持命令行参数。"""
    args = parse_arguments()
    
    if not args.case:
        print("错误: 必须提供 --case 参数指定要分析的案例ID")
        print("用法: python Analyze.py --case <CASE_ID> [--cve <CVE_ID>] [--refresh-intel]")
        return
    
    # 使用案例模式
    await run_case_analysis(args.case, args.cve, args.refresh_intel)


if __name__ == "__main__":
    asyncio.run(main())
