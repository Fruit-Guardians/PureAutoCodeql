import re

def extract_predicate_with_name(code: str, name: str) -> str:
    """提取指定名称的谓词体"""
    pattern = r"predicate\s+" + name + r"\s*\([^)]*\)\s*\{"
    match = re.search(pattern, code)
    if not match:
        return ""
    
    start = match.end()
    count = 1
    i = start
    while i < len(code) and count > 0:
        if code[i] == '{':
            count += 1
        elif code[i] == '}':
            count -= 1
        i += 1
    
    if count == 0:
        return code[start:i-1].strip()
    return ""

def extract_ql_predicate(code: str) -> dict:
    """从CodeQL代码中提取关键谓词"""
    res = {}
    
    # 提取isSource
    source_body = extract_predicate_with_name(code, "isSource")
    if source_body:
        # 替换参数名，统一使用this
        # 通常参数名为 source 或 src
        source_body = re.sub(r'\bsource\b', 'this', source_body)
        source_body = re.sub(r'\bsrc\b', 'this', source_body)
        res["isSource"] = source_body
        
    # 提取isSink
    sink_body = extract_predicate_with_name(code, "isSink")
    if sink_body:
        # 替换参数名，统一使用this
        # 通常参数名为 sink
        sink_body = re.sub(r'\bsink\b', 'this', sink_body)
        res["isSink"] = sink_body
        
    # 提取isAdditionalFlowStep
    step_body = extract_predicate_with_name(code, "isAdditionalFlowStep")
    if step_body:
        res["isAdditionalFlowStep"] = step_body
    else:
        res["isAdditionalFlowStep"] = "none()"


    return res

def Get_Breakpoint(predicate: dict, language: str = "java") -> str:
    """组装断点查询语句"""
    lang = language.lower()
    
    if lang == "python":
        imports = """import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources
"""
    elif lang == "cpp":
        imports = """import cpp
import semmle.code.cpp.dataflow.DataFlow
import semmle.code.cpp.dataflow.TaintTracking
"""
    elif lang == "go":
        imports = """import go
import semmle.go.dataflow.DataFlow
import semmle.go.dataflow.TaintTracking
"""
    elif lang == "javascript" or lang == "js" or lang == "typescript" or lang == "ts":
        imports = """import javascript
import semmle.javascript.dataflow.DataFlow
import semmle.javascript.dataflow.TaintTracking
"""
    elif lang == "csharp" or lang == "cs":
        imports = """import csharp
import semmle.code.csharp.dataflow.DataFlow
import semmle.code.csharp.dataflow.TaintTracking
"""
    elif lang == "ruby" or lang == "rb":
        imports = """import ruby
import codeql.ruby.DataFlow
import codeql.ruby.TaintTracking
"""
    else: # java or default
        imports = """import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking
"""

    template = imports + """

/** ====== 与原查询相同的 Source/Sink 定义 ====== */
class FixedSourceNode extends DataFlow::Node {
  FixedSourceNode() {"""+predicate["isSource"]+"""}
}

class FixedSinkNode extends DataFlow::Node {
  FixedSinkNode() {"""+predicate["isSink"]+"""}
}

/** 前向：fixed source -> ANY（如需补边在这里加） */
module ForwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { src instanceof FixedSourceNode }
  predicate isSink(DataFlow::Node sink)  { any() }
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { """+predicate["isAdditionalFlowStep"]+""" }
}
module F = TaintTracking::Global<ForwardCfg>;

/** “后向”：ANY -> fixed sink（仍然正向求解，只是把 sink 当成目标） */
module BackwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { any() }
  predicate isSink(DataFlow::Node sink)  { sink instanceof FixedSinkNode }
}
module B = TaintTracking::Global<BackwardCfg>;

/** 从固定 source 可达 */
predicate forwardReachable(DataFlow::Node n) {
  exists(DataFlow::Node s, DataFlow::Node t | F::flow(s, t) and t = n)
}

/** 位于“能到固定 sink 的那些文件”中 */
predicate backwardReachable(DataFlow::Node n) {
  exists(DataFlow::Node s, DataFlow::Node t |
    B::flow(s, t) and s.getLocation().getFile() = n.getLocation().getFile()
  )
}

/** 候选断流点：能从 source 到达，但自身无法再到 sink，且在后向可达文件里 */
predicate isCut(DataFlow::Node n) {
  forwardReachable(n) and
  backwardReachable(n) and
  not exists(DataFlow::Node t | B::flow(n, t))
}

/** 最后节点：在候选断流点集合中，不存在“更靠后的候选点”可由它前向到达 */
predicate isLastCut(DataFlow::Node n) {
  isCut(n) and
  not exists(DataFlow::Node m |
    isCut(m) and m != n and F::flow(n, m)
  )
}

/** 结果：每条路径的最后一个节点（可能有多条分支，各回一个） */
from DataFlow::Node n
where isLastCut(n)
select n.getLocation(), "最后节点（路径在此处中断）"
"""
    return template
