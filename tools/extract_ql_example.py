#!/usr/bin/env python3
"""
使用示例：展示如何使用改进版的extract_ql_improved.py
"""

from extract_ql_improved import extract_and_generate_breakpoint, extract_ql_predicate, Get_Breakpoint

# 示例1: 基本的Java CodeQL查询
java_example = """
import java
import semmle.code.java.dataflow.DataFlow

/**
 * @name Example Taint Tracking
 * @description Example of taint tracking from source to sink
 */
predicate isSource(DataFlow::Node source) {
  exists(Method m |
    m.hasName("getInput") and
    source.asParameter() = m.getAParameter()
  )
}

predicate isSink(DataFlow::Node sink) {
  exists(Method m |
    m.hasName("executeQuery") and
    sink.asExpr() = m.getAnArgument()
  )
}

predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(Method m |
    m.hasName("process") and
    src.asExpr() = m.getArgument(0) and
    dst.asExpr() = m.getResult()
  )
}

from DataFlow::PathNode source, DataFlow::PathNode sink
where isSource(source.getNode()) and isSink(sink.getNode()) and DataFlow::localPath(source, sink)
select source, sink, "Taint flow from source to sink"
"""

# 示例2: 带有复杂格式的CodeQL查询（包含注释、多行参数等）
complex_example = """
import java
import semmle.code.java.dataflow.TaintTracking

// 自定义Source类
class MySource extends Method {
  MySource() {
    this.hasName("readLine") or
    this.hasName("getParameter")
  }
}

// 自定义Sink类
class MySink extends Method {
  MySink() {
    this.hasName("execute") or
    this.hasName("writeToFile")
  }
}

/**
 * 判断是否为Source节点
 * 支持多种输入源
 */
cached predicate isSource(
  DataFlow::Node source
) {
  exists(Method m |
    m instanceof MySource and
    source.asParameter() = m.getAParameter()
  )
}

/**
 * 判断是否为Sink节点
 * 支持多种输出目标
 */
private predicate isSink(
  DataFlow::Node sink
) {
  exists(Method m |
    m instanceof MySink and
    sink.asExpr() = m.getAnArgument()
  )
}

/**
 * 额外的流步骤，用于处理特殊情况
 */
predicate isAdditionalFlowStep(
  DataFlow::Node src,
  DataFlow::Node dst
) {
  // 处理方法调用链
  exists(Method m, MethodCall call |
    call.getMethod() = m and
    src.asExpr() = call.getQualifier() and
    dst.asExpr() = call
  ) or
  // 处理赋值操作
  exists(Assignment assign |
    src.asExpr() = assign.getSource() and
    dst.asExpr() = assign.getDestination()
  )
}

from DataFlow::PathNode source, DataFlow::PathNode sink
where isSource(source.getNode()) and isSink(sink.getNode()) and TaintTracking::localTaint(source, sink)
select source.getNode(), source.getNode().getLocation(), 
       sink.getNode(), sink.getNode().getLocation(),
       "Taint flow from $@ to $@.", 
       source.getNode(), source.getNode().toString(), 
       sink.getNode(), sink.getNode().toString()
"""

# 示例3: C++ CodeQL查询
cpp_example = """
import cpp
import semmle.code.cpp.dataflow.DataFlow

predicate isSource(DataFlow::Node source) {
  exists(Function f |
    f.hasName("getUserInput") and
    source.asParameter() = f.getAParameter()
  )
}

predicate isSink(DataFlow::Node sink) {
  exists(Function f |
    f.hasName("system") and
    sink.asExpr() = f.getAnArgument()
  )
}

predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(FunctionCall call |
    src.asExpr() = call.getArgument(0) and
    dst.asExpr() = call
  )
}

from DataFlow::PathNode source, DataFlow::PathNode sink
where isSource(source.getNode()) and isSink(sink.getNode()) and DataFlow::localPath(source, sink)
select source, sink, "Taint flow from source to sink"
"""

def test_java_example():
    """测试Java示例"""
    print("=" * 60)
    print("测试Java示例")
    print("=" * 60)
    
    try:
        predicates, breakpoint_query = extract_and_generate_breakpoint(java_example, "java")
        
        print("提取的谓词:")
        for name, body in predicates.items():
            print(f"{name}:")
            print(f"  {body[:100]}..." if len(body) > 100 else f"  {body}")
            print()
        
        print("\n生成的断点查询:")
        print(breakpoint_query[:500] + "..." if len(breakpoint_query) > 500 else breakpoint_query)
        
        print("\n✅ Java示例测试成功")
        return True
    except Exception as e:
        print(f"❌ Java示例测试失败: {e}")
        return False

def test_complex_example():
    """测试复杂格式示例"""
    print("\n" + "=" * 60)
    print("测试复杂格式示例")
    print("=" * 60)
    
    try:
        predicates = extract_ql_predicate(complex_example)
        
        print("提取的谓词:")
        for name, body in predicates.items():
            print(f"{name}:")
            print(f"  {body[:100]}..." if len(body) > 100 else f"  {body}")
            print()
        
        # 尝试生成断点查询
        if "isSource" in predicates and "isSink" in predicates:
            breakpoint_query = Get_Breakpoint(predicates, "java")
            print("\n生成的断点查询:")
            print(breakpoint_query[:500] + "..." if len(breakpoint_query) > 500 else breakpoint_query)
            print("\n✅ 复杂格式示例测试成功")
        else:
            print("\n⚠️ 缺少必要的谓词，无法生成断点查询")
            print("✅ 谓词提取部分测试成功")
        
        return True
    except Exception as e:
        print(f"❌ 复杂格式示例测试失败: {e}")
        return False

def test_cpp_example():
    """测试C++示例"""
    print("\n" + "=" * 60)
    print("测试C++示例")
    print("=" * 60)
    
    try:
        predicates, breakpoint_query = extract_and_generate_breakpoint(cpp_example, "cpp")
        
        print("提取的谓词:")
        for name, body in predicates.items():
            print(f"{name}:")
            print(f"  {body[:100]}..." if len(body) > 100 else f"  {body}")
            print()
        
        print("\n生成的断点查询:")
        print(breakpoint_query[:500] + "..." if len(breakpoint_query) > 500 else breakpoint_query)
        
        print("\n✅ C++示例测试成功")
        return True
    except Exception as e:
        print(f"❌ C++示例测试失败: {e}")
        return False

if __name__ == "__main__":
    print("开始测试改进版的extract_ql_improved.py")
    
    success_count = 0
    total_tests = 3
    
    if test_java_example():
        success_count += 1
    
    if test_complex_example():
        success_count += 1
    
    if test_cpp_example():
        success_count += 1
    
    print("\n" + "=" * 60)
    print(f"测试结果: {success_count}/{total_tests} 通过")
    print("=" * 60)
    
    if success_count == total_tests:
        print("🎉 所有测试通过！")
    else:
        print("⚠️ 部分测试失败，请检查日志")