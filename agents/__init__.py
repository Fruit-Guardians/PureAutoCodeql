"""
Agent modules for multi-agent vulnerability analysis.

This package contains specialized agents for different aspects of vulnerability analysis:
- CVEAnalysisAgent: Converts CVE JSON to Markdown reports
- JavaPathAnalysisAgent: Analyzes Java file paths and identifies Sink points
- JavaSourceAnalysisAgent: Identifies Source points in Java code
- PythonSourceAnalysisAgent: Identifies Source points in Python code
- PythonPathAnalysisAgent: Summarizes Sink points in Python projects
- CSourceAnalysisAgent: Identifies Source points in C/C++ code
- CPathAnalysisAgent: Summarizes Sink points in C/C++ code
- CodeQLGeneratorAgent: Generates CodeQL query code based on natural language requirements
- CodeQLRunnerAgent: Executes CodeQL queries and analyzes the results
"""
