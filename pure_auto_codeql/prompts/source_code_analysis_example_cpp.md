# C/C++源码分析实战示例

## 场景：CodeQL查询返回空结果

### CodeQL查询结果（空）
```
CodeQL Results:
从source到sink没有找到数据流路径。

Source候选: projects/CVE-2023-5678/source_code/main.c:15 - recv()
Sink候选: projects/CVE-2023-5678/source_code/main.c:45 - strcpy()
```

---

## 正确的LLM分析流程

### 步骤1：读取源文件

**Action**: 使用server-filesystem读取 `projects/CVE-2023-5678/source_code/main.c`

**源文件内容**:
```c
// main.c
#include <string.h>
#include <sys/socket.h>

char* receive_data(int sockfd) {
    /* Source点：网络接收 */
    static char buffer[1024];
    recv(sockfd, buffer, 1024, 0);  // 第15行
    return buffer;
}

void process_data(char* input, char* output) {
    /* 中间转换：内存拷贝 */
    memcpy(output, input, strlen(input));  // 第25行
}

void store_data(char* data) {
    /* Sink点：缓冲区溢出 */
    char small_buf[64];
    strcpy(small_buf, data);  // 第45行 - 危险！
}

int main() {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    char* received = receive_data(sock);     // 第15行
    char processed[1024];
    process_data(received, processed);       // 第25行 memcpy
    store_data(processed);                   // 第45行 strcpy
}
```

### 步骤2：分析数据流路径

**LLM思考过程**:
```
1. Source: receive_data() 调用 recv() 返回 buffer (char*)
   ↓
2. 传递给: process_data(input=received, output=processed)
   ↓
3. memcpy(output, input, strlen(input)) ← 【断流点1！】
   - input: 源数据
   - output: 目标缓冲区
   - CodeQL可能无法追踪memcpy的数据流
   ↓
4. 传递给: store_data(data=processed)
   ↓
5. Sink: strcpy(small_buf, data) - 缓冲区溢出

问题：
- memcpy的参数传递未被CodeQL正确建模
- 第二个参数（output）接收了污染数据
```

### 步骤3：读取头文件（如果需要）

**Action**: 检查是否有宏定义或typedef

```c
// 假设有 utils.h
#define COPY_DATA(dest, src) memcpy(dest, src, strlen(src))

// 如果代码使用宏，需要理解宏展开后的逻辑
```

### 步骤4：识别断流点

**断流点分析**:
```json
{
  "breakpoints": [
    {
      "id": "breakpoint_1",
      "file_path": "main.c",
      "line_number": "25",
      "breakpoint_type": "内存操作函数 - memcpy",
      "breakpoint_reason": "memcpy(output, input, len)将污染数据从input拷贝到output，CodeQL未建模此数据流",
      "source_context": "memcpy(output, input, strlen(input));",
      "data_flow_before": "input (char*) 来自 receive_data() 的返回值",
      "data_flow_after": "output (char*) 传递给 store_data()",
      "connection_required": true,
      "importance": "high",
      "cpp_specific_info": {
        "is_pointer_operation": true,
        "is_type_cast": false,
        "is_memory_function": true,
        "is_macro": false,
        "pointer_level": 1,
        "involved_types": ["char*"],
        "memory_function": "memcpy"
      }
    }
  ],
  "analysis_summary": {
    "total_breakpoints": "1",
    "main_flow_path": "recv() → receive_data() → process_data() → memcpy() → store_data() → strcpy()",
    "connection_complexity": "低",
    "recommended_approach": "为memcpy添加isAdditionalFlowStep条件，连接第二个参数(src)到第一个参数(dst)",
    "selection_reason": "memcpy是唯一的数据转换点，连接此处即可完成数据流",
    "cpp_challenges": "内存操作函数memcpy的参数传递未被CodeQL默认建模"
  }
}
```

### 步骤5：生成修复条件

**isAdditionalFlowStep条件**:
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  // 连接memcpy的源参数到目标参数
  exists(FunctionCall fc |
    fc.getTarget().hasName("memcpy") and
    src.asExpr() = fc.getArgument(1) and  // 源参数(input)
    dst.asExpr() = fc.getArgument(0)      // 目标参数(output)
  )
}
```

---

## 更复杂的案例：指针解引用

### 源代码
```c
struct packet {
    char* data;
    int length;
};

char* get_packet_data(int sock) {
    static char buffer[1024];
    recv(sock, buffer, 1024, 0);  // Source
    return buffer;
}

void process_packet(struct packet* pkt) {
    char* data = pkt->data;  // 断流点1：结构体字段访问
    char output[256];
    sprintf(output, "%s", data);  // 断流点2：sprintf
    system(output);  // Sink
}

int main() {
    struct packet pkt;
    pkt.data = get_packet_data(sock);  // 断流点3：结构体字段赋值
    process_packet(&pkt);
}
```

### 断流点分析

**断流点1：结构体字段赋值**
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(Assignment assign, FieldAccess fa |
    assign.getLValue() = fa and
    fa.getTarget().getName() = "data" and
    src.asExpr() = assign.getRValue() and
    dst.asExpr() = fa
  )
}
```

**断流点2：结构体字段访问**
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(FieldAccess fa |
    fa.getTarget().getName() = "data" and
    src.asExpr() = fa.getQualifier() and
    dst.asExpr() = fa
  )
}
```

**断流点3：sprintf**
```ql
predicate isAdditionalFlowStep(DataFlow::Node src, DataFlow::Node dst) {
  exists(FunctionCall fc |
    fc.getTarget().hasName("sprintf") and
    dst.asExpr() = fc.getArgument(0) and  // 目标缓冲区
    src.asExpr() = fc.getArgument(2)      // 格式化参数（跳过格式串）
  )
}
```

---

## 关键要点

### ✅ 正确做法
1. **读取完整源文件** - 包括.c和相关的.h文件
2. **理解指针传递** - 追踪指针的来源和去向
3. **识别内存函数** - memcpy, strcpy, sprintf, memmove等
4. **分析结构体字段** - 结构体是C中数据组织的主要方式
5. **检查宏定义** - 宏可能隐藏了实际的函数调用

### ❌ 错误做法
1. 忽略指针操作，只看函数调用
2. 不查看头文件中的宏定义和结构体定义
3. 假设所有内存函数都被CodeQL建模
4. 忽略结构体字段的赋值和访问

---

## 常见断流函数清单

### 内存操作
```c
memcpy(dest, src, len)    // src → dest
memmove(dest, src, len)   // src → dest
memset(dest, value, len)  // value → dest
bcopy(src, dest, len)     // src → dest
```

### 字符串操作
```c
strcpy(dest, src)         // src → dest
strncpy(dest, src, n)     // src → dest
strcat(dest, src)         // src → dest
sprintf(dest, fmt, ...)   // args → dest
snprintf(dest, n, fmt, ...) // args → dest
```

### 指针操作
```c
*ptr = value              // value → *ptr (解引用赋值)
ptr = &var                // &var → ptr (取地址)
arr[i] = value            // value → arr[i] (数组访问)
struct.field = value      // value → field (字段赋值)
```

### 输入函数
```c
read(fd, buf, len)        // → buf
recv(sock, buf, len, 0)   // → buf
fread(buf, size, n, fp)   // → buf
fgets(buf, size, fp)      // → buf
scanf(fmt, &var)          // → var
```

---

## 实战建议

1. **绘制调用图** - 在纸上画出函数调用关系
2. **标注指针传递** - 每个指针参数的来源和去向
3. **识别类型转换** - 特别是void*转换
4. **检查宏展开** - 使用`gcc -E`查看预处理后的代码
5. **验证缓冲区大小** - 确认是否真的有安全问题

**记住**：C/C++的数据流追踪比Python复杂，因为涉及指针、内存操作和宏！

