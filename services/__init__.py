"""Legacy services package.

Implementation lives under ``pure_auto_codeql.services``. This top-level package
re-exports the public API for compatibility.
"""

from pure_auto_codeql.services import (
    AgentResult,
    CodeQLExecutionResult,
    CodeQLExecutionService,
    CodeQLLSPService,
    CodeQLSyntaxSession,
    KnowledgeBaseFactory,
    LanguageDetector,
    MultiAgentAnalyzer,
    PythonKnowledgeBase,
    apply_placeholders,
    build_placeholder_map,
)

__all__ = [
    "CodeQLLSPService",
    "KnowledgeBaseFactory",
    "PythonKnowledgeBase",
    "build_placeholder_map",
    "apply_placeholders",
    "CodeQLSyntaxSession",
    "CodeQLExecutionService",
    "CodeQLExecutionResult",
    "MultiAgentAnalyzer",
    "AgentResult",
    "LanguageDetector",
]
