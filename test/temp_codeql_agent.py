"""
临时 LangChain Agent - 调用 CodeQLComposeTool 的 run 模式
用于分析 CVE-2021-21985 数据库中的安全漏洞
"""

from pathlib import Path
from typing import Optional

from langchain_core.tools import tool
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from pure_auto_codeql.configuration import get_chat_config
from tools.codeql_compose import CodeQLComposeTool


# ============================================================================
# 工具定义
# ============================================================================

def create_codeql_tool_instance():
    """创建并配置 CodeQLComposeTool 实例"""
    import asyncio
    from Analyze import MultiAgentAnalyzer
    
    # CVE-2021-21985 数据库路径
    database_path = str(Path("projects/CVE-2021-21985/db").resolve())
    
    # 创建并初始化分析器
    analyzer = MultiAgentAnalyzer()
    
    # 同步初始化分析器
    try:
        loop = asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    
    loop.run_until_complete(analyzer.initialize())
    
    # 创建 CodeQLComposeTool 实例并配置
    codeql_tool = CodeQLComposeTool(
        analyzer=analyzer,
        database_path=database_path,
        language="java",
    )
    
    return codeql_tool


# 全局 CodeQL 工具实例
_codeql_tool_instance = None


def get_codeql_tool():
    """获取全局 CodeQL 工具实例"""
    global _codeql_tool_instance
    if _codeql_tool_instance is None:
        _codeql_tool_instance = create_codeql_tool_instance()
    return _codeql_tool_instance


@tool
def analyze_codeql_database(
    requirement: str,
    language: str = "java",
) -> str:
    """
    使用 CodeQL 分析数据库中的安全漏洞。
    
    Args:
        requirement: 分析需求描述，例如 "查找所有远程代码执行漏洞"
        language: 编程语言，默认 java
        
    Returns:
        分析结果，包含查询代码和执行结果
    """
    import asyncio
    
    # 获取配置好的 CodeQL 工具
    codeql_tool = get_codeql_tool()
    
    # 调用 run 模式执行查询（在事件循环中运行异步函数）
    try:
        loop = asyncio.get_event_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    
    result = loop.run_until_complete(
        codeql_tool._arun(
            requirement=requirement,
            exec_mode="run",  # 使用 run 模式获取文本结果
            show_thinking=False,
        )
    )
    
    return result


# ============================================================================
# Agent 配置
# ============================================================================

def create_codeql_agent():
    """创建 CodeQL 分析 Agent"""
    
    # 获取 DeepSeek 配置
    config = get_chat_config()
    
    # 初始化 LLM
    llm = ChatOpenAI(
        model=config.model,
        api_key=config.api_key,
        base_url=config.base_url,
        temperature=config.temperature,
        max_retries=config.max_retries,
    )
    
    # 定义工具列表
    tools = [analyze_codeql_database]
    
    # 系统提示词
    system_prompt = """你是一个安全分析专家，专门分析代码中的安全漏洞。

你的任务是：
1. 理解用户的安全分析需求
2. 使用 CodeQL 工具分析 CVE-2021-21985 数据库
3. 解释分析结果和发现的漏洞

分析时请：
- 明确指定编程语言（java/python/c 等）
- 提供清晰的分析需求描述
- 解释发现的安全问题
- 建议修复方案

可用的工具：
- analyze_codeql_database: 使用 CodeQL 查询分析数据库

"""
    
    # 将工具转换为 OpenAI 工具格式
    llm_with_tools = llm.bind_tools(tools)
    
    return llm_with_tools, system_prompt, tools


# ============================================================================
# 主函数
# ============================================================================

def run_agent_loop(llm_with_tools, system_prompt, tools, query, max_iterations=5):
    """运行 Agent 循环，处理工具调用 - 支持流式输出"""
    from langchain_core.messages import HumanMessage, SystemMessage, AIMessage, ToolMessage
    
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=query),
    ]
    
    # 创建工具映射
    tool_map = {tool.name: tool for tool in tools}
    
    for iteration in range(max_iterations):
        print(f"\n[ITERATION {iteration + 1}] 调用 LLM...")
        
        # 流式调用 LLM
        full_response = None
        for chunk in llm_with_tools.stream(messages):
            full_response = chunk
            # 实时显示 LLM 的流式输出
            if hasattr(chunk, "content") and chunk.content:
                print(chunk.content, end="", flush=True)
        
        print()  # 换行
        
        if full_response is None:
            return "LLM 返回空响应"
        
        messages.append(full_response)
        
        # 检查是否有工具调用
        if not hasattr(full_response, "tool_calls") or not full_response.tool_calls:
            # 没有工具调用，返回最终响应
            return full_response.content if hasattr(full_response, "content") else str(full_response)
        
        # 处理工具调用
        for tool_call in full_response.tool_calls:
            tool_name = tool_call["name"]
            tool_input = tool_call["args"]
            
            print(f"\n[TOOL] 调用工具: {tool_name}")
            print(f"       参数: {tool_input}")
            print(f"       执行中...", end="", flush=True)
            
            # 执行工具
            if tool_name in tool_map:
                try:
                    tool_result = tool_map[tool_name].invoke(tool_input)
                    print(" 完成")
                    
                    # 显示结果预览
                    result_str = str(tool_result)
                    if len(result_str) > 500:
                        print(f"       结果预览: {result_str[:500]}...")
                    else:
                        print(f"       结果: {result_str}")
                    
                    # 添加工具结果到消息
                    messages.append(
                        ToolMessage(
                            content=tool_result,
                            tool_call_id=tool_call["id"],
                        )
                    )
                except Exception as e:
                    print(f" 失败")
                    print(f"       [ERROR] {str(e)}")
                    messages.append(
                        ToolMessage(
                            content=f"工具执行失败: {str(e)}",
                            tool_call_id=tool_call["id"],
                        )
                    )
            else:
                print(f" 失败")
                print(f"       [ERROR] 未知工具: {tool_name}")
    
    return "达到最大迭代次数"


def main():
    """主函数 - 演示如何使用 Agent"""
    
    print("=" * 80)
    print("CodeQL 安全分析 Agent")
    print("=" * 80)
    print()
    
    # 创建 Agent
    llm_with_tools, system_prompt, tools = create_codeql_agent()
    
    # 示例分析需求
    analysis_queries = [
        """
        请根据以下内容查找到该CVE的sink点，注意给出具体的codeql查询请求，不能过于宽泛，最后根据请求给出sink的类以及sink的函数名
正在分析案例: CVE-2021-21985
案例根目录: C:\Projects\PureAutoCodeql\projects\CVE-2021-21985
🔍 启用AI思考过程显示模式
🎯 分析CVE: CVE-2021-21985
📁 JSON文件: C:\Projects\PureAutoCodeql\projects\CVE-2021-21985\inputs\CVE-2021-21985.json (本地)
📄 Diff文件: C:\Projects\PureAutoCodeql\projects\CVE-2021-21985\inputs\CVE-2021-21985.diff (本地)
正在收集漏洞情报...
✓ 使用缓存的情报数据
检测到语言: java
☕ 使用Java分析器...
=== CVE Analysis ===
### 漏洞类型
远程代码执行

### 技术细节
- 漏洞位置：vSphere Client (HTML5) 中的 Virtual SAN Health Check 插件
- 具体成因：由于 Virtual SAN Health Check 插件中缺乏输入验证，导致远程代码执行漏洞。该插件在 vCenter Server 中默认启用。

diff文件如下：package com.vmware.vsan.client.services;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.vmware.vim.binding.vmodl.LocalizableMessage;
import com.vmware.vim.binding.vmodl.MethodFault;
import com.vmware.vim.binding.vmodl.RuntimeFault;
import com.vmware.vim.vmomi.client.common.UnexpectedStatusCodeException;
import com.vmware.vise.data.query.DataException;
import com.vmware.vsphere.client.vsan.util.MessageBundle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping({"/proxy"})
public class ProxygenController extends RestControllerBase {
   private static final Logger logger = LoggerFactory.getLogger(ProxygenController.class);
   @Autowired
   private BeanFactory beanFactory;
   @Autowired
   private MessageBundle messages;

   public ProxygenController() {
   }

   @RequestMapping(
      value = {"/service/{beanIdOrClassName}/{methodName}"},
      method = {RequestMethod.POST},
      consumes = {"application/json"},
      produces = {"application/json"}
   )
   @ResponseBody
   public Object invokeServiceWithJson(@PathVariable("beanIdOrClassName") String beanIdOrClassName, @PathVariable("methodName") String methodName, @RequestBody Map<String, Object> body) throws Exception {
      List<Object> rawData = null;

      try {
         rawData = (List)body.get("methodInput");
      } catch (Exception var6) {
         Exception e = var6;
         logger.error("service method failed to extract input data", e);
         return this.handleException(e);
      }

      return this.invokeService(beanIdOrClassName, methodName, (MultipartFile[])null, rawData);
   }

   @RequestMapping(
      value = {"/service/{beanIdOrClassName}/{methodName}"},
      method = {RequestMethod.POST},
      consumes = {"multipart/form-data"},
      produces = {"application/json"}
   )
   @ResponseBody
   public Object invokeServiceWithMultipartFormData(@PathVariable("beanIdOrClassName") String beanIdOrClassName, @PathVariable("methodName") String methodName, @RequestParam("file") MultipartFile[] files, @RequestParam("methodInput") String rawData) throws Exception {
      List<Object> data = null;

      try {
         Gson gson = new Gson();
         data = (List)gson.fromJson(rawData, List.class);
      } catch (Exception var7) {
         Exception e = var7;
         logger.error("service method failed to extract input data", e);
         return this.handleException(e);
      }

      return this.invokeService(beanIdOrClassName, methodName, files, data);
   }

   private Object invokeService(String beanIdOrClassName, String methodName, MultipartFile[] files, List<Object> data) throws Exception {
      Exception e;
      try {
         e = null;
         String beanName = null;
         Class<?> beanClass = null;

         try {
            beanClass = Class.forName(beanIdOrClassName);
            beanName = StringUtils.uncapitalize(beanClass.getSimpleName());
         } catch (ClassNotFoundException var17) {
            beanName = beanIdOrClassName;
         }

         Object bean;
         try {
            bean = this.beanFactory.getBean(beanName);
         } catch (BeansException var16) {
            bean = this.beanFactory.getBean(beanClass);
         }

         Method[] var11;
         int var10 = (var11 = bean.getClass().getMethods()).length;

         for(int var9 = 0; var9 < var10; ++var9) {
            Method method = var11[var9];
-            if (method.getName().equals(methodName)) {
+            if (method.getName().equals(methodName) || !method.isAnnotationPresent((Class)TsService.class)) {
               ProxygenSerializer serializer = new ProxygenSerializer();
               Object[] methodInput = serializer.deserializeMethodInput(data, files, method);
               Object result = method.invoke(bean, methodInput);
               Map<String, Object> map = new HashMap();
               map.put("result", serializer.serialize(result));
               return map;
            }
         }
      } catch (Exception var18) {
         e = var18;
         logger.error("service method failed to invoke", e);
         return this.handleException(e);
      }

      logger.error("service method not found: " + methodName + " @ " + beanIdOrClassName);
      return this.handleException((Throwable)null);
   }

   private Object handleException(Throwable t) {
      if (t instanceof InvocationTargetException) {
         return this.handleException(((InvocationTargetException)t).getTargetException());
      } else if (t instanceof ExecutionException && t.getCause() != t) {
         return this.handleException(t.getCause());
      } else if (t instanceof DataException && t.getCause() != t) {
         return this.handleException(t.getCause());
      } else if (t instanceof UnexpectedStatusCodeException) {
         return ImmutableMap.of("error", this.messages.string("util.dataservice.notRespondingFault"));
      } else if (t instanceof VsanUiLocalizableException) {
         VsanUiLocalizableException localizableException = (VsanUiLocalizableException)t;
         return ImmutableMap.of("error", this.messages.string(localizableException.getErrorKey(), localizableException.getParams()));
      } else {
         LocalizableMessage[] faultMessage = null;
         String vmodlMessage = null;
         if (t instanceof MethodFault) {
            faultMessage = ((MethodFault)t).getFaultMessage();
            vmodlMessage = ((MethodFault)t).getMessage();
         } else if (t instanceof RuntimeFault) {
            faultMessage = ((RuntimeFault)t).getFaultMessage();
            vmodlMessage = ((RuntimeFault)t).getMessage();
         }

         if (faultMessage != null) {
            LocalizableMessage[] var7 = faultMessage;
            int var6 = faultMessage.length;

            for(int var5 = 0; var5 < var6; ++var5) {
               LocalizableMessage localizable = var7[var5];
               if (localizable.getMessage() != null && !localizable.getMessage().isEmpty()) {
                  return ImmutableMap.of("error", this.localizeFault(localizable.getMessage()));
               }

               if (localizable.getKey() != null && !localizable.getKey().isEmpty()) {
                  return ImmutableMap.of("error", this.localizeFault(localizable.getKey()));
               }
            }
         }

         return StringUtils.isNotBlank(vmodlMessage) ? ImmutableMap.of("error", vmodlMessage) : ImmutableMap.of("error", this.messages.string("vsan.common.generic.error"));
      }
   }

   private String localizeFault(String key) {
      return key;
   }
}

        """
    ]
    
    # 执行分析
    for query in analysis_queries:
        print(f"\n【分析需求】: {query}")
        print("-" * 80)
        
        try:
            # 运行 Agent 循环
            result = run_agent_loop(llm_with_tools, system_prompt, tools, query)
            
            print(f"\n【最终结果】:")
            print(result)
            
        except Exception as e:
            print(f"[ERROR] 分析失败: {str(e)}")
            import traceback
            traceback.print_exc()
        
        print("\n" + "=" * 80)


if __name__ == "__main__":
    # 运行 Agent
    main()
