"""
在 Agent 中使用额外输入文件的示例

演示如何在自定义 Agent 中集成和使用额外输入文件。
"""

from typing import Any
from pure_auto_codeql.core.context import AnalysisContext
from pure_auto_codeql.core.pipeline import AnalysisStep


class EnhancedCVEAnalysisStep(AnalysisStep):
    """增强的 CVE 分析步骤，使用额外输入文件"""
    
    def __init__(self):
        super().__init__("enhanced_cve_analysis")
    
    async def execute(self, context: AnalysisContext) -> Any:
        """执行增强的 CVE 分析"""
        print(f"\n🔍 开始增强型 CVE 分析: {context.cve_assets.cve_id}\n")
        
        # 1. 构建基础分析提示
        prompt_parts = []
        prompt_parts.append(f"# CVE 漏洞分析: {context.cve_assets.cve_id}\n")
        
        # 2. 添加额外上下文信息
        extra_context = self._build_extra_context(context)
        if extra_context:
            prompt_parts.append(extra_context)
        
        # 3. 组合完整提示
        full_prompt = "\n".join(prompt_parts)
        
        print("📝 生成的分析提示词:")
        print("=" * 80)
        print(full_prompt[:500] + "..." if len(full_prompt) > 500 else full_prompt)
        print("=" * 80)
        
        # 实际使用时，这里会调用 LLM
        # result = await llm_service.analyze(full_prompt)
        
        return {
            "success": True,
            "prompt_length": len(full_prompt),
            "has_extra_files": context.cve_assets.has_extra_files(),
            "extra_files_count": len(context.cve_assets.extra_files)
        }
    
    def _build_extra_context(self, context: AnalysisContext) -> str:
        """构建额外上下文信息"""
        if not context.cve_assets.has_extra_files():
            return ""
        
        sections = ["\n## 额外上下文信息\n"]
        
        # 添加系统架构信息
        context_files = context.cve_assets.get_extra_files_by_category('context')
        if context_files:
            sections.append("### 系统架构与背景")
            for f in context_files:
                sections.append(f"\n**文件: {f.path.name}**\n")
                sections.append(f.read_text())
        
        # 添加配置信息
        config_files = context.cve_assets.get_extra_files_by_category('config')
        if config_files:
            sections.append("\n### 配置与版本信息")
            for f in config_files:
                sections.append(f"\n**文件: {f.path.name}**\n")
                if f.file_type == 'json':
                    try:
                        import json
                        data = f.read_json()
                        sections.append("```json")
                        sections.append(json.dumps(data, indent=2, ensure_ascii=False))
                        sections.append("```")
                    except:
                        sections.append(f.read_text())
                else:
                    sections.append(f.read_text())
        
        # 添加文档
        doc_files = context.cve_assets.get_extra_files_by_category('doc')
        if doc_files:
            sections.append("\n### 相关文档")
            for f in doc_files:
                sections.append(f"\n**文件: {f.path.name}**\n")
                sections.append(f.read_text())
        
        # 添加分析笔记
        note_files = context.cve_assets.get_extra_files_by_category('note')
        if note_files:
            sections.append("\n### 分析笔记")
            for f in note_files:
                sections.append(f"\n**文件: {f.path.name}**\n")
                sections.append(f.read_text())
        
        # 添加漏洞利用相关信息
        exploit_files = context.cve_assets.get_extra_files_by_category('exploit')
        if exploit_files:
            sections.append("\n### 漏洞利用信息")
            for f in exploit_files:
                sections.append(f"\n**文件: {f.path.name}**\n")
                sections.append(f.read_text())
        
        return "\n".join(sections)


class SmartSourceAnalysisStep(AnalysisStep):
    """智能 Source 分析步骤，利用额外文件优化分析"""
    
    def __init__(self):
        super().__init__("smart_source_analysis")
    
    async def execute(self, context: AnalysisContext) -> Any:
        """执行智能 Source 分析"""
        print(f"\n🎯 开始智能 Source 分析\n")
        
        # 检查是否有配置文件提供的版本信息
        version_info = self._extract_version_info(context)
        
        # 检查是否有架构信息
        arch_info = self._extract_architecture_info(context)
        
        # 检查是否有 POC/Exploit 信息
        exploit_info = self._extract_exploit_info(context)
        
        print("📊 提取的额外信息:")
        if version_info:
            print(f"  ✅ 版本信息: {version_info}")
        if arch_info:
            print(f"  ✅ 架构信息: {arch_info}")
        if exploit_info:
            print(f"  ✅ 利用信息: {exploit_info}")
        
        # 基于额外信息优化分析策略
        strategy = self._determine_analysis_strategy(
            version_info, arch_info, exploit_info
        )
        
        print(f"\n🔧 分析策略: {strategy}")
        
        return {
            "success": True,
            "strategy": strategy,
            "version_info": version_info,
            "architecture_info": arch_info,
            "exploit_info": exploit_info
        }
    
    def _extract_version_info(self, context: AnalysisContext) -> dict:
        """从配置文件提取版本信息"""
        version_info = {}
        
        config_files = context.cve_assets.get_extra_files_by_category('config')
        for f in config_files:
            if f.file_type == 'json':
                try:
                    data = f.read_json()
                    # 查找版本相关字段
                    for key, value in data.items():
                        if 'version' in key.lower() or key.lower() in ['java', 'python', 'node']:
                            version_info[key] = value
                except:
                    pass
        
        return version_info
    
    def _extract_architecture_info(self, context: AnalysisContext) -> str:
        """从上下文文件提取架构信息"""
        context_files = context.cve_assets.get_extra_files_by_category('context')
        
        for f in context_files:
            if 'architecture' in f.path.name.lower() or 'arch' in f.path.name.lower():
                try:
                    content = f.read_text()
                    # 提取前 200 字符作为概要
                    return content[:200].strip() + "..."
                except:
                    pass
        
        return ""
    
    def _extract_exploit_info(self, context: AnalysisContext) -> str:
        """从 exploit 文件提取利用信息"""
        exploit_files = context.cve_assets.get_extra_files_by_category('exploit')
        
        if exploit_files:
            summaries = []
            for f in exploit_files:
                try:
                    content = f.read_text()
                    # 提取前 150 字符
                    summaries.append(f"{f.path.name}: {content[:150].strip()}...")
                except:
                    pass
            return " | ".join(summaries)
        
        return ""
    
    def _determine_analysis_strategy(
        self, 
        version_info: dict, 
        arch_info: str, 
        exploit_info: str
    ) -> str:
        """根据额外信息确定分析策略"""
        if exploit_info:
            return "guided_by_exploit"  # 有 POC，可以针对性分析
        elif arch_info:
            return "architecture_aware"  # 有架构信息，考虑架构特点
        elif version_info:
            return "version_specific"  # 有版本信息，针对特定版本
        else:
            return "general"  # 通用分析


async def demo_enhanced_analysis():
    """演示增强分析流程"""
    from pure_auto_codeql.utils.case import resolve_case, discover_cve_assets
    from pure_auto_codeql.core.context import AnalysisContext
    
    print("\n" + "=" * 80)
    print("增强型 Agent 演示 - 使用额外输入文件")
    print("=" * 80)
    
    # 1. 准备上下文
    case_id = "CVE-2024-7099"  # 替换为您的案例 ID
    case_paths = resolve_case(case_id)
    cve_assets = discover_cve_assets(case_paths)
    
    context = AnalysisContext(
        case_id=case_id,
        case_paths=case_paths,
        cve_assets=cve_assets,
        language="python",  # 示例
    )
    
    # 2. 执行增强的 CVE 分析
    enhanced_step = EnhancedCVEAnalysisStep()
    result1 = await enhanced_step.execute(context)
    print(f"\n✅ CVE 分析完成: {result1}")
    
    # 3. 执行智能 Source 分析
    smart_step = SmartSourceAnalysisStep()
    result2 = await smart_step.execute(context)
    print(f"\n✅ Source 分析完成: {result2}")
    
    print("\n" + "=" * 80)
    print("演示完成！")
    print("=" * 80)


if __name__ == "__main__":
    import asyncio
    
    try:
        asyncio.run(demo_enhanced_analysis())
    except FileNotFoundError as e:
        print(f"\n❌ 错误: {e}")
        print("请确保案例目录存在并包含必要的文件")
    except Exception as e:
        print(f"\n❌ 发生错误: {e}")
        import traceback
        traceback.print_exc()

