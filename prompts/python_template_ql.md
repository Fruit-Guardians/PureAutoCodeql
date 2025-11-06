# Python CodeQL 查询模板（基于真实成功案例优化版）

本模板基于 8 个真实成功案例提炼而成，包含经过实战验证的最佳实践。

---

## 一、固定骨架（严格遵守）

```ql
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
```

---

## 二、核心 Helper 谓词模式（真实案例验证）

### 1. **检查 callee 是全局名称**（最常用）
```ql
predicate calleeIsGlobalName(DataFlow::CallCfgNode call, string nm) {
  call.getFunction().asCfgNode().getNode() instanceof Name and
  call.getFunction().asCfgNode().getNode().(Name).getId() = nm
}
```
**用途**: 识别 `eval()`, `exec()`, `redirect()`, `open()` 等全局函数调用

**示例**:
```ql
calleeIsGlobalName(call, "eval")  // 匹配 eval(...)
calleeIsGlobalName(call, "open")  // 匹配 open(...)
```

### 2. **检查 callee 是属性访问**
```ql
predicate calleeIsAttr(DataFlow::CallCfgNode call, string attr) {
  call.getFunction() instanceof DataFlow::AttrRead and
  call.getFunction().(DataFlow::AttrRead).getAttributeName() = attr
}
```
**用途**: 识别 `obj.redirect()`, `df.eval()`, `fp.write()` 等方法调用

**示例**:
```ql
calleeIsAttr(call, "redirect")  // 匹配 obj.redirect(...)
calleeIsAttr(call, "write")     // 匹配 fp.write(...)
```

### 3. **限定文件作用域**（降噪必备）
```ql
predicate inTargetFile(DataFlow::Node n, string filename) {
  n.getLocation().getFile().getBaseName() = filename
}

// 或使用正则匹配路径
predicate inPathPattern(DataFlow::Node n, string pattern) {
  n.getLocation().getFile().getRelativePath().regexpMatch(pattern)
}
```
**用途**: 精确定位到特定文件，减少误报

**示例**:
```ql
inTargetFile(source, "cnl_blueprint.py")
inPathPattern(n, ".*/PIL/ImageMath\\.py$")
```

### 4. **限定函数作用域**
```ql
predicate inTargetFunction(DataFlow::Node n, string funcName) {
  n.getEnclosingCallable().getScope().getName() = funcName
}

predicate isAffectedFunction(Function f) {
  f.getName() in ["func1", "func2", "func3"]
}
```
**用途**: 只关注特定函数内的数据流

**示例**:
```ql
inTargetFunction(source, "addcrypted")
```

### 5. **检查 receiver 模块**（框架特定）
```ql
predicate attrReceiverLooksLikeModule(DataFlow::CallCfgNode call, string modName) {
  exists(DataFlow::Node recv |
    recv = call.getFunction().(DataFlow::AttrRead).getObject() and
    (
      recv instanceof DataFlow::ModuleVariableNode and
      recv.(DataFlow::ModuleVariableNode).getVariable().toString().matches("%" + modName + "%")
      or
      recv.asCfgNode().getNode() instanceof Name and
      recv.asCfgNode().getNode().(Name).getId() = modName
    )
  )
}
```
**用途**: 识别 Django/Flask/pandas 等框架的调用

**示例**:
```ql
attrReceiverLooksLikeModule(call, "django.shortcuts")
attrReceiverLooksLikeModule(call, "pandas")
```

---

## 三、常见 Sink 模式（复制即用）

### 1. **Eval/Exec 代码注入**
```ql
predicate isEvalSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      // 全局 eval/exec
      calleeIsGlobalName(call, "eval") or
      calleeIsGlobalName(call, "exec") or
      // pandas.eval / df.eval
      (calleeIsAttr(call, "eval") and
       attrReceiverLooksLikeModule(call, "pandas")) or
      // builtins.eval
      (calleeIsAttr(call, "eval") and
       attrReceiverLooksLikeModule(call, "builtins"))
    ) and
    sink = call.getArg(0)
  )
}
```

### 2. **文件操作（路径遍历/任意文件写入）**
```ql
predicate isFileWriteSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    // open() 的第一个参数（文件路径）
    calleeIsGlobalName(call, "open") and
    sink = call.getArg(0)
  )
  or
  exists(DataFlow::CallCfgNode call |
    // fp.write() 的 receiver
    calleeIsAttr(call, "write") and
    sink = call.getFunction().(DataFlow::AttrRead).getObject()
  )
}
```

### 3. **重定向（Open Redirect）**
```ql
predicate isRedirectSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsGlobalName(call, "redirect") or
      calleeIsGlobalName(call, "HttpResponseRedirect") or
      calleeIsGlobalName(call, "HttpResponsePermanentRedirect") or
      (calleeIsAttr(call, "redirect") and
       attrReceiverLooksLikeModule(call, "django"))
    ) and
    sink = call.getArg(0)
  )
}
```

### 4. **SQL 注入**
```ql
predicate isSQLSink(DataFlow::Node sink) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsAttr(call, "execute") or
      calleeIsAttr(call, "execute_query_") or
      calleeIsAttr(call, "executemany") or
      calleeIsAttr(call, "raw")
    ) and
    sink = call.getArg(0)
  )
}
```

---

## 四、常见 Sanitizer 模式

### 1. **Django URL 验证**
```ql
predicate isDjangoURLValidationSanitizer(DataFlow::Node n) {
  exists(DataFlow::CallCfgNode call |
    (
      calleeIsGlobalName(call, "url_has_allowed_host_and_scheme") or
      calleeIsGlobalName(call, "is_safe_url") or
      calleeIsAttr(call, "url_has_allowed_host_and_scheme") or
      calleeIsAttr(call, "is_safe_url")
    ) and
    n = call.getArg(0)
  )
}
```

### 2. **反向路由（Django reverse）**
```ql
predicate isReverseResult(DataFlow::Node n) {
  exists(DataFlow::CallCfgNode call |
    n.asCfgNode().getNode() = call.getNode().getNode() and
    (calleeIsGlobalName(call, "reverse") or calleeIsAttr(call, "reverse"))
  )
}
```

### 3. **常量路径（以 / 开头）**
```ql
predicate isLeadingSlashConst(DataFlow::Node n) {
  exists(StringLiteral s |
    n.asCfgNode().getNode() = s and
    s.getText().regexpMatch("^[rRbBuUfF]*[\"']/")
  )
}
```

---

## 五、高级技巧：isAdditionalFlowStep

### 用例1: open() 返回值传播（文件写入场景）
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // open(tainted_path) 的 taint 传播到返回的文件对象
  exists(DataFlow::CallCfgNode call |
    calleeIsGlobalName(call, "open") and
    src = call.getArg(0) and  // 被污染的路径参数
    dst = call                // open() 的返回值（文件对象）
  )
}
```
**场景**: 检测 `fp = open(user_input); fp.write(...)`

### 用例2: 字符串拼接传播（可选，通常内置）
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // 字符串格式化: f"...{tainted}..."
  exists(Fstring fs |
    dst.asCfgNode().getNode() = fs and
    src.asCfgNode().getNode() = fs.getAValue()
  )
}
```

---

## 六、Source 的三种常见模式

### 模式1: 远程输入（最常用）
```ql
predicate isSource(DataFlow::Node src) {
  src instanceof RemoteFlowSource
}
```
**涵盖**: Flask/Django 的 `request.GET`, `request.POST`, `request.args` 等

### 模式2: 特定函数的参数
```ql
predicate isSource(DataFlow::Node src) {
  exists(Function f |
    isAffectedFunction(f) and
    src.getScope() = f and
    src instanceof DataFlow::ParameterNode
  )
}
```
**用途**: 检测库内部的污点传播（如 PIL.ImageMath.eval 的 expression 参数）

### 模式3: 组合条件（精确定位）
```ql
predicate isSource(DataFlow::Node src) {
  src instanceof RemoteFlowSource and
  inTargetFile(src, "views.py") and
  inTargetFunction(src, "vulnerable_handler")
}
```
**用途**: 降噪，只关注特定场景

---

## 七、实战检查清单

生成查询后，逐项检查：

### 结构检查
- [ ] 包含所有必需的 import（python, DataFlow, TaintTracking, RemoteFlowSources）
- [ ] module 名称有意义（如 `SqlInjectionConfig`, `OpenRedirectConfig`）
- [ ] 使用了 `module Flow = TaintTracking::Global<Config>`
- [ ] 导入了 `Flow::PathGraph`
- [ ] select 语句有 4 或 7 个参数

### 语法检查
- [ ] Helper 谓词使用 `asCfgNode().getNode()` 访问 AST
- [ ] 使用 `calleeIsGlobalName` 而不是直接检查 Name
- [ ] 使用 `calleeIsAttr` 检查方法调用
- [ ] Sanitizer/AdditionalFlowStep 使用 `none()` 表示空

### 逻辑检查
- [ ] Source 定义合理（通常是 RemoteFlowSource）
- [ ] Sink 精确（匹配特定危险函数的特定参数）
- [ ] Sanitizer 有实际意义（不要空实现）
- [ ] 有必要的作用域限定（文件/函数）以降噪

---

## 八、常见错误规避

### ❌ 错误1: 直接对 DataFlow::Node 做 AST 断言
```ql
// 错误
call.getFunction() instanceof Name

// 正确
call.getFunction().asCfgNode().getNode() instanceof Name
```

### ❌ 错误2: isSanitizer 不属于 ConfigSig
```ql
// 旧版写法（已弃用）
module Config extends TaintTracking::Configuration {
  override predicate isSanitizer(DataFlow::Node n) { ... }
}

// 新版写法
module Config implements DataFlow::ConfigSig {
  predicate isSanitizer(DataFlow::Node n) { ... }  // ✅ 这是正确的
}
```

### ❌ 错误3: select 语句参数不匹配
```ql
// 错误：8 个参数
select sink.getNode(), src, sink, "msg", src, "source", sink, "sink"

// 正确：7 个参数
select sink.getNode(), src, sink, "msg", src, "source", sink, "sink"

// 或简化：4 个参数
select sink.getNode(), src, sink, "msg"
```

---

## 九、完整示例（Django Open Redirect）

```ql
/**
 * @kind path-problem
 * @name Django Open Redirect
 * @description User-controlled URL flows to redirect without validation
 * @id python/django-open-redirect
 * @tags security, taint, cwe-601
 * @problem.severity medium
 * @precision high
 */

import python
import semmle.python.dataflow.new.DataFlow
import semmle.python.dataflow.new.TaintTracking
import semmle.python.dataflow.new.RemoteFlowSources

/** ========== Helpers ========== */
predicate calleeIsGlobalName(DataFlow::CallCfgNode call, string nm) {
  call.getFunction().asCfgNode().getNode() instanceof Name and
  call.getFunction().asCfgNode().getNode().(Name).getId() = nm
}

predicate calleeIsAttr(DataFlow::CallCfgNode call, string attr) {
  call.getFunction() instanceof DataFlow::AttrRead and
  call.getFunction().(DataFlow::AttrRead).getAttributeName() = attr
}

predicate isRedirectCall(DataFlow::CallCfgNode call) {
  calleeIsGlobalName(call, "redirect") or
  calleeIsGlobalName(call, "HttpResponseRedirect") or
  calleeIsAttr(call, "redirect")
}

predicate isURLValidationCall(DataFlow::Node n) {
  exists(DataFlow::CallCfgNode call |
    (calleeIsGlobalName(call, "url_has_allowed_host_and_scheme") or
     calleeIsAttr(call, "url_has_allowed_host_and_scheme")) and
    n = call.getArg(0)
  )
}

/** ========== Config ========== */
module RedirectConfig implements DataFlow::ConfigSig {
  predicate isSource(DataFlow::Node src) {
    src instanceof RemoteFlowSource
  }

  predicate isSink(DataFlow::Node sink) {
    exists(DataFlow::CallCfgNode call |
      isRedirectCall(call) and
      sink = call.getArg(0)
    )
  }

  predicate isSanitizer(DataFlow::Node n) {
    isURLValidationCall(n)
  }
}

module Flow = TaintTracking::Global<RedirectConfig>;
import Flow::PathGraph

from Flow::PathNode src, Flow::PathNode sink
where Flow::flowPath(src, sink)
select sink.getNode(), src, sink,
  "Untrusted URL flows to redirect without validation.",
  src, "source", sink, "sink"
```

---

## 十、参考真实案例

- **CVE-2024-8412**: Django open redirect（URL 验证）
- **CVE-2025-54802**: pyLoad 任意文件写入（open + write）
- **CVE-2024-7099**: SQL 注入（参数级 source）
- **CVE-2022-22817**: PIL.ImageMath.eval（库内污点传播）
- **CVE-2025-47789**: Django redirect（sanitizer 降噪）
- **CVE-2025-46725**: 通用 eval/exec 检测

更多案例参见 `resources/codeql/python/py/` 目录。

