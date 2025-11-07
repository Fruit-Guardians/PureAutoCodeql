# SSE客户端使用示例

## Python客户端示例

### 使用httpx库（推荐）

```python
import httpx
import json
import asyncio

async def stream_analysis_output(task_id: str):
    """订阅分析任务的SSE流式输出"""
    url = f"http://localhost:8000/api/analysis/{task_id}/stream"
    
    async with httpx.AsyncClient() as client:
        async with client.stream("GET", url) as response:
            if response.status_code != 200:
                print(f"错误: {response.status_code}")
                return
            
            async for line in response.aiter_lines():
                # SSE格式: event: 和 data: 行
                if line.startswith("event:"):
                    event_type = line[6:].strip()
                elif line.startswith("data:"):
                    data = json.loads(line[5:].strip())
                    
                    # 根据事件类型处理
                    if event_type == "step_start":
                        print(f"📌 开始步骤: {data.get('step_name')}")
                    elif event_type == "agent_action":
                        print(f"🤔 {data.get('message')}")
                    elif event_type == "tool_start":
                        print(f"🔧 {data.get('message')}")
                    elif event_type == "tool_output":
                        print(f"✅ {data.get('message')}")
                    elif event_type == "agent_output":
                        print(data.get('message'), end='', flush=True)
                    elif event_type == "step_complete":
                        print(f"✓ 完成步骤: {data.get('step_name')}")
                    elif event_type == "error":
                        print(f"❌ 错误: {data.get('message')}")
                    elif event_type == "completed":
                        print(f"🎉 任务完成")
                        break

# 使用示例
async def main():
    # 1. 启动分析任务
    async with httpx.AsyncClient() as client:
        response = await client.post(
            "http://localhost:8000/api/analysis/start",
            json={
                "case_id": "CVE-2021-21985",
                "language": "java",
                "max_rounds": 5,
                "enable_cve_analysis": True,
                "enable_sink_analysis": True
            }
        )
        data = response.json()
        task_id = data["task_id"]
        print(f"任务已启动: {task_id}")
    
    # 2. 订阅SSE流式输出
    await stream_analysis_output(task_id)
    
    # 3. 获取最终结果
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"http://localhost:8000/api/analysis/{task_id}/result"
        )
        result = response.json()
        print(f"分析结果: {result}")

if __name__ == "__main__":
    asyncio.run(main())
```

### 使用requests库（同步）

```python
import requests
import json

def stream_analysis_output(task_id: str):
    """订阅分析任务的SSE流式输出（同步版本）"""
    url = f"http://localhost:8000/api/analysis/{task_id}/stream"
    
    with requests.get(url, stream=True) as response:
        if response.status_code != 200:
            print(f"错误: {response.status_code}")
            return
        
        event_type = None
        for line in response.iter_lines():
            if not line:
                continue
            
            line = line.decode('utf-8')
            
            if line.startswith("event:"):
                event_type = line[6:].strip()
            elif line.startswith("data:"):
                data = json.loads(line[5:].strip())
                
                # 处理事件
                if event_type == "agent_output":
                    print(data.get('message'), end='', flush=True)
                elif event_type == "completed":
                    print(f"\n🎉 任务完成")
                    break
                else:
                    print(f"[{event_type}] {data.get('message')}")

# 使用示例
def main():
    # 1. 启动任务
    response = requests.post(
        "http://localhost:8000/api/analysis/start",
        json={"case_id": "CVE-2021-21985"}
    )
    task_id = response.json()["task_id"]
    print(f"任务已启动: {task_id}")
    
    # 2. 订阅输出
    stream_analysis_output(task_id)

if __name__ == "__main__":
    main()
```

## JavaScript客户端示例

### 使用Fetch API

```javascript
async function startAnalysis() {
  // 1. 启动分析任务
  const response = await fetch('http://localhost:8000/api/analysis/start', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({
      case_id: 'CVE-2021-21985',
      language: 'java',
      max_rounds: 5,
      enable_cve_analysis: true,
      enable_sink_analysis: true
    })
  });
  
  const data = await response.json();
  const taskId = data.task_id;
  console.log(`任务已启动: ${taskId}`);
  
  // 2. 订阅SSE流式输出
  await streamAnalysisOutput(taskId);
  
  // 3. 获取最终结果
  const resultResponse = await fetch(
    `http://localhost:8000/api/analysis/${taskId}/result`
  );
  const result = await resultResponse.json();
  console.log('分析结果:', result);
}

async function streamAnalysisOutput(taskId) {
  const url = `http://localhost:8000/api/analysis/${taskId}/stream`;
  const response = await fetch(url);
  
  if (!response.ok) {
    console.error(`错误: ${response.status}`);
    return;
  }
  
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  
  let eventType = null;
  let buffer = '';
  
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    
    buffer += decoder.decode(value, {stream: true});
    const lines = buffer.split('\n');
    buffer = lines.pop(); // 保留最后一个不完整的行
    
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        const data = JSON.parse(line.substring(5).trim());
        
        // 根据事件类型处理
        switch (eventType) {
          case 'step_start':
            console.log(`📌 开始步骤: ${data.step_name}`);
            break;
          case 'agent_action':
            console.log(`🤔 ${data.message}`);
            break;
          case 'tool_start':
            console.log(`🔧 ${data.message}`);
            break;
          case 'agent_output':
            process.stdout.write(data.message);
            break;
          case 'step_complete':
            console.log(`✓ 完成步骤: ${data.step_name}`);
            break;
          case 'error':
            console.error(`❌ 错误: ${data.message}`);
            break;
          case 'completed':
            console.log('🎉 任务完成');
            return;
        }
      }
    }
  }
}

// 使用
startAnalysis().catch(console.error);
```

### 使用EventSource（浏览器环境）

```javascript
function streamAnalysisOutput(taskId) {
  const url = `http://localhost:8000/api/analysis/${taskId}/stream`;
  const eventSource = new EventSource(url);
  
  // 监听各种事件类型
  eventSource.addEventListener('step_start', (e) => {
    const data = JSON.parse(e.data);
    console.log(`📌 开始步骤: ${data.step_name}`);
  });
  
  eventSource.addEventListener('agent_action', (e) => {
    const data = JSON.parse(e.data);
    console.log(`🤔 ${data.message}`);
  });
  
  eventSource.addEventListener('tool_start', (e) => {
    const data = JSON.parse(e.data);
    console.log(`🔧 ${data.message}`);
  });
  
  eventSource.addEventListener('agent_output', (e) => {
    const data = JSON.parse(e.data);
    document.getElementById('output').textContent += data.message;
  });
  
  eventSource.addEventListener('completed', (e) => {
    console.log('🎉 任务完成');
    eventSource.close();
  });
  
  eventSource.addEventListener('error', (e) => {
    console.error('SSE连接错误', e);
    eventSource.close();
  });
}

// 使用示例
async function startAnalysisWithUI() {
  // 启动任务
  const response = await fetch('http://localhost:8000/api/analysis/start', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({case_id: 'CVE-2021-21985'})
  });
  
  const {task_id} = await response.json();
  document.getElementById('task-id').textContent = task_id;
  
  // 订阅输出
  streamAnalysisOutput(task_id);
}
```

## cURL示例（调试用）

```bash
# 启动任务
TASK_ID=$(curl -X POST http://localhost:8000/api/analysis/start \
  -H "Content-Type: application/json" \
  -d '{
    "case_id": "CVE-2021-21985",
    "language": "java",
    "max_rounds": 5
  }' | jq -r '.task_id')

echo "任务ID: $TASK_ID"

# 订阅SSE流式输出
curl -N http://localhost:8000/api/analysis/$TASK_ID/stream

# 获取最终结果
curl http://localhost:8000/api/analysis/$TASK_ID/result
```

## 错误处理示例

```python
import httpx
import asyncio

async def stream_with_retry(task_id: str, max_retries: int = 3):
    """带重试机制的SSE订阅"""
    url = f"http://localhost:8000/api/analysis/{task_id}/stream"
    
    for attempt in range(max_retries):
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                async with client.stream("GET", url) as response:
                    if response.status_code == 404:
                        print(f"任务不存在: {task_id}")
                        return
                    elif response.status_code == 410:
                        print(f"任务已结束: {task_id}")
                        return
                    elif response.status_code != 200:
                        raise Exception(f"HTTP {response.status_code}")
                    
                    async for line in response.aiter_lines():
                        # 处理事件...
                        pass
                    
                    # 正常完成
                    return
                    
        except (httpx.TimeoutException, httpx.NetworkError) as e:
            print(f"连接错误 (尝试 {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                await asyncio.sleep(2 ** attempt)  # 指数退避
            else:
                print("达到最大重试次数")
                raise
```

## 注意事项

1. **立即订阅**: 启动任务后应立即订阅SSE，否则可能错过早期事件
2. **连接保持**: SSE连接会持续到任务完成，注意设置适当的超时时间
3. **错误处理**: 处理404（任务不存在）和410（任务已结束）错误
4. **重连机制**: 网络不稳定时实现自动重连
5. **CORS**: 跨域请求需要服务器配置CORS头（已在设计中包含）

