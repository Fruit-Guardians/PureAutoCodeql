/**
 * @kind path-problem
 * @name <简明英文名称>
 * @description <详细描述>
 * @id python/<项目>-<漏洞类型>
 * @tags security, taint, <相关标签>
 * @problem.severity <error|warning|recommendation>
 * @precision <high|medium|low>
 */

 import python
 import semmle.python.dataflow.new.DataFlow
 import semmle.python.dataflow.new.TaintTracking
 import semmle.python.dataflow.new.RemoteFlowSources
 
 /** ========== Helper 谓词 ========== */
 <HELPER-PREDICATES>
 
 /** ========== 数据流配置 ========== */
 module VulnConfig implements DataFlow::ConfigSig {
   predicate isSource(DataFlow::Node src) {
     /* 定义 source */
   }
 
   predicate isSink(DataFlow::Node sink) {
     /* 定义 sink */
   }
 
   predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
     /* 可选：额外流步 */
     none()  // 如果不需要，使用 none()
   }
 
   predicate isSanitizer(DataFlow::Node node) {
     /* 可选：净化器 */
     none()  // 如果不需要，使用 none()
   }
 }
 
 module Flow = TaintTracking::Global<VulnConfig>;
 import Flow::PathGraph
 
 from Flow::PathNode src, Flow::PathNode sink
 where Flow::flowPath(src, sink)
 select sink.getNode(), src, sink,
   "<诊断消息>",
   src, "source", sink, "sink"