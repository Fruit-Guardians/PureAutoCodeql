"""
RAG工具用于处理CodeQL Java标准库文档，增强CodeQL查询生成能力。
"""

import os
import re
from pathlib import Path
from typing import List, Dict, Tuple, Optional
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


class CodeQLRAGTool:
    """CodeQL RAG工具类，用于检索和利用CodeQL Java标准库文档。"""
    
    def __init__(self, codeql_lib_path: Optional[str] = None):
        """
        初始化RAG工具。
        
        Args:
            codeql_lib_path: CodeQL标准库路径，如果为None则使用内置知识库
        """
        self.codeql_lib_path = codeql_lib_path
        self.documents = []
        self.document_metadata = []
        self.vectorizer = None
        self.document_vectors = None
        
        # 初始化内置的CodeQL Java标准库知识
        self._initialize_builtin_knowledge()
        
        # 如果提供了CodeQL库路径，则加载实际文档
        if codeql_lib_path and os.path.exists(codeql_lib_path):
            self._load_codeql_library()
    
    def _initialize_builtin_knowledge(self):
        """初始化内置的CodeQL Java标准库知识。"""
        
        # CodeQL Java标准库的核心概念和API
        builtin_docs = [
            {
                "title": "DataFlow库",
                "content": """
DataFlow库用于跟踪数据在程序中的流动。主要包含：
- DataFlow::Node: 数据流节点
- DataFlow::Configuration: 数据流配置
- DataFlow::PathGraph: 数据流路径图

示例用法：
import java
import semmle.code.java.dataflow.DataFlow

from DataFlow::Node source, DataFlow::Node sink
where DataFlow::localFlow(source, sink)
select source, sink
""",
                "category": "dataflow"
            },
            {
                "title": "TaintTracking库",
                "content": """
TaintTracking库用于污点分析，跟踪不受信任的数据流向敏感操作。

主要API：
- TaintTracking::Configuration: 污点跟踪配置
- TaintTracking::PathGraph: 污点路径图

示例：
import java
import semmle.code.java.dataflow.TaintTracking

from TaintTracking::Configuration cfg, DataFlow::Node source, DataFlow::Node sink
where cfg.hasFlow(source, sink)
select source, sink
""",
                "category": "tainttracking"
            },
            {
                "title": "Security包",
                "content": """
Security包包含常见安全漏洞的检测模式。

主要模块：
- Security::CWE: CWE漏洞分类
- Security::CVSS: CVSS评分

常用查询模式：
import java
import semmle.code.java.security.Security

from VulnerableMethod vm
select vm
""",
                "category": "security"
            },
            {
                "title": "Source和Sink定义",
                "content": """
在CodeQL中定义Source和Sink：

Source（数据源）：
- 用户输入：getParameter(), getQueryString()
- 文件读取：FileInputStream, BufferedReader
- 网络输入：Socket, HttpServletRequest

Sink（敏感操作）：
- SQL执行：PreparedStatement.execute()
- 命令执行：Runtime.exec()
- 文件写入：FileOutputStream, FileWriter

示例Source定义：
class UserInputSource extends DataFlow::Node {
  UserInputSource() {
    exists(MethodAccess ma | 
      ma.getMethod().getName() = "getParameter" and
      ma.getMethod().getDeclaringType().hasQualifiedName("javax.servlet", "HttpServletRequest")
    )
  }
}
""",
                "category": "sourcesink"
            },
            {
                "title": "控制流分析",
                "content": """
控制流分析用于跟踪程序执行路径。

主要API：
- ControlFlow::Node: 控制流节点
- ControlFlow::Block: 基本块

示例：
import java
import semmle.code.java.controlflow.ControlFlow

from ControlFlow::Node node
where node = any(ControlFlow::entryNode())
select node
""",
                "category": "controlflow"
            },
            {
                "title": "类型系统",
                "content": """
CodeQL Java类型系统：

基本类型：
- PrimitiveType: 基本类型（int, boolean等）
- RefType: 引用类型
- Class: 类
- Interface: 接口

类型操作：
- getASupertype(): 获取父类型
- getASubtype(): 获取子类型
- hasQualifiedName(): 检查限定名

示例：
import java

from Class c
where c.hasQualifiedName("java.lang", "String")
select c
""",
                "category": "typesystem"
            }
        ]
        
        for doc in builtin_docs:
            self.documents.append(doc["content"])
            self.document_metadata.append({
                "title": doc["title"],
                "category": doc["category"]
            })
    
    def _load_codeql_library(self):
        """加载实际的CodeQL库文档。"""
        if not self.codeql_lib_path:
            return
            
        try:
            # 遍历CodeQL库目录，查找.qll文件
            for root, dirs, files in os.walk(self.codeql_lib_path):
                for file in files:
                    if file.endswith('.qll'):
                        file_path = Path(root) / file
                        try:
                            content = self._parse_qll_file(file_path)
                            if content:
                                self.documents.append(content)
                                self.document_metadata.append({
                                    "title": file,
                                    "path": str(file_path),
                                    "category": "qll_library"
                                })
                        except Exception as e:
                            print(f"Error parsing {file_path}: {e}")
        except Exception as e:
            print(f"Error loading CodeQL library: {e}")
    
    def _parse_qll_file(self, file_path: Path) -> Optional[str]:
        """解析.qll文件，提取文档和API信息。"""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 提取模块文档（注释）
            doc_pattern = r'/\*\*(.*?)\*/'
            docs = re.findall(doc_pattern, content, re.DOTALL)
            
            # 提取导入语句
            import_pattern = r'import\s+(.*?)'
            imports = re.findall(import_pattern, content)
            
            # 提取类和方法定义
            class_pattern = r'class\s+(\w+)\s*'
            classes = re.findall(class_pattern, content)
            
            # 组合文档信息
            parsed_content = f"文件: {file_path.name}\n"
            
            if docs:
                parsed_content += f"文档: {docs[0].strip()}\n"
            
            if imports:
                parsed_content += f"导入: {', '.join(imports)}\n"
            
            if classes:
                parsed_content += f"类定义: {', '.join(classes)}\n"
            
            # 添加原始内容的前200个字符作为参考
            preview = content[:200].replace('\n', ' ')
            parsed_content += f"内容预览: {preview}..."
            
            return parsed_content
            
        except Exception as e:
            print(f"Error parsing {file_path}: {e}")
            return None
    
    def build_vector_index(self):
        """构建文档向量索引。"""
        if not self.documents:
            return
        
        self.vectorizer = TfidfVectorizer(
            max_features=1000,
            stop_words='english',
            ngram_range=(1, 2)
        )
        
        self.document_vectors = self.vectorizer.fit_transform(self.documents)
    
    def search(self, query: str, top_k: int = 3) -> List[Tuple[str, float, Dict]]:
        """
        搜索与查询相关的文档。
        
        Args:
            query: 查询字符串
            top_k: 返回前k个最相关的结果
            
        Returns:
            包含(文档内容, 相似度分数, 元数据)的列表
        """
        if not self.documents or self.vectorizer is None:
            self.build_vector_index()
        
        if not self.documents:
            return []
        
        # 向量化查询
        query_vector = self.vectorizer.transform([query])
        
        # 计算相似度
        similarities = cosine_similarity(query_vector, self.document_vectors)
        
        # 获取最相关的结果
        top_indices = similarities[0].argsort()[-top_k:][::-1]
        
        results = []
        for idx in top_indices:
            # 返回所有结果，包括相似度为0的
            results.append((
                self.documents[idx],
                float(similarities[0][idx]),
                self.document_metadata[idx]
            ))
        
        return results
    
    def get_relevant_context(self, query: str, max_chars: int = 1000) -> str:
        """
        获取与查询相关的上下文信息。
        
        Args:
            query: 查询字符串
            max_chars: 最大字符数限制
            
        Returns:
            相关的上下文信息
        """
        results = self.search(query)
        
        context_parts = []
        total_chars = 0
        
        for content, score, metadata in results:
            if total_chars + len(content) <= max_chars:
                context_parts.append(f"# {metadata.get('title', 'Unknown')} (相似度: {score:.3f})")
                context_parts.append(content)
                total_chars += len(content)
            else:
                # 如果内容太长，截取部分
                remaining_chars = max_chars - total_chars
                if remaining_chars > 100:  # 至少保留100字符
                    truncated_content = content[:remaining_chars] + "..."
                    context_parts.append(f"# {metadata.get('title', 'Unknown')} (相似度: {score:.3f})")
                    context_parts.append(truncated_content)
                break
        
        return "\n\n".join(context_parts)
    
    def enhance_prompt_with_rag(self, original_prompt: str, query: str) -> str:
        """
        使用RAG增强原始提示词。
        
        Args:
            original_prompt: 原始提示词
            query: 用户查询
            
        Returns:
            增强后的提示词
        """
        # 获取相关上下文
        context = self.get_relevant_context(query)
        
        # 构建增强的提示词
        enhanced_prompt = f"""
基于以下CodeQL Java标准库知识，请生成相应的CodeQL查询：

相关文档上下文：
{context}

用户需求：{query}

请基于上述文档生成准确、符合CodeQL语法的查询。确保：
1. 使用正确的导入语句
2. 遵循CodeQL最佳实践
3. 包含适当的注释说明
4. 输出完整的可执行查询

请直接生成CodeQL代码，用<codeql>标签包裹：
"""
        
        return enhanced_prompt


def create_codeql_rag_tool() -> CodeQLRAGTool:
    """创建CodeQL RAG工具实例。"""
    return CodeQLRAGTool()


if __name__ == "__main__":
    # 测试RAG工具
    rag_tool = create_codeql_rag_tool()
    
    # 测试搜索功能
    test_query = "查找Java中的SQL注入漏洞"
    results = rag_tool.search(test_query)
    
    print(f"查询: {test_query}")
    print("搜索结果:")
    for i, (content, score, metadata) in enumerate(results):
        print(f"{i+1}. {metadata.get('title', 'Unknown')} (分数: {score:.3f})")
        print(f"   内容预览: {content[:100]}...")
        print()
    
    # 测试增强提示词
    enhanced = rag_tool.enhance_prompt_with_rag("生成CodeQL查询", test_query)
    print("增强后的提示词:")
    print(enhanced[:500] + "..." if len(enhanced) > 500 else enhanced)