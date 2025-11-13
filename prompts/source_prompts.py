"""
Source分析相关的提示词模板。
"""


def get_language_specific_instructions(language: str) -> str:
    """获取特定语言的指令说明。"""
    instructions = {
        "java": "关注Servlet API、网络请求处理、文件读取等入口点",
        "python": "关注Web框架、文件操作、网络请求等输入源",
        "cpp": "关注网络套接字、文件读取、环境变量等输入函数"
    }
    return instructions.get(language, "关注可能的输入源和用户控制数据入口点")


def get_language_specific_focus(language: str) -> str:
    """获取特定语言的分析重点。"""
    focus_areas = {
        "java": "Servlet API调用、网络请求处理、文件读取操作",
        "python": "Web框架请求处理、文件操作、网络API调用",
        "cpp": "网络套接字函数、文件读取API、环境变量读取"
    }
    return focus_areas.get(language, "可能的输入源和用户控制数据入口点")


SOURCE_ANALYSIS_BASE_PROMPT = """你是一名资深的 CodeQL 安全研究员与 {language_upper} 代码审计专家，专注识别可能的 Source 候选函数。
任务目标：基于提供的 CVE 信息与 {language_upper} 文件路径，使用工具进行分析，仅产出“可能存在 Source 点的函数列表”。

输入信息：

1. CVE 分析结果：
{cve_analysis}

2. 相关 {language_upper} 文件路径：
{source_paths_str}

3. 工作目录：`{current_dir}`（所有路径均相对于该目录）。

可用工具：
- server-filesystem：读取文件内容
- ripgrep：快速搜索文件内容

行动指令（严格按照顺序执行）：
1. 理解不可信输入来源类型：网络/套接字、文件读取、管道/IPC、环境变量、标准输入、反序列化/二进制解析等。
2. 结合路径列表，聚焦可能接收用户控制数据的函数或方法（{language_instructions}）。
3. **特别关注可能需要 isAdditionalFlowStep 的情况**：
   - 字符串到对象的转换（如 Log4j2 中的字符串解析为对象）
   - 反序列化操作中数据格式转换
   - 反射调用中的参数传递
   - 动态代理或包装器模式中的数据流
   - 集合操作中的数据提取（如 Map.get() 后的类型转换）
   - 示例：Log4j2 漏洞中，字符串消息通过 MessagePatternConverter 转换为对象，此时 CodeQL 无法直接跟踪，需要设置 isAdditionalFlowStep 来建立数据流连接
4. 给出每个候选的理由与置信度（high/medium/low）。
5. 注意首先使用文件工具读取Sink点所在的文件，然后用ripgrep进行有需要的搜索

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "candidates": [
    {{
      "file_path": "相对路径（如 src/.../file.{file_extension}）",
      "function_name": "函数/方法名",
      "signature": "函数签名（含参数与类型）",
      "reason": "为何此处可能是 Source（用户控制输入、解析入口等）",
      "confidence": "high|medium|low"
    }}
  ],
}}

规则：
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必要时可以使用 server-filesystem 读取文件内容进行补充验证**。
- 请确保输出为合法可解析的 JSON。
- 结果应与源码实际位置一致。
 - 使用 `server-filesystem` 的 `read_text_file` 时，不要同时设置 `head` 与 `tail`；如需同时查看文件顶部与底部，请分两次分别读取并标注来源。
"""


SOURCE_ANALYSIS_WITH_SINK_PROMPT = """你是一名资深的 CodeQL 安全研究员与 {language_upper} 代码审计专家，专注识别可能的 Source 候选函数。

任务目标：基于提供的 CVE 信息、Sink 点分析结果和 {language_upper} 文件路径，仅产出"可能存在 Source 点的函数列表"。

输入信息：

1. CVE 分析结果：
{cve_analysis}

2. Sink 点分析结果：
{sink_analysis}

3. 相关 {language_upper} 文件路径：
{source_paths_str}

4. 工作目录：`{current_dir}`（所有路径均相对于该目录）。

可用工具：
- server-filesystem：读取文件内容
- ripgrep：快速搜索文件内容

行动指令（严格按照顺序执行）：
1. **首先仔细分析 Sink 点信息**，理解漏洞的终点位置和类型
2. 基于 Sink 点的位置和类型，反向追踪可能的 Source 点
3. 关注与 Sink 点相关的数据流路径，查找可能的输入源
4. 结合 CVE 分析结果，理解漏洞的触发条件和输入来源
5. **特别关注可能需要 isAdditionalFlowStep 的情况**：
   - 字符串到对象的转换（如 Log4j2 中的字符串解析为对象）
   - 反序列化操作中数据格式转换
   - 反射调用中的参数传递
   - 动态代理或包装器模式中的数据流
   - 集合操作中的数据提取（如 Map.get() 后的类型转换）
   - 示例：Log4j2 漏洞中，字符串消息通过 MessagePatternConverter 转换为对象，此时 CodeQL 无法直接跟踪，需要设置 isAdditionalFlowStep 来建立数据流连接
6. 给出每个候选 Source 点的理由与置信度（high/medium/low）
7. 注意首先使用ripgrep进行快速搜索

输出要求（必须严格遵守）：
- 仅输出 JSON（不要输出除 JSON 以外的任何文字、Markdown 或代码块标记）。
- JSON 结构如下：
{{
  "cve": "",
  "sink_info": "基于此 Sink 点查找对应的 Source 点",
  "candidates": [
    {{
      "file_path": "相对路径（如 src/.../file.{file_extension}）",
      "function_name": "函数/方法名",
      "signature": "函数签名（含参数与类型）",
      "reason": "为何此处可能是 Source（基于 Sink 点反向追踪）",
      "confidence": "high|medium|low"
    }}
  ],
}}


规则：
- 你拥有搜索功能，可以使用search功能进行快速的文件、函数搜索，注意函数搜索的正则要精确，如果正则精确不了则精确路径！
- 若没有发现候选函数，请输出：{{"candidates": []}}
- **必须基于 Sink 点信息进行分析**，而不是独立查找 Source
- 必要时可以使用 server-filesystem 读取文件内容进行补充验证
- 请确保输出为合法可解析的 JSON
- 结果应与 Sink 点位置和漏洞类型一致
 - 使用 `server-filesystem` 的 `read_text_file` 时，不要同时设置 `head` 与 `tail`；如需同时查看文件顶部与底部，请分两次分别读取并标注来源。

"""


def build_source_analysis_prompt(
    language: str,
    cve_analysis: str,
    source_paths: list,
    current_dir: str,
    file_extension: str = "ext"
) -> str:
    """构建基础的source分析提示词。"""
    language_upper = language.upper()
    source_paths_str = "\n".join(source_paths)
    language_instructions = get_language_specific_instructions(language)

    return SOURCE_ANALYSIS_BASE_PROMPT.format(
        language_upper=language_upper,
        cve_analysis=cve_analysis,
        source_paths_str=source_paths_str,
        current_dir=current_dir,
        language_instructions=language_instructions,
        file_extension=file_extension
    )


def build_source_analysis_with_codeql_prompt(
    language: str,
    cve_analysis: str,
    source_paths: list,
    current_dir: str,
    codeql_query: str,
    query_results: str,
    file_extension: str = "ext"
) -> str:
    """构建包含CodeQL查询结果的source分析提示词。"""
    language_upper = language.upper()
    source_paths_str = "\n".join(source_paths)
    language_focus = get_language_specific_focus(language)

    return SOURCE_ANALYSIS_WITH_CODEQL_PROMPT.format(
        language_upper=language_upper,
        cve_analysis=cve_analysis,
        source_paths_str=source_paths_str,
        current_dir=current_dir,
        codeql_query=codeql_query,
        query_results=query_results,
        language_focus=language_focus,
        file_extension=file_extension
    )


def build_source_analysis_with_sink_prompt(
    language: str,
    cve_analysis: str,
    sink_analysis: str,
    source_paths: list,
    current_dir: str,
    file_extension: str = "ext"
) -> str:
    """构建基于sink分析结果的source分析提示词。"""
    language_upper = language.upper()
    source_paths_str = "\n".join(source_paths)

    return SOURCE_ANALYSIS_WITH_SINK_PROMPT.format(
        language_upper=language_upper,
        cve_analysis=cve_analysis,
        sink_analysis=sink_analysis,
        source_paths_str=source_paths_str,
        current_dir=current_dir,
        file_extension=file_extension
    )
