/** ====== 与上个查询相同的 Source/Sink 和补边 ====== */
class FixedSourceNode extends DataFlow::Node { FixedSourceNode() { <SINK> } }
class FixedSinkNode   extends DataFlow::Node { FixedSinkNode()   { <SOURCE> } }


/** 任意节点的简便定义（配合 Config 使用） */
predicate anyNode(DataFlow::Node n) { exists(DataFlow::Node m | m = n) }

/** 前向：fixed source -> ANY */
module ForwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { src instanceof FixedSourceNode }
  predicate isSink(DataFlow::Node sink)  { anyNode(sink) }
    
    
  /*通过所给的断流的信息不断补充，直到有source点可以通向SINK点*/
  predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {<ISADDITION>}
    
}
module F = TaintTracking::Global<ForwardCfg>;

/* “后向”：ANY -> fixed sink（仍然正向求解，只是把 sink 当成目标） */
module BackwardCfg implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) { anyNode(src) }
  predicate isSink(DataFlow::Node sink)  { sink instanceof FixedSinkNode }
}
module B = TaintTracking::Global<BackwardCfg>;

/** 前向可达：从固定 source 能到达的节点 n */
predicate forwardReachable(DataFlow::Node n) {
  exists(DataFlow::Node s, DataFlow::Node t |
    F::flow(s, t) and t = n
  )
}

/** “后向”可达：能流向固定 sink 的节点，把这些节点所在的文件的所有节点定义为可达，最后取交集可以找到source流到的断流处 n */
predicate backwardReachable(DataFlow::Node n) {
  exists(DataFlow::Node s, DataFlow::Node t |
    B::flow(s, t) and s.getLocation().getFile() = n.getLocation().getFile()
  )
}

/** 结果：位于某条 source→sink 路径上*/
from DataFlow::Node n
where forwardReachable(n) and backwardReachable(n) and inAssignOrInit(n)
select n.getLocation()