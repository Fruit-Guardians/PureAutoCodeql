import re
import logging
from typing import Dict, List, Optional, Tuple

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def extract_imports(code: str) -> List[str]:
    """从CodeQL代码中提取所有导入语句"""
    import_pattern = r'import\s+[^\n]+'
    imports = re.findall(import_pattern, code)
    return imports

def extract_predicate_with_name(code: str, predicate_name: str) -> Optional[str]:
    """
    从CodeQL代码中提取指定名称的谓词定义
    
    Args:
        code: CodeQL代码
        predicate_name: 谓词名称
        
    Returns:
        谓词体内容，如果未找到则返回None
    """
    # Python的re模块不支持递归正则表达式，所以我们使用更简单但有效的方法
    # 这个正则表达式可以处理：
    # 1. 谓词前可能有修饰符（private, cached等）
    # 2. 参数列表可能跨多行
    # 3. 谓词体可能包含嵌套的大括号（有限支持）
    
    # 使用一个更简单的正则表达式，匹配谓词定义的开始
    predicate_start_pattern = rf"(?:private\s+|cached\s+|override\s+)*predicate\s+{predicate_name}\s*\([^)]*\)\s*\{{"
    
    # 找到所有谓词开始的位置
    start_matches = list(re.finditer(predicate_start_pattern, code, re.DOTALL))
    
    if not start_matches:
        logger.warning(f"未找到谓词: {predicate_name}")
        return None
    
    # 使用第一个匹配
    start_match = start_matches[0]
    start_pos = start_match.end()
    
    # 从开始位置查找匹配的大括号
    brace_count = 1
    pos = start_pos
    while pos < len(code) and brace_count > 0:
        if code[pos] == '{':
            brace_count += 1
        elif code[pos] == '}':
            brace_count -= 1
        pos += 1
    
    if brace_count != 0:
        logger.warning(f"谓词 {predicate_name} 的大括号不匹配")
        return None
    
    # 提取谓词体
    predicate_body = code[start_pos:pos-1].strip()
    return predicate_body

def extract_class_definition(code: str, class_name: str) -> Optional[str]:
    """
    从CodeQL代码中提取指定名称的类定义
    
    Args:
        code: CodeQL代码
        class_name: 类名
        
    Returns:
        类定义内容，如果未找到则返回None
    """
    # 使用一个更简单的方法，匹配类定义的开始
    class_start_pattern = rf"(?:abstract\s+|final\s+)*class\s+{class_name}(?:\s+extends\s+[^\s{{]+)?(?:\s+implements\s+[^\s{{]+)?\s*\{{"
    
    # 找到所有类开始的位置
    start_matches = list(re.finditer(class_start_pattern, code, re.DOTALL))
    
    if not start_matches:
        logger.warning(f"未找到类: {class_name}")
        return None
    
    # 使用第一个匹配
    start_match = start_matches[0]
    start_pos = start_match.end()
    
    # 从开始位置查找匹配的大括号
    brace_count = 1
    pos = start_pos
    while pos < len(code) and brace_count > 0:
        if code[pos] == '{':
            brace_count += 1
        elif code[pos] == '}':
            brace_count -= 1
        pos += 1
    
    if brace_count != 0:
        logger.warning(f"类 {class_name} 的大括号不匹配")
        return None
    
    # 提取类体
    class_body = code[start_pos:pos-1].strip()
    return class_body

def extract_ql_predicate(code: str) -> Dict[str, str]:
    """
    从CodeQL代码中提取指定的谓词定义
    
    Args:
        code: CodeQL代码
        
    Returns:
        包含提取的谓词的字典
    """
    result = {}
    
    # 提取isSink谓词
    is_sink = extract_predicate_with_name(code, "isSink")
    if is_sink:
        # 使用更精确的正则表达式，只替换作为独立变量使用的sink
        is_sink = re.sub(r'\bsink\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', is_sink)
        result["isSink"] = is_sink
    
    # 提取isSource谓词
    is_source = extract_predicate_with_name(code, "isSource")
    if is_source:
        # 替换source变量
        is_source = re.sub(r'\bsource\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', is_source)
        # 替换src变量
        is_source = re.sub(r'\bsrc\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', is_source)
        result["isSource"] = is_source
    
    # 提取isAdditionalFlowStep谓词
    is_additional_flow_step = extract_predicate_with_name(code, "isAdditionalFlowStep")
    if is_additional_flow_step:
        result["isAdditionalFlowStep"] = is_additional_flow_step
    
    # 提取导入语句
    imports = extract_imports(code)
    if imports:
        result["imports"] = "\n".join(imports)
    
    return result

def Get_Breakpoint(predicate: Dict[str, str], language: str = "java") -> str:
    """
    组装断点查询语句
    
    Args:
        predicate: 包含谓词定义的字典
        language: 编程语言，默认为java
        
    Returns:
        组装好的断点查询语句
    """
    # 根据语言选择适当的导入语句
    if language.lower() == "java":
        dataflow_import = "import semmle.code.java.dataflow.DataFlow"
        tainttracking_import = "import semmle.code.java.dataflow.TaintTracking"
    elif language.lower() == "cpp":
        dataflow_import = "import cpp"
        tainttracking_import = "import semmle.code.cpp.dataflow.DataFlow"
    elif language.lower() == "python":
        dataflow_import = "import python"
        tainttracking_import = "import semmle.code.python.dataflow.TaintTracking"
    else:
        # 默认使用java
        dataflow_import = "import semmle.code.java.dataflow.DataFlow"
        tainttracking_import = "import semmle.code.java.dataflow.TaintTracking"
    
    # 使用自定义导入语句（如果提供）
    custom_imports = predicate.get("imports", "")
    if custom_imports:
        imports_section = custom_imports
    else:
        imports_section = f"import {language}\n{dataflow_import}\n{tainttracking_import}"
    
    # 检查必需的谓词是否存在
    if "isSource" not in predicate:
        logger.error("缺少必需的isSource谓词")
        raise ValueError("缺少必需的isSource谓词")
    
    if "isSink" not in predicate:
        logger.error("缺少必需的isSink谓词")
        raise ValueError("缺少必需的isSink谓词")
    
    # 获取isAdditionalFlowStep，如果不存在则使用空字符串
    is_additional_flow_step = predicate.get("isAdditionalFlowStep", "")
    
    template = f'''{imports_section}

/** ====== 与原查询相同的 Source/Sink 定义 ====== */
class FixedSourceNode extends DataFlow::Node {{
  FixedSourceNode() {{{{predicate["isSource"]}}}}
}}

class FixedSinkNode extends DataFlow::Node {{
  FixedSinkNode() {{{{predicate["isSink"]}}}}
}}

/** 前向：fixed source -> ANY（如需补边在这里加） */
module ForwardCfg implements DataFlow::ConfigSig {{
  predicate isSource(DataFlow::Node src) {{ src instanceof FixedSourceNode }}
  predicate isSink(DataFlow::Node sink)  {{ any() }}
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {{ {is_additional_flow_step} }}
}}
module F = TaintTracking::Global<ForwardCfg>;

/** "后向"：ANY -> fixed sink（仍然正向求解，只是把 sink 当成目标） */
module BackwardCfg implements DataFlow::ConfigSig {{
  predicate isSource(DataFlow::Node src) {{ any() }}
  predicate isSink(DataFlow::Node sink)  {{ sink instanceof FixedSinkNode }}
}}
module B = TaintTracking::Global<BackwardCfg>;

/** 从固定 source 可达 */
predicate forwardReachable(DataFlow::Node n) {{
  exists(DataFlow::Node s, DataFlow::Node t | F::flow(s, t) and t = n)
}}

/** 位于"能到固定 sink 的那些文件"中 */
predicate backwardReachable(DataFlow::Node n) {{
  exists(DataFlow::Node s, DataFlow::Node t |
    B::flow(s, t) and s.getLocation().getFile() = n.getLocation().getFile()
  )
}}

/** 候选断流点：能从 source 到达，但自身无法再到 sink，且在后向可达文件里 */
predicate isCut(DataFlow::Node n) {{
  forwardReachable(n) and
  backwardReachable(n) and
  not exists(DataFlow::Node t | B::flow(n, t))
}}

/** 最后节点：在候选断流点集合中，不存在"更靠后的候选点"可由它前向到达 */
predicate isLastCut(DataFlow::Node n) {{
  isCut(n) and
  not exists(DataFlow::Node m |
    isCut(m) and m != n and F::flow(n, m)
  )
}}

/** 结果：每条路径的最后一个节点（可能有多条分支，各回一个） */
from DataFlow::Node n
where isLastCut(n)
select n.getLocation(), "最后节点（路径在此处中断）"
'''
    return template

def extract_and_generate_breakpoint(code: str, language: str = "java") -> Tuple[Dict[str, str], str]:
    """
    从CodeQL代码中提取谓词并生成断点查询
    
    Args:
        code: CodeQL代码
        language: 编程语言，默认为java
        
    Returns:
        元组：(提取的谓词字典, 生成的断点查询)
    """
    try:
        predicates = extract_ql_predicate(code)
        breakpoint_query = Get_Breakpoint(predicates, language)
        return predicates, breakpoint_query
    except Exception as e:
        logger.error(f"提取谓词并生成断点查询失败: {e}")
        raise