{
  "cve": "CVE-2025-54802",
  "sink_info": "基于路径遍历导致的任意文件写入漏洞，Sink点在cnl_blueprint.py的addcrypted()函数中的文件写入操作",
  "candidates": [
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "addcrypted",
      "signature": "def addcrypted()",
      "start_line": 86,
      "end_line": 92,
      "reason": "该函数直接处理HTTP POST请求中的package参数，该参数通过flask.request.form.get()获取，是路径遍历攻击的直接输入源",
      "confidence": "high"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "add",
      "signature": "def add()",
      "start_line": 56,
      "end_line": 62,
      "reason": "该函数同样处理HTTP POST请求中的package参数，虽然不直接涉及文件写入，但使用相同的输入源机制，可能在其他路径上存在类似风险",
      "confidence": "medium"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "addcrypted2",
      "signature": "def addcrypted2()",
      "start_line": 118,
      "end_line": 124,
      "reason": "该函数也处理HTTP POST请求中的package参数，使用相同的输入获取方式，可能在其他代码路径上存在类似漏洞",
      "confidence": "medium"
    },
    {
      "file_path": "src/pyload/webui/app/blueprints/cnl_blueprint.py",
      "function_name": "flashgot",
      "signature": "def flashgot()",
      "start_line": 154,
      "end_line": 160,
      "reason": "该函数处理HTTP POST请求中的package参数，虽然不涉及文件写入，但使用相同的用户输入机制",
      "confidence": "low"
    }
  ]
}