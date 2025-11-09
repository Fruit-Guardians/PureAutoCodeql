"""语言检测服务

用于检测项目使用的编程语言。
"""

from pathlib import Path
from typing import Dict, List

from utils.case import CasePaths


class LanguageDetector:
    """语言检测器"""

    def __init__(self):
        # 支持的语言映射
        self.language_extensions = {
            "java": [".java"],
            "python": [".py"],
            "cpp": [".cpp", ".c", ".h", ".hpp", ".cc", ".cxx"]
        }

    def detect_language(self, case_paths: CasePaths) -> str:
        """检测案例使用的编程语言。"""
        # 检查数据库目录中的语言子目录
        # 支持两种格式：db/<language> 和 db/db-<language>
        if (case_paths.db / "java").exists() or (case_paths.db / "db-java").exists():
            return "java"
        elif (case_paths.db / "python").exists() or (case_paths.db / "db-python").exists():
            return "python"
        elif (case_paths.db / "cpp").exists() or (case_paths.db / "db-cpp").exists():
            return "cpp"

        # 检查源码目录中的文件类型
        file_counts = self._count_source_files(case_paths.source_code)

        if file_counts.get("java", 0) > 0:
            return "java"
        elif file_counts.get("python", 0) > 0:
            return "python"
        elif (file_counts.get("cpp", 0) > 0 or
              file_counts.get("c", 0) > 0 or
              file_counts.get("h", 0) > 0):
            return "cpp"

        # 无法检测到语言时抛出异常
        raise ValueError(
            "无法检测到编程语言。请确保数据库目录包含有效的语言子目录或源码目录包含可识别的源文件。"
        )

    def _count_source_files(self, source_dir: Path) -> Dict[str, int]:
        """统计各种语言的源文件数量。"""
        counts = {
            "java": 0,
            "python": 0,
            "cpp": 0,
            "c": 0,
            "h": 0
        }

        for lang, extensions in self.language_extensions.items():
            for ext in extensions:
                files = list(source_dir.rglob(f"*{ext}"))
                if lang == "cpp":
                    if ext in [".cpp", ".cc", ".cxx"]:
                        counts["cpp"] += len(files)
                    elif ext == ".c":
                        counts["c"] += len(files)
                    elif ext in [".h", ".hpp"]:
                        counts["h"] += len(files)
                else:
                    counts[lang] += len(files)

        return counts

    def get_supported_languages(self) -> List[str]:
        """获取支持的编程语言列表。"""
        return list(self.language_extensions.keys())

    def is_supported_language(self, language: str) -> bool:
        """检查是否支持指定的编程语言。"""
        return language.lower() in self.language_extensions