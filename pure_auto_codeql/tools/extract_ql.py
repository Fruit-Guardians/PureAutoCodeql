import re

def extract_predicate_body_and_params(code: str, name: str) -> tuple[str, list[str]]:
    """提取指定名称的谓词体及其参数名"""
    # 匹配 predicate name ( params ) {
    pattern = r"predicate\s+" + name + r"\s*\(([^)]*)\)\s*\{"
    match = re.search(pattern, code, re.DOTALL)
    if not match:
        return "", []
    
    param_str = match.group(1)
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
        body = code[start:i-1].strip()
        params = []
        if param_str.strip():
            # 简单按逗号分割参数
            for p in param_str.split(','):
                parts = p.strip().split()
                if parts:
                    params.append(parts[-1])
        return body, params
    return "", []

def extract_imports(code: str) -> str:
    """提取import语句"""
    imports = []
    for line in code.splitlines():
        line = line.strip()
        if line.startswith("import ") and not line.startswith("import java") and not line.startswith("import python") and not line.startswith("import semmle"):
            # 排除基础库import，避免重复（虽然重复通常也没事，但保持整洁）
            # 或者简单粗暴全部提取，由CodeQL去重? CodeQL不允许完全重复的import吗？通常允许。
            # 考虑到Get_Breakpoint已经加了基础import，这里只加额外的。
            # 但很难判断哪些是额外的。不如全部提取，然后在Get_Breakpoint里拼接时容忍重复。
            # 为了稳妥，这里提取所有import，后面去重。
            imports.append(line)
    # 还是简单全部提取吧，Get_Breakpoint里的基础import是必须的，额外的可能是第三方的
    # 重新实现：只提取非基础的？
    # 用户可能import了一些特定的库。
    all_imports = [line.strip() for line in code.splitlines() if line.strip().startswith("import ")]
    return "\n".join(all_imports)

def extract_ql_predicate(code: str) -> dict:
    """从CodeQL代码中提取关键谓词"""
    res = {}
    
    # 提取imports
    res["imports"] = extract_imports(code)

    # 提取isSource
    source_body, source_params = extract_predicate_body_and_params(code, "isSource")
    if source_body:
        # 替换参数名，统一使用this
        if source_params:
            p = source_params[0]
            if p != "this":
                source_body = re.sub(r'\b' + re.escape(p) + r'\b', 'this', source_body)
        
        # 兼容旧逻辑
        source_body = re.sub(r'\bsource\b', 'this', source_body)
        source_body = re.sub(r'\bsrc\b', 'this', source_body)
        res["isSource"] = source_body
        
    # 提取isSink
    sink_body, sink_params = extract_predicate_body_and_params(code, "isSink")
    if sink_body:
        # 替换参数名，统一使用this
        if sink_params:
            p = sink_params[0]
            if p != "this":
                sink_body = re.sub(r'\b' + re.escape(p) + r'\b', 'this', sink_body)

        # 兼容旧逻辑
        sink_body = re.sub(r'\bsink\b', 'this', sink_body)
        res["isSink"] = sink_body
        
    # 提取isAdditionalFlowStep
    step_body, step_params = extract_predicate_body_and_params(code, "isAdditionalFlowStep")
    if step_body:
        # 规范化参数为 src, dst
        if len(step_params) >= 2:
            p1 = step_params[0]
            p2 = step_params[1]
            if p1 != "src":
                step_body = re.sub(r'\b' + re.escape(p1) + r'\b', 'src', step_body)
            if p2 != "dst":
                step_body = re.sub(r'\b' + re.escape(p2) + r'\b', 'dst', step_body)
        res["isAdditionalFlowStep"] = step_body
    else:
        res["isAdditionalFlowStep"] = "none()"

    # 提取isBarrier/isSanitizer
    barrier_body, barrier_params = extract_predicate_body_and_params(code, "isBarrier")
    if not barrier_body:
        barrier_body, barrier_params = extract_predicate_body_and_params(code, "isSanitizer")
    
    if barrier_body:
        # 规范化参数为 node
        if barrier_params:
            p = barrier_params[0]
            if p != "node":
                barrier_body = re.sub(r'\b' + re.escape(p) + r'\b', 'node', barrier_body)
        res["isBarrier"] = barrier_body
    else:
        res["isBarrier"] = "none()"

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

    # 添加额外的import
    extra_imports = predicate.get("imports", "")
    if extra_imports:
        imports += "\n" + extra_imports

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
  predicate isBarrier(DataFlow::Node node) { """+predicate.get("isBarrier", "none()")+""" }
}
module F = TaintTracking::Global<ForwardCfg>;

/** “后向”：ANY -> fixed sink（仍然正向求解，只是把 sink 当成目标） */
module BackwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { any() }
  predicate isSink(DataFlow::Node sink)  { sink instanceof FixedSinkNode }
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { """+predicate["isAdditionalFlowStep"]+""" }
  predicate isBarrier(DataFlow::Node node) { """+predicate.get("isBarrier", "none()")+""" }
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
