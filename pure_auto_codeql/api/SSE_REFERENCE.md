# SSE 流式事件参考文档

本文档描述了 PureAutoCodeQL 分析服务的 Server-Sent Events (SSE) 流式事件格式，帮助前端开发者正确处理实时分析进度。

## 目录

- [快速开始](#快速开始)
- [事件类型](#事件类型)
- [事件数据结构](#事件数据结构)
- [Agent流式输出](#agent流式输出)
- [完整事件流程](#完整事件流程)
- [前端实现示例](#前端实现示例)
- [错误处理](#错误处理)

---

## 快速开始

### 1. 启动分析任务

```http
POST /api/analysis/start
Content-Type: application/json

{
  "case_id": "CVE-2021-21985",
  "language": "java",
  "max_rounds": 3,
  "enable_cve_analysis": true,
  "enable_sink_analysis": true
}
```

响应：
```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "case_id": "CVE-2021-21985",
  "status": "running",
  "created_at": "2025-11-08T10:30:00Z"
}
```

### 2. 连接SSE流

```http
GET /api/analysis/{task_id}/stream
Accept: text/event-stream
```

---

## 事件类型

所有SSE事件都遵循以下格式：

```
event: message
data: {"type": "event_type", "timestamp": "...", ...}
```

### 基础事件类型

| 事件类型 | 说明 | 触发时机 |
|---------|------|---------|
| `step_start` | 分析步骤开始 | 整个分析流程开始时 |
| `step_progress` | 步骤进度更新 | 步骤执行过程中 |
| `step_complete` | 步骤完成 | 步骤成功完成时 |
| `completed` | 分析完成 | 整个分析流程完成时 |
| `error` | 错误发生 | 任何错误发生时 |

### Agent级别事件类型

| 事件类型 | 说明 | 触发时机 |
|---------|------|---------|
| `agent_start` | Agent开始执行 | Agent开始工作时 |
| `agent_thinking` | Agent思考过程 | Agent推理、工具选择、流式输出时 |
| `agent_tool_call` | Agent调用工具 | Agent调用MCP工具时 |
| `agent_complete` | Agent执行完成 | Agent完成工作时 |

---

## 事件数据结构

### 基础事件字段

所有事件都包含以下基础字段：

```typescript
interface BaseEvent {
  type: string;           // 事件类型
  timestamp: string;      // ISO 8601 时间戳
  message?: string;       // 可选的消息描述
  data?: any;            // 可选的附加数据
}
```

### Agent事件字段

Agent级别的事件增加了以下字段：

```typescript
interface AgentEvent extends BaseEvent {
  agent_name: string;     // Agent名称
  agent_type: string;     // Agent类型标识
}
```

### 各事件类型详细结构

#### 1. `step_start` - 步骤开始

```json
{
  "type": "step_start",
  "timestamp": "2025-11-08T10:30:00.123Z",
  "step_name": "analysis",
  "message": "开始分析任务",
  "data": {
    "case_id": "CVE-2021-21985",
    "language": "java"
  }
}
```

#### 2. `agent_start` - Agent开始

```json
{
  "type": "agent_start",
  "timestamp": "2025-11-08T10:30:01.456Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "开始CVE分析",
  "data": {
    "json_path": "/path/to/cve.json"
  }
}
```

#### 3. `agent_thinking` - Agent思考过程

**思考/工具选择：**
```json
{
  "type": "agent_thinking",
  "timestamp": "2025-11-08T10:30:02.789Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "决定使用工具 'read_text_file'",
  "data": {
    "tool": "read_text_file",
    "tool_input": {
      "path": "projects/CVE-2021-21985/cve.json"
    }
  }
}
```

**流式输出chunk：**
```json
{
  "type": "agent_thinking",
  "timestamp": "2025-11-08T10:30:03.123Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "根据CVE JSON数据，这是一个远程代码执行漏洞...",
  "data": {
    "stream_chunk": "根据CVE JSON数据，这是一个远程代码执行漏洞..."
  }
}
```

#### 4. `agent_tool_call` - Agent调用工具

```json
{
  "type": "agent_tool_call",
  "timestamp": "2025-11-08T10:30:02.890Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "开始调用工具: read_text_file",
  "data": {
    "tool_name": "read_text_file",
    "event_data": {
      "input": {
        "path": "projects/CVE-2021-21985/cve.json"
      }
    }
  }
}
```

#### 5. `agent_complete` - Agent完成

```json
{
  "type": "agent_complete",
  "timestamp": "2025-11-08T10:30:15.456Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "CVE分析完成",
  "data": {
    "success": true
  }
}
```

#### 6. `completed` - 分析完成

```json
{
  "type": "completed",
  "timestamp": "2025-11-08T10:35:00.000Z",
  "step_name": "analysis",
  "message": "分析任务完成",
  "data": {
    "success": true,
    "execution_time": 300.5
  }
}
```

#### 7. `error` - 错误

```json
{
  "type": "error",
  "timestamp": "2025-11-08T10:30:10.000Z",
  "agent_name": "CVE Analysis Agent",
  "agent_type": "cve_analysis",
  "message": "CVE分析失败: File not found",
  "data": {
    "error": "File not found"
  }
}
```

---

## Agent流式输出

### Agent类型列表

分析过程中会依次执行以下Agent，每个Agent都会推送完整的事件流：

| Agent Type | Agent Name | 描述 | 输出内容 |
|-----------|-----------|------|---------|
| `cve_analysis` | CVE Analysis Agent | CVE分析 | 漏洞类型、技术细节、Sink点、Source点 |
| `sink_analysis` | Sink Path Analysis Agent | Sink路径分析 | 危险函数调用点、执行路径 |
| `source_analysis` | Source Analysis Agent | Source点分析 | 用户输入点、数据来源 |
| `codeql_compose` | CodeQL Compose Tool | CodeQL查询生成 | 迭代式查询生成和验证 |
| `codeql_generation` | CodeQL Generation Agent | CodeQL代码生成 | 查询代码（每轮迭代） |
| `codeql_error_analysis` | CodeQL Error Analysis Agent | CodeQL错误分析 | 错误诊断和修复建议（失败时） |

### Agent执行流程

```
1. CVE Analysis Agent
   ├─ agent_start
   ├─ agent_thinking (多次，包含流式输出)
   ├─ agent_tool_call (读取文件、搜索等)
   └─ agent_complete

2. Sink Path Analysis Agent
   ├─ agent_start
   ├─ agent_thinking (多次)
   ├─ agent_tool_call (浏览代码目录)
   └─ agent_complete

3. Source Analysis Agent
   ├─ agent_start
   ├─ agent_thinking (多次)
   ├─ agent_tool_call (搜索源码)
   └─ agent_complete

4. CodeQL Compose Tool
   ├─ agent_start
   │
   ├─ [第1轮] CodeQL Generation Agent
   │   ├─ agent_start
   │   ├─ agent_thinking (生成查询)
   │   └─ agent_complete
   │
   ├─ [可选] CodeQL Error Analysis Agent (如果有错误)
   │   ├─ agent_start
   │   ├─ agent_thinking (分析错误)
   │   └─ agent_complete
   │
   ├─ [第N轮] ... (最多max_rounds轮)
   │
   └─ agent_complete
```

### Agent思考过程内容

每个Agent的 `agent_thinking` 事件包含两种内容：

**1. 工具选择决策**
```json
{
  "type": "agent_thinking",
  "message": "决定使用工具 'search_files'",
  "data": {
    "tool": "search_files",
    "tool_input": {"pattern": "*.java"}
  }
}
```

**2. 流式输出内容（模型生成的文本）**
```json
{
  "type": "agent_thinking",
  "message": "基于分析结果，漏洞的sink点位于...",
  "data": {
    "stream_chunk": "基于分析结果，漏洞的sink点位于..."
  }
}
```

---

## 完整事件流程

### 典型的分析任务事件序列

```
1. step_start (整体分析开始)
   ↓
2. agent_start (CVE Analysis Agent)
   ↓
3. agent_thinking (决定读取CVE JSON)
   ↓
4. agent_tool_call (read_text_file)
   ↓
5. agent_thinking (流式输出分析结果 - 多个chunk)
   ↓
6. agent_complete (CVE Analysis Agent完成)
   ↓
7. agent_start (Sink Path Analysis Agent)
   ↓
8. agent_thinking (决定浏览源码目录)
   ↓
9. agent_tool_call (list_directory)
   ↓
10. agent_thinking (流式输出Sink分析 - 多个chunk)
   ↓
11. agent_complete (Sink Path Analysis Agent完成)
   ↓
12. agent_start (Source Analysis Agent)
   ↓
13. agent_thinking + agent_tool_call (搜索源码)
   ↓
14. agent_thinking (流式输出Source分析 - 多个chunk)
   ↓
15. agent_complete (Source Analysis Agent完成)
   ↓
16. agent_start (CodeQL Compose Tool)
   ↓
17. agent_start (CodeQL Generation Agent - 第1轮)
   ↓
18. agent_thinking (生成CodeQL查询 - 多个chunk)
   ↓
19. agent_complete (CodeQL Generation Agent - 第1轮)
   ↓
20. [如果有语法错误]
    ├─ agent_start (CodeQL Error Analysis Agent)
    ├─ agent_thinking (分析错误并提供修复建议)
    ├─ agent_complete (CodeQL Error Analysis Agent)
    └─ [返回步骤17，进行下一轮]
   ↓
21. agent_complete (CodeQL Compose Tool完成)
   ↓
22. completed (整体分析完成)
```

---

## 前端实现示例

### JavaScript/TypeScript 示例

#### 基础连接

```typescript
interface StreamEvent {
  type: string;
  timestamp: string;
  message?: string;
  data?: any;
  agent_name?: string;
  agent_type?: string;
  step_name?: string;
}

function connectToAnalysisStream(taskId: string) {
  const eventSource = new EventSource(
    `/api/analysis/${taskId}/stream`
  );

  eventSource.onmessage = (event) => {
    const data: StreamEvent = JSON.parse(event.data);
    handleEvent(data);
  };

  eventSource.onerror = (error) => {
    console.error('SSE连接错误:', error);
    eventSource.close();
  };

  return eventSource;
}
```

#### 事件处理器

```typescript
function handleEvent(event: StreamEvent) {
  switch (event.type) {
    case 'step_start':
      handleStepStart(event);
      break;
    case 'agent_start':
      handleAgentStart(event);
      break;
    case 'agent_thinking':
      handleAgentThinking(event);
      break;
    case 'agent_tool_call':
      handleAgentToolCall(event);
      break;
    case 'agent_complete':
      handleAgentComplete(event);
      break;
    case 'completed':
      handleCompleted(event);
      break;
    case 'error':
      handleError(event);
      break;
    default:
      console.log('未知事件类型:', event.type);
  }
}
```

#### Agent状态跟踪

```typescript
interface AgentState {
  name: string;
  type: string;
  status: 'running' | 'completed' | 'error';
  startTime: string;
  endTime?: string;
  output: string;
  tools: string[];
}

class AnalysisTracker {
  private agents: Map<string, AgentState> = new Map();
  private currentAgent: string | null = null;

  handleAgentStart(event: StreamEvent) {
    const agentId = event.agent_type!;
    this.currentAgent = agentId;
    
    this.agents.set(agentId, {
      name: event.agent_name!,
      type: event.agent_type!,
      status: 'running',
      startTime: event.timestamp,
      output: '',
      tools: []
    });

    this.updateUI(`🚀 ${event.agent_name} 开始执行`);
  }

  handleAgentThinking(event: StreamEvent) {
    if (!this.currentAgent) return;
    
    const agent = this.agents.get(this.currentAgent);
    if (!agent) return;

    // 累积输出内容
    if (event.data?.stream_chunk) {
      agent.output += event.data.stream_chunk;
      this.updateAgentOutput(this.currentAgent, agent.output);
    }

    // 记录工具选择
    if (event.data?.tool) {
      agent.tools.push(event.data.tool);
      this.updateUI(`🤔 ${agent.name} 决定使用工具: ${event.data.tool}`);
    }
  }

  handleAgentToolCall(event: StreamEvent) {
    if (!this.currentAgent) return;
    
    const agent = this.agents.get(this.currentAgent);
    if (!agent) return;

    this.updateUI(`🔧 ${agent.name} 调用工具: ${event.data?.tool_name}`);
  }

  handleAgentComplete(event: StreamEvent) {
    if (!this.currentAgent) return;
    
    const agent = this.agents.get(this.currentAgent);
    if (!agent) return;

    agent.status = event.data?.success ? 'completed' : 'error';
    agent.endTime = event.timestamp;
    
    const duration = new Date(agent.endTime).getTime() - 
                    new Date(agent.startTime).getTime();
    
    this.updateUI(`✅ ${agent.name} 完成 (耗时: ${duration}ms)`);
    this.currentAgent = null;
  }

  private updateUI(message: string) {
    // 更新UI显示
    console.log(message);
  }

  private updateAgentOutput(agentId: string, output: string) {
    // 更新Agent输出显示
    console.log(`Agent ${agentId} 输出:`, output);
  }
}
```

#### React Hook 示例

```typescript
import { useEffect, useState } from 'react';

interface AnalysisProgress {
  currentAgent: string | null;
  agentOutputs: Record<string, string>;
  logs: string[];
  status: 'idle' | 'running' | 'completed' | 'error';
}

export function useAnalysisStream(taskId: string | null) {
  const [progress, setProgress] = useState<AnalysisProgress>({
    currentAgent: null,
    agentOutputs: {},
    logs: [],
    status: 'idle'
  });

  useEffect(() => {
    if (!taskId) return;

    const eventSource = new EventSource(
      `/api/analysis/${taskId}/stream`
    );

    eventSource.onmessage = (event) => {
      const data: StreamEvent = JSON.parse(event.data);

      setProgress(prev => {
        const newProgress = { ...prev };

        switch (data.type) {
          case 'step_start':
            newProgress.status = 'running';
            newProgress.logs.push(`开始分析: ${data.message}`);
            break;

          case 'agent_start':
            newProgress.currentAgent = data.agent_name || null;
            newProgress.logs.push(`🚀 ${data.agent_name} 开始执行`);
            break;

          case 'agent_thinking':
            if (data.agent_type && data.data?.stream_chunk) {
              const current = newProgress.agentOutputs[data.agent_type] || '';
              newProgress.agentOutputs[data.agent_type] = 
                current + data.data.stream_chunk;
            }
            if (data.data?.tool) {
              newProgress.logs.push(
                `🤔 ${data.agent_name} 使用工具: ${data.data.tool}`
              );
            }
            break;

          case 'agent_tool_call':
            newProgress.logs.push(
              `🔧 调用工具: ${data.data?.tool_name}`
            );
            break;

          case 'agent_complete':
            newProgress.logs.push(`✅ ${data.agent_name} 完成`);
            break;

          case 'completed':
            newProgress.status = 'completed';
            newProgress.currentAgent = null;
            newProgress.logs.push('🎉 分析完成！');
            break;

          case 'error':
            newProgress.status = 'error';
            newProgress.logs.push(`❌ 错误: ${data.message}`);
            break;
        }

        return newProgress;
      });
    };

    eventSource.onerror = () => {
      setProgress(prev => ({
        ...prev,
        status: 'error',
        logs: [...prev.logs, '❌ 连接断开']
      }));
      eventSource.close();
    };

    return () => {
      eventSource.close();
    };
  }, [taskId]);

  return progress;
}
```

#### Vue 3 Composition API 示例

```typescript
import { ref, onUnmounted, watch } from 'vue';

export function useAnalysisStream(taskId: Ref<string | null>) {
  const currentAgent = ref<string | null>(null);
  const agentOutputs = ref<Record<string, string>>({});
  const logs = ref<string[]>([]);
  const status = ref<'idle' | 'running' | 'completed' | 'error'>('idle');
  
  let eventSource: EventSource | null = null;

  const connect = (id: string) => {
    if (eventSource) {
      eventSource.close();
    }

    eventSource = new EventSource(`/api/analysis/${id}/stream`);

    eventSource.onmessage = (event) => {
      const data: StreamEvent = JSON.parse(event.data);

      switch (data.type) {
        case 'step_start':
          status.value = 'running';
          logs.value.push(`开始分析: ${data.message}`);
          break;

        case 'agent_start':
          currentAgent.value = data.agent_name || null;
          logs.value.push(`🚀 ${data.agent_name} 开始执行`);
          break;

        case 'agent_thinking':
          if (data.agent_type && data.data?.stream_chunk) {
            const current = agentOutputs.value[data.agent_type] || '';
            agentOutputs.value[data.agent_type] = 
              current + data.data.stream_chunk;
          }
          break;

        case 'agent_complete':
          logs.value.push(`✅ ${data.agent_name} 完成`);
          break;

        case 'completed':
          status.value = 'completed';
          currentAgent.value = null;
          logs.value.push('🎉 分析完成！');
          break;

        case 'error':
          status.value = 'error';
          logs.value.push(`❌ 错误: ${data.message}`);
          break;
      }
    };

    eventSource.onerror = () => {
      status.value = 'error';
      logs.value.push('❌ 连接断开');
      eventSource?.close();
    };
  };

  watch(taskId, (newId) => {
    if (newId) {
      connect(newId);
    }
  });

  onUnmounted(() => {
    eventSource?.close();
  });

  return {
    currentAgent,
    agentOutputs,
    logs,
    status
  };
}
```

---

## 错误处理

### 常见错误场景

#### 1. 连接错误

```typescript
eventSource.onerror = (error) => {
  console.error('SSE连接错误:', error);
  
  // 实现重连逻辑
  if (retryCount < MAX_RETRIES) {
    setTimeout(() => {
      retryCount++;
      connectToAnalysisStream(taskId);
    }, RETRY_DELAY);
  }
};
```

#### 2. Agent执行错误

当收到 `error` 类型事件时：

```typescript
function handleError(event: StreamEvent) {
  console.error('分析错误:', {
    agent: event.agent_name,
    type: event.agent_type,
    message: event.message,
    error: event.data?.error
  });
  
  // 显示错误信息给用户
  showErrorNotification({
    title: `${event.agent_name} 执行失败`,
    message: event.message,
    details: event.data?.error
  });
}
```

#### 3. 超时处理

```typescript
const ANALYSIS_TIMEOUT = 600000; // 10分钟

const timeoutId = setTimeout(() => {
  console.warn('分析超时');
  eventSource.close();
  handleTimeout();
}, ANALYSIS_TIMEOUT);

eventSource.addEventListener('completed', () => {
  clearTimeout(timeoutId);
});
```

### 重连策略

```typescript
class SSEConnection {
  private eventSource: EventSource | null = null;
  private retryCount = 0;
  private readonly maxRetries = 3;
  private readonly retryDelay = 2000;

  connect(taskId: string) {
    this.eventSource = new EventSource(
      `/api/analysis/${taskId}/stream`
    );

    this.eventSource.onerror = () => {
      this.eventSource?.close();
      
      if (this.retryCount < this.maxRetries) {
        console.log(`重连中... (${this.retryCount + 1}/${this.maxRetries})`);
        
        setTimeout(() => {
          this.retryCount++;
          this.connect(taskId);
        }, this.retryDelay * this.retryCount);
      } else {
        console.error('达到最大重连次数');
        this.handleFinalError();
      }
    };

    this.eventSource.onmessage = (event) => {
      this.retryCount = 0; // 重置重连计数
      this.handleMessage(event);
    };
  }

  disconnect() {
    this.eventSource?.close();
    this.eventSource = null;
  }

  private handleMessage(event: MessageEvent) {
    // 处理消息
  }

  private handleFinalError() {
    // 处理最终失败
  }
}
```

---

## 最佳实践

### 1. 性能优化

- **节流更新**: 对于高频的 `agent_thinking` 事件，使用节流避免过度渲染

```typescript
import { throttle } from 'lodash';

const updateOutput = throttle((agentType: string, content: string) => {
  // 更新UI
  setAgentOutput(agentType, content);
}, 100); // 每100ms最多更新一次
```

- **虚拟滚动**: 日志列表使用虚拟滚动避免DOM过多

```typescript
import { VirtualScroller } from 'virtual-scroller';

<VirtualScroller items={logs} itemHeight={30}>
  {(log) => <LogItem log={log} />}
</VirtualScroller>
```

### 2. 用户体验

- **进度指示器**: 显示当前执行的Agent和整体进度

```typescript
const agentSequence = [
  'cve_analysis',
  'sink_analysis', 
  'source_analysis',
  'codeql_compose'
];

const currentStep = agentSequence.indexOf(currentAgentType);
const progress = ((currentStep + 1) / agentSequence.length) * 100;
```

- **实时输出显示**: 流式显示Agent的输出内容

```typescript
<div className="agent-output">
  <h3>{currentAgent}</h3>
  <pre className="streaming-content">
    {agentOutputs[currentAgentType]}
    <span className="cursor">|</span>
  </pre>
</div>
```

### 3. 数据保存

- **保存完整日志**: 将所有事件保存以便后续查看

```typescript
const events: StreamEvent[] = [];

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  events.push(data);
  
  // 定期保存到本地存储
  if (events.length % 100 === 0) {
    localStorage.setItem(
      `analysis_${taskId}_events`, 
      JSON.stringify(events)
    );
  }
};
```

### 4. 可访问性

- **屏幕阅读器支持**: 使用 ARIA live regions

```html
<div aria-live="polite" aria-atomic="true">
  {latestLogMessage}
</div>
```

---

## 调试技巧

### 1. 控制台输出所有事件

```typescript
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  console.group(`[${data.type}] ${data.timestamp}`);
  console.log('Agent:', data.agent_name, `(${data.agent_type})`);
  console.log('Message:', data.message);
  console.log('Data:', data.data);
  console.groupEnd();
  
  handleEvent(data);
};
```

### 2. 事件统计

```typescript
const eventStats = {
  total: 0,
  byType: {} as Record<string, number>,
  byAgent: {} as Record<string, number>
};

function trackEvent(event: StreamEvent) {
  eventStats.total++;
  eventStats.byType[event.type] = (eventStats.byType[event.type] || 0) + 1;
  
  if (event.agent_type) {
    eventStats.byAgent[event.agent_type] = 
      (eventStats.byAgent[event.agent_type] || 0) + 1;
  }
}

// 在分析完成后查看统计
console.table(eventStats.byType);
console.table(eventStats.byAgent);
```

### 3. 事件时序分析

```typescript
interface EventTiming {
  type: string;
  timestamp: number;
  duration?: number;
}

const timeline: EventTiming[] = [];
let lastTimestamp: number | null = null;

function recordEvent(event: StreamEvent) {
  const timestamp = new Date(event.timestamp).getTime();
  
  const timing: EventTiming = {
    type: event.type,
    timestamp: timestamp,
    duration: lastTimestamp ? timestamp - lastTimestamp : undefined
  };
  
  timeline.push(timing);
  lastTimestamp = timestamp;
}

// 分析最慢的步骤
const slowSteps = timeline
  .filter(t => t.duration)
  .sort((a, b) => (b.duration || 0) - (a.duration || 0))
  .slice(0, 10);

console.log('最慢的10个步骤:', slowSteps);
```

---

## 附录

### A. 完整的TypeScript类型定义

```typescript
// 事件类型枚举
export enum StreamEventType {
  // 基础事件
  STEP_START = 'step_start',
  STEP_PROGRESS = 'step_progress',
  STEP_COMPLETE = 'step_complete',
  COMPLETED = 'completed',
  ERROR = 'error',
  
  // Agent事件
  AGENT_START = 'agent_start',
  AGENT_THINKING = 'agent_thinking',
  AGENT_TOOL_CALL = 'agent_tool_call',
  AGENT_COMPLETE = 'agent_complete',
}

// Agent类型枚举
export enum AgentType {
  CVE_ANALYSIS = 'cve_analysis',
  SINK_ANALYSIS = 'sink_analysis',
  SOURCE_ANALYSIS = 'source_analysis',
  CODEQL_COMPOSE = 'codeql_compose',
  CODEQL_GENERATION = 'codeql_generation',
  CODEQL_ERROR_ANALYSIS = 'codeql_error_analysis',
}

// 事件接口
export interface StreamEvent {
  type: StreamEventType;
  timestamp: string;
  message?: string;
  data?: any;
  
  // Agent事件特有字段
  agent_name?: string;
  agent_type?: AgentType;
  
  // 步骤事件特有字段
  step_name?: string;
}

// 工具调用数据
export interface ToolCallData {
  tool: string;
  tool_input?: any;
}

// 流式输出数据
export interface StreamChunkData {
  stream_chunk: string;
}

// Agent工具调用事件数据
export interface AgentToolCallData {
  tool_name: string;
  event_data?: any;
}

// 完成事件数据
export interface CompleteData {
  success: boolean;
  execution_time?: number;
}

// 错误事件数据
export interface ErrorData {
  error: string;
  stack_trace?: string;
}
```

### B. 常见问题

**Q: 为什么会收到大量的 `agent_thinking` 事件？**

A: 这是正常的。Agent的流式输出会分成多个小chunk发送，每个chunk都会触发一个 `agent_thinking` 事件。建议在前端进行节流处理。

**Q: 如何判断分析是否真正完成？**

A: 监听 `completed` 事件，该事件只会在整个分析流程成功完成时发送一次。

**Q: SSE连接断开后如何恢复？**

A: 当前实现不支持断点续传。如果连接断开，需要实现重连逻辑，但可能会丢失部分事件。建议：
1. 在服务端保存事件历史
2. 重连后通过 `/api/analysis/{task_id}/events` 获取历史事件

**Q: 如何区分不同轮次的CodeQL生成？**

A: 查看 `agent_start` 事件的 `data.round_index` 字段，它会标识当前是第几轮迭代。

**Q: 前端如何展示多轮CodeQL迭代？**

A: 建议：
1. 为每轮迭代创建单独的输出区域
2. 使用折叠面板显示历史轮次
3. 高亮显示最终成功的查询

---

## 更新日志

- **2025-11-08**: 初始版本，包含所有Agent的SSE事件文档

---

## 联系支持

如有问题或建议，请通过以下方式联系：

- 创建 GitHub Issue
- 查看项目文档: `/docs`
- API测试工具: `/api/docs` (FastAPI Swagger UI)

