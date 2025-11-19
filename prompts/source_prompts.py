"""
Source分析相关的提示词模板。
"""


def get_language_specific_instructions(language: str) -> str:
    """获取特定语言的指令说明。"""
    instructions = {
        "java": "关注Servlet API、网络请求处理、文件读取等入口点。默认包含CodeQL官方的所有远程输入点Source（RemoteFlowSource），6. 从Sink点函数开始向前反向找被调用点，找到可能的Source点",
        "python": "关注Web框架、文件操作、网络请求等输入源，6. 从Sink点函数开始向前反向找被调用点，找到可能的Source点",
        "cpp": "关注网络套接字、文件读取、环境变量等输入函数，6. 在查找Source点的过程中顺便给出需要ql生成isAdditionalFlowStep的步骤，比如加减法乘法运算导致的溢出,或者其他通过偏移等操作进行的流传输，需要给出对应的isAdditionalFlowStep点"
    }
    return instructions.get(language, "关注可能的输入源和用户控制数据入口点")


def get_language_specific_focus(language: str) -> str:
    """获取特定语言的分析重点。"""
    focus_areas = {
        "java": "Servlet API调用、网络请求处理、文件读取操作。默认包含所有RemoteFlowSource（如HttpServletRequest、Spring RequestMapping参数、JAX-RS参数等）",
        "python": "Web框架请求处理、文件操作、网络API调用",
        "cpp": "网络套接字函数、文件读取API、环境变量读取，在查找Source点的过程中顺便给出需要ql生成isAdditionalFlowStep的步骤，比如加减法乘法运算导致的溢出,或者其他通过偏移等操作进行的流传输，需要给出对应的isAdditionalFlowStep点"
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
- **LSP工具（推荐优先使用）**：
注意！ 指定文档位置的 `filePath` 必须使用绝对路径（例如：`/home/orxiain/Projects/Github/Pure2/projects/CVE-2022-26125/source_code/...`）。
  * `definition`: 查找符号的定义位置
    - 参数：`symbolName` - 要查找的符号名称
    - 返回：符号的定义位置和完整实现代码
    - 例如：`definition(symbolName="handle_request")`
  * `references`: 查找符号的所有引用位置（**核心工具：追踪数据流**）
    - 参数：`symbolName` - 要查找引用的符号名称
    - 返回：所有引用该符号的文件和位置列表
    - 例如：`references(symbolName="process_input")`
  * `hover`: 获取指定位置的类型和文档信息
    - 参数：`filePath`, `line`, `column`
    - 用于理解参数类型、函数签名等
- **MCP Tree-sitter 代码分析服务器**：
  * 使用前必须通过 `register_project_tool` 用 **绝对路径** 注册项目根目录，例如：
    - `register_project_tool(path="/home/orxiain/Projects/Github/Pure2/projects/CVE-2022-26125/source_code", name="CVE-2022-26125", description="CVE-2022-26125 source_code project")`
  * 注册成功后，后续所有 tree-sitter 命令中的 `project` 参数必须使用注册时的名称（例如 `"CVE-2022-26125"`）：
    - `list_files(project="CVE-2022-26125", pattern="**/*.{file_extension}")`
    - `get_ast(project="CVE-2022-26125", path="相对路径/到/文件.{file_extension}", max_depth=5, include_text=True)`
    - `find_text(project="CVE-2022-26125", pattern="函数名或关键字", file_pattern="**/*.{file_extension}")`
    - `run_query(project="CVE-2022-26125", query="(function_definition ...)", file_path="相对路径/到/文件.{file_extension}", language="{language}")`
    - `get_symbols(project="CVE-2022-26125", file_path="相对路径/到/文件.{file_extension}")`
  * MCP Tree-sitter 的 `path`/`file_path` 参数是 **相对注册 project 根目录的相对路径**，但注册时的 `path` 必须是绝对路径。
- **基础工具**（LSP 或 tree-sitter 不可用时使用）：
  * server-filesystem：读取文件内容（注意：`read_text_file` 的 `path` 也推荐使用绝对路径以避免歧义）
  * ripgrep：快速搜索文件内容

行动指令（严格按照顺序执行）：
1. 理解不可信输入来源类型：网络/套接字、文件读取、管道/IPC、环境变量、标准输入、反序列化/二进制解析等。
2. **优先使用LSP工具分析**（推荐工作流）：
   - **第一步**：使用 `definition` 定位可疑的入口函数
     * 例如：`definition(symbolName="servlet_handler")`
   - **第二步**：使用 `references` 查看函数被哪里调用，理解数据流向
     * 例如：`references(symbolName="parse_request")`
   - **第三步**：使用 `hover` 理解输入参数的类型
     * 查看具体位置的变量类型信息
3. 结合路径列表，聚焦可能接收用户控制数据的函数或方法（{language_instructions}）。必要时，先使用 MCP Tree-sitter 的 `register_project_tool` 正确注册项目根目录，再通过 `list_files` / `find_text` / `get_ast` / `run_query` 等命令精确定位候选函数。
4. 给出每个候选的理由与置信度（high/medium/low）。
5. 使用LSP工具快速定位，避免盲目搜索

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
  "is_additional_flow_steps": [
    {{
      "type": "传播类型",
      "description": "传播描述（例如：加减法运算导致的溢出）",
      "example": "示例代码片段",
      "codeql_rule": "对应的CodeQL规则"
    }}
  ]
}}

规则：
- 使用 MCP Tree-sitter 工具前，必须确保已经通过 `register_project_tool` 使用 **绝对路径** 注册当前分析的源码目录（例如：`/home/orxiain/Projects/Github/Pure2/projects/CVE-2022-26125/source_code`）。
- 在调用 tree-sitter 相关命令时，`project` 名必须与注册时完全一致；命令中的 `path`/`file_path` 必须是相对于注册根目录的相对路径。
- 文件/内容搜索时，必须将文件后缀限制为项目语言：Java(.java)、Python(.py)、C/C++(.c .cc .cpp .cxx .h .hh .hpp .hxx)。
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
- **LSP工具（推荐优先使用）**：
  * `definition`: 查找符号的定义位置
    - 参数：`symbolName` - 要查找的符号名称
    - 返回：符号的定义位置和完整实现代码
    - 例如：`definition(symbolName="sink_function")`
  * `references`: 查找符号的所有引用位置（**核心工具：反向追踪调用链**）
    - 参数：`symbolName` - 要查找引用的符号名称
    - 返回：所有引用该符号的文件和位置列表
    - 例如：`references(symbolName="危险函数名")`
    - **这是反向追踪的关键工具**
  * `hover`: 获取指定位置的类型和文档信息
    - 参数：`filePath`, `line`, `column`
    - 用于理解参数来源和类型
- **基础工具**（LSP不可用时使用）：
  * server-filesystem：读取文件内容
  * ripgrep：快速搜索文件内容

行动指令（严格按照顺序执行）：
1. **首先仔细分析 Sink 点信息**，理解漏洞的终点位置和类型
2. **使用LSP工具反向追踪**（推荐工作流）：
   - **第一步**：使用 `references(symbolName="sink_function")` 查找所有调用Sink函数的位置
     * 这会返回所有调用该函数的文件和行号
   - **第二步**：对每个调用方，使用 `definition(symbolName="caller_function")` 了解调用方函数
     * 获取调用方函数的完整实现
   - **第三步**：对调用方函数继续使用 `references`，逐层向上追踪
     * 重复：`references(symbolName="caller_function")` → 找到更上层的调用方
   - **第四步**：使用 `hover` 理解参数的来源和类型
     * 查看特定位置的变量类型信息
   - **第五步**：重复这个过程，直到找到接收外部输入的入口点（HTTP路由、命令行参数、文件读取等）
3. 基于 Sink 点的位置和类型，反向追踪可能的 Source 点
4. 关注与 Sink 点相关的数据流路径，查找可能的输入源
5. 结合 CVE 分析结果，理解漏洞的触发条件和输入来源
6. 给出每个候选 Source 点的理由与置信度（high/medium/low）
7. **使用LSP工具精确追踪，避免盲目搜索**

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
  "is_additional_flow_steps": [
    {{
      "type": "传播类型",
      "description": "传播描述（例如：加减法运算导致的溢出）",
      "example": "示例代码片段",
      "codeql_rule": "对应的CodeQL规则"
    }}
  ]
}}


规则：
- 文件/内容搜索时，必须将文件后缀限制为项目语言：Java(.java)、Python(.py)、C/C++(.c .cc .cpp .cxx .h .hh .hpp .hxx)。
- 你拥有搜索功能，可以使用search功能进行快速的文件、函数搜索，注意函数搜索的正则要精确，如果正则精确不了则精确路径！
- 若没有发现候选函数，请输出：{{"candidates": []}}
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
