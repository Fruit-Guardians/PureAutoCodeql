import re
from typing import Optional


def find_path_from_java_file(java_file_path: str, source_root: str = "src/main/java") -> Optional[str]:
    """
    解析单个.java文件，根据其包和类声明查找其规范源路径。

    Args:
        java_file_path: 要解析的.java文件的路径。
        source_root: 源根目录（例如"src/main/java"）。

    Returns:
        规范路径字符串（例如"src/main/java/com/foo/Bar.java"），
        如果解析失败（文件未找到，或包/类未找到）则返回None。
    """
    
    package_regex = re.compile(r"^\s*package\s+([a-zA-Z0-9_.]+);")
    class_regex = re.compile(
        r"^\s*(?:public\s+)?(?:class|interface|enum)\s+([a-zA-Z0-9_]+)"
    )

    current_package: Optional[str] = None
    current_class: Optional[str] = None

    try:
        with open(java_file_path, 'r', encoding='utf-8') as f:
            for line in f:
                if not current_package:
                    package_match = package_regex.match(line)
                    if package_match:
                        current_package = package_match.group(1)
                        continue # Package and class are rarely on the same line

                if not current_class:
                    class_match = class_regex.match(line)
                    if class_match:
                        current_class = class_match.group(1)
                
                if current_package and current_class:
                    break
    except (IOError, OSError):
        return None # Failed to read file

    if current_package and current_class:
        package_path = current_package.replace('.', '/')
        return f"{source_root}/{package_path}/{current_class}.java"
    
    return None