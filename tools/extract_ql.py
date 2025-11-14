import re

def extract_ql_predicate(code: str) -> dict:
    """从CodeQL代码中提取指定的谓词定义。"""
    sink_pattern = r"(?s)predicate\s+isSink\s*\([^)]*\)\s*\{(.*?)\}"
    source_pattern = r"(?s)predicate\s+isSource\s*\([^)]*\)\s*\{(.*?)\}"
    isadd_flow_step_pattern = r"(?s)predicate\s+isAdditionalFlowStep\s*\([^)]*\)\s*\{(.*?)\}"

    sink_result = re.search(sink_pattern, code)
    source_result = re.search(source_pattern, code)
    isadd_flow_step_result = re.search(isadd_flow_step_pattern, code)

    res = {}
    if sink_result:
        # 使用更精确的正则表达式，只替换作为独立变量使用的sink
        sink_body = sink_result.group(1)
        # 匹配以下模式并替换：
        # 1. sink.asExpr()
        # 2. sink.asParameter()
        # 3. sink.asVariable()
        # 4. sink.asExpr() = ...
        # 5. ... = sink.asExpr()
        # 6. sink instanceof ...
        # 7. 其他独立的sink变量使用
        sink_body = re.sub(r'\bsink\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', sink_body)
        res["isSink"] = sink_body
    if source_result:
        # 使用更精确的正则表达式，只替换作为独立变量使用的source和src
        source_body = source_result.group(1)
        # 替换source变量
        source_body = re.sub(r'\bsource\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', source_body)
        # 替换src变量
        source_body = re.sub(r'\bsrc\b(?=\s*\.|\s*=|\s+instanceof|\s*\))', 'this', source_body)
        res["isSource"] = source_body
    if isadd_flow_step_result:
        res["isAdditionalFlowStep"] = isadd_flow_step_result.group(1)

    return res

def Get_Breakpoint(predicate: dict) -> str:
    """组装断点查询语句"""
    template = '''import java
import semmle.code.java.dataflow.DataFlow
import semmle.code.java.dataflow.TaintTracking

/** ====== 与原查询相同的 Source/Sink 定义 ====== */
class FixedSourceNode extends DataFlow::Node {
  FixedSourceNode() {'''+predicate["isSource"]+'''}
}

class FixedSinkNode extends DataFlow::Node {
  FixedSinkNode() {'''+predicate["isSink"]+'''}
}

/** 前向：fixed source -> ANY（如需补边在这里加） */
module ForwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { src instanceof FixedSourceNode }
  predicate isSink(DataFlow::Node sink)  { any() }
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) { '''+predicate["isAdditionalFlowStep"]+''' }
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
'''
    return template