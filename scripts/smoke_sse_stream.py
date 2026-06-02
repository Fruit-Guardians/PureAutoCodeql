"""手工 SSE 流式接口 smoke 脚本。"""

import requests
import json
import time
from datetime import datetime
from typing import List, Dict


class SSEClient:
    """SSE 客户端，用于接收服务器推送的事件"""

    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.session = requests.Session()

    def start_analysis(self, case_id: str, language: str = "java",
                       requirement: str = "分析所有安全漏洞") -> str:
        """启动分析任务"""
        url = f"{self.base_url}/api/analysis/start"
        payload = {
            "case_id": case_id,
            "language": language,
            "requirement": requirement,
            "max_rounds": 3,
            "enable_cve_analysis": True,
            "enable_sink_analysis": True
        }

        print(f"🚀 启动分析任务: {case_id}")
        print(f"   请求URL: {url}")
        print(f"   配置: {json.dumps(payload, indent=2, ensure_ascii=False)}")

        response = self.session.post(url, json=payload)
        response.raise_for_status()

        result = response.json()
        task_id = result["task_id"]

        print(f"✅ 任务创建成功")
        print(f"   任务ID: {task_id}")
        print(f"   状态: {result['status']}\n")

        return task_id

    def stream_events(self, task_id: str, duration_seconds: int = 20) -> List[Dict]:
        """连接 SSE 流并接收事件"""
        url = f"{self.base_url}/api/analysis/{task_id}/stream"

        print(f"📡 连接 SSE 流: {url}")
        print(f"   监听时长: {duration_seconds} 秒")
        print(f"   开始时间: {datetime.now().strftime('%H:%M:%S')}\n")
        print("=" * 80)

        events = []
        start_time = time.time()
        
        # 用于累积 LLM 流式输出
        llm_buffer = []
        current_llm_step = None
        current_llm_agent = None

        try:
            with self.session.get(url, stream=True, timeout=None) as response:
                response.raise_for_status()

                current_event_type = None

                for line in response.iter_lines(decode_unicode=True):
                    # 检查是否超时
                    elapsed = time.time() - start_time
                    if elapsed >= duration_seconds:
                        # 如果有未输出的 LLM 内容，先输出
                        if llm_buffer:
                            self._flush_llm_output(llm_buffer, current_llm_step, current_llm_agent)
                            llm_buffer = []
                        print(f"\n⏰ 已监听 {duration_seconds} 秒，停止接收事件")
                        break

                    if not line:
                        continue

                    # 解析 SSE 数据
                    if line.startswith('event:'):
                        current_event_type = line.split(':', 1)[1].strip()
                    elif line.startswith('data:'):
                        data_str = line.split(':', 1)[1].strip()
                        try:
                            data = json.loads(data_str)
                            events.append(data)

                            event_type = data.get('type', 'unknown')
                            
                            # 处理 LLM 流式输出（agent_thinking 类型，注意是小写）
                            if event_type == 'agent_thinking':
                                # token 可能在 message 或 data.stream_chunk 中
                                token = data.get('message', '')
                                if not token and data.get('data', {}).get('stream_chunk'):
                                    token = data['data']['stream_chunk']
                                
                                step_name = data.get('step_name', 'N/A')
                                agent_name = data.get('agent_name', 'N/A')
                                
                                # 如果是新的步骤或代理，先输出之前的内容
                                if (current_llm_step != step_name or current_llm_agent != agent_name) and llm_buffer:
                                    self._flush_llm_output(llm_buffer, current_llm_step, current_llm_agent)
                                    llm_buffer = []
                                
                                # 如果是新的 LLM 输出开始，显示标题
                                if not llm_buffer:
                                    print(f"\n{'─' * 80}")
                                    print(f"🤖 LLM 流式输出")
                                    if agent_name and agent_name != 'N/A':
                                        print(f"   代理: {agent_name}")
                                    if step_name and step_name != 'N/A':
                                        print(f"   步骤: {step_name}")
                                    print(f"{'─' * 80}")
                                
                                current_llm_step = step_name
                                current_llm_agent = agent_name
                                llm_buffer.append(token)
                                
                                # 实时显示 token（不换行）
                                print(token, end='', flush=True)
                            
                            # 处理其他事件
                            else:
                                # 如果有累积的 LLM 输出，先输出
                                if llm_buffer:
                                    self._flush_llm_output(llm_buffer, current_llm_step, current_llm_agent)
                                    llm_buffer = []
                                    current_llm_step = None
                                    current_llm_agent = None
                                
                                # 输出普通事件
                                self._print_event(data)

                        except json.JSONDecodeError as e:
                            print(f"⚠️  JSON 解析错误: {e}")
                            print(f"   原始数据: {data_str}")
                    elif line.startswith(':'):
                        # 心跳消息（简化输出）
                        pass  # 不输出心跳，避免干扰

        except requests.exceptions.RequestException as e:
            print(f"\n❌ 请求错误: {e}")
        except KeyboardInterrupt:
            print(f"\n⚠️  用户中断")
        finally:
            # 确保最后的 LLM 输出被刷新
            if llm_buffer:
                self._flush_llm_output(llm_buffer, current_llm_step, current_llm_agent)

        print("=" * 80)
        print(f"\n📊 总共接收到 {len(events)} 个事件")

        return events
    
    def _print_event(self, data: Dict):
        """打印普通事件"""
        timestamp = data.get('timestamp', 'N/A')
        event_type = data.get('type', 'unknown')
        step_name = data.get('step_name', 'N/A')
        agent_name = data.get('agent_name', 'N/A')
        message = data.get('message', '')
        
        # 根据事件类型选择图标（事件类型是小写形式）
        icons = {
            'agent_start': '🚀',
            'agent_complete': '✅',
            'agent_error': '❌',
            'agent_tool_call': '🔧',
            'step_start': '🚀',
            'step_complete': '✅',
            'step_error': '❌',
            'error': '❌',
            'progress': '⏳',
            'info': 'ℹ️',
            'warning': '⚠️',
            'codeql_execution': '🔍',
            'analysis_result': '📊',
        }
        icon = icons.get(event_type, '📝')
        
        print(f"\n{icon} [{timestamp}] {event_type.upper()}")
        if agent_name != 'N/A':
            print(f"   代理: {agent_name}")
        print(f"   步骤: {step_name}")
        if message:
            print(f"   消息: {message}")
        
        # 如果有额外数据，也打印出来
        if data.get('data'):
            data_content = data['data']
            # 如果数据较小，直接显示；如果较大，简化显示
            data_str = json.dumps(data_content, indent=2, ensure_ascii=False)
            if len(data_str) > 500:
                print(f"   数据: <{len(data_str)} 字节>")
            else:
                print(f"   数据: {data_str}")
        
        print("-" * 80)
    
    def _flush_llm_output(self, buffer: List[str], step_name: str, agent_name: str):
        """输出累积的 LLM 内容"""
        if not buffer:
            return
        
        full_text = ''.join(buffer)
        print(f"\n\n{'─' * 80}")
        print(f"🤖 LLM 输出完成")
        if agent_name:
            print(f"   代理: {agent_name}")
        if step_name:
            print(f"   步骤: {step_name}")
        print(f"   长度: {len(full_text)} 字符")
        print(f"{'─' * 80}\n")

    def cancel_task(self, task_id: str):
        """取消任务"""
        url = f"{self.base_url}/api/analysis/{task_id}"

        print(f"\n🛑 取消任务: {task_id}")

        try:
            response = self.session.delete(url)
            response.raise_for_status()
            result = response.json()
            print(f"✅ {result['message']}")
        except requests.exceptions.RequestException as e:
            print(f"❌ 取消任务失败: {e}")

    def get_task_status(self, task_id: str):
        """获取任务状态"""
        url = f"{self.base_url}/api/analysis/{task_id}/status"

        try:
            response = self.session.get(url)
            response.raise_for_status()
            status = response.json()

            print(f"\n📋 任务状态:")
            print(f"   ID: {status['task_id']}")
            print(f"   案例: {status['case_id']}")
            print(f"   状态: {status['status']}")
            print(f"   创建时间: {status.get('created_at', 'N/A')}")
            print(f"   更新时间: {status.get('updated_at', 'N/A')}")

            if status.get('error'):
                print(f"   错误: {status['error']}")

            return status
        except requests.exceptions.RequestException as e:
            print(f"❌ 获取状态失败: {e}")
            return None


def main():
    """主测试流程"""
    print("\n" + "=" * 80)
    print("SSE 流式接口测试".center(80))
    print("=" * 80 + "\n")

    # 初始化客户端
    client = SSEClient(base_url="http://localhost:8000")

    # 测试参数
    case_id = "CVE-2021-21985"
    stream_duration = 120  # 监听 20 秒

    try:
        # 1. 启动分析任务
        task_id = client.start_analysis(
            case_id=case_id,
            language="java",
            requirement="分析 CVE-2021-21985 的安全漏洞，重点关注远程代码执行和反序列化漏洞"
        )

        # 2. 连接 SSE 流并接收事件（20秒）
        events = client.stream_events(task_id, duration_seconds=stream_duration)

        # 3. 输出统计信息
        print(f"\n📈 事件统计:")
        event_types = {}
        for event in events:
            event_type = event.get('type', 'unknown')
            event_types[event_type] = event_types.get(event_type, 0) + 1

        for event_type, count in event_types.items():
            print(f"   {event_type}: {count} 次")

        # 4. 获取当前任务状态
        client.get_task_status(task_id)

        # 5. 停止任务
        client.cancel_task(task_id)

        # 6. 再次查看状态确认
        time.sleep(1)
        client.get_task_status(task_id)

        print("\n" + "=" * 80)
        print("测试完成".center(80))
        print("=" * 80 + "\n")

    except Exception as e:
        print(f"\n❌ 测试过程中出错: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
