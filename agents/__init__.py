"""
Agent modules for multi-agent vulnerability analysis.

This package contains specialized agents for different aspects of vulnerability analysis:
- CVEAnalysisAgent: Converts CVE JSON to Markdown reports
- UnifiedSinkPathAgent: Unified agent for analyzing Sink points in Java, Python, and C/C++ code
- UnifiedSourceAnalysisAgent: Unified agent for analyzing Source points in Java, Python, and C/C++ code
- PathAnalysisAgent: Analyzes source-to-sink paths to identify isAdditionalFlowStep points
- CodeQLGeneratorAgent: Generates CodeQL query code based on natural language requirements
- CodeQLRunnerAgent: Executes CodeQL queries and analyzes the results
- SinkVerificationAgent: Verifies identified Sink points using CodeQL queries
- SourceVerificationAgent: Verifies identified Source points using CodeQL queries

NOTE: The UnifiedSinkPathAgent and UnifiedSourceAnalysisAgent are the recommended agents 
for all sink and source path analysis tasks. They support Java, Python, and C/C++ with 
consistent logic and improved efficiency.
"""

from .sink_verification_agent import SinkVerificationAgent
from .source_verification_agent import SourceVerificationAgent

__all__ = [
    "SinkVerificationAgent",
    "SourceVerificationAgent",
]
