{
  "cve": "CVE-2025-54802",
  "sink_info": "基于路径遍历漏洞的Sink点(addcrypted函数中的文件写入操作)查找对应的Source点",
  "candidates": [
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "addcrypted",
      "signature": "def addcrypted()",
      "start_line": 75,
      "end_line": 105,
      "reason": "Sink点所在函数本身接收HTTP POST请求参数，其中package参数直接用于构造文件路径，是主要的用户输入源",
      "confidence": "high"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "add",
      "signature": "def add()",
      "start_line": 50,
      "end_line": 73,
      "reason": "与Sink点相似的HTTP路由处理函数，接收package参数，可能通过相同的数据流路径影响Sink点",
      "confidence": "medium"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "addcrypted2",
      "signature": "def addcrypted2()",
      "start_line": 107,
      "end_line": 140,
      "reason": "另一个加密处理函数，接收package参数，可能通过共享的请求处理逻辑影响Sink点",
      "confidence": "medium"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "flashgot",
      "signature": "def flashgot()",
      "start_line": 142,
      "end_line": 158,
      "reason": "FlashGot扩展相关的处理函数，接收package参数，可能通过扩展调用链影响Sink点",
      "confidence": "low"
    }
  ]
}