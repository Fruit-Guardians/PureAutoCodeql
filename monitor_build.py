#!/usr/bin/env python3
"""
CodeQL 构建进度监控工具

实时监控 CodeQL 数据库构建过程，显示进度信息
"""

import os
import sys
import time
from pathlib import Path
import subprocess

def get_dir_size(path: Path) -> str:
    """获取目录大小"""
    try:
        if os.name == 'nt':  # Windows
            # 使用 Python 递归计算
            total = 0
            for root, dirs, files in os.walk(path):
                for f in files:
                    fp = os.path.join(root, f)
                    if os.path.exists(fp):
                        total += os.path.getsize(fp)
            # 转换为人类可读格式
            for unit in ['B', 'KB', 'MB', 'GB']:
                if total < 1024:
                    return f"{total:.1f}{unit}"
                total /= 1024
            return f"{total:.1f}TB"
        else:  # Linux/Mac
            result = subprocess.run(
                ["du", "-sh", str(path)], 
                capture_output=True, 
                text=True, 
                check=False
            )
            if result.returncode == 0:
                return result.stdout.split()[0]
    except Exception as e:
        return f"错误: {e}"
    return "未知"

def get_file_size(path: Path) -> str:
    """获取文件大小"""
    try:
        if not path.exists():
            return "不存在"
        size = path.stat().st_size
        for unit in ['B', 'KB', 'MB', 'GB']:
            if size < 1024:
                return f"{size:.1f}{unit}"
            size /= 1024
        return f"{size:.1f}TB"
    except Exception as e:
        return f"错误: {e}"

def check_docker_container() -> bool:
    """检查是否有 CodeQL 容器在运行"""
    try:
        result = subprocess.run(
            ["docker", "ps", "--filter", "ancestor=codeql-builder", "--format", "{{.ID}}"],
            capture_output=True,
            text=True,
            check=False
        )
        return bool(result.stdout.strip())
    except:
        return False

def get_docker_stats() -> dict:
    """获取 Docker 容器资源使用情况"""
    try:
        result = subprocess.run(
            ["docker", "stats", "--no-stream", "--format", "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}"],
            capture_output=True,
            text=True,
            check=False
        )
        if result.returncode == 0 and result.stdout.strip():
            lines = result.stdout.strip().split('\n')
            if lines:
                parts = lines[0].split('|')
                if len(parts) >= 3:
                    return {
                        'cpu': parts[0],
                        'mem': parts[1],
                        'mem_percent': parts[2]
                    }
    except:
        pass
    return {}

def monitor_build(case_id: str, interval: int = 5):
    """监控构建进度"""
    
    # 查找项目目录
    projects_dir = Path("projects")
    case_dir = projects_dir / case_id
    
    if not case_dir.exists():
        # 尝试模糊匹配
        matches = [d for d in projects_dir.glob("*") if case_id.lower() in d.name.lower()]
        if matches:
            case_dir = matches[0]
            print(f"找到匹配的项目: {case_dir.name}")
        else:
            print(f"❌ 找不到项目目录: {case_id}")
            print(f"\n可用的项目:")
            for d in projects_dir.glob("*"):
                if d.is_dir():
                    print(f"  - {d.name}")
            return
    
    db_dir = case_dir / "db"
    
    # 检测语言
    cpp_db = db_dir / "cpp"
    java_db = db_dir / "java"
    python_db = db_dir / "python"
    
    if cpp_db.exists():
        target_db = cpp_db
        lang = "C/C++"
    elif java_db.exists():
        target_db = java_db
        lang = "Java"
    elif python_db.exists():
        target_db = python_db
        lang = "Python"
    else:
        target_db = db_dir
        lang = "未知"
    
    print(f"🔍 监控项目: {case_dir.name}")
    print(f"📝 语言: {lang}")
    print(f"📂 数据库路径: {target_db}")
    print(f"⏱️  刷新间隔: {interval} 秒")
    print(f"\n{'='*60}")
    print(f"按 Ctrl+C 停止监控")
    print(f"{'='*60}\n")
    
    start_time = time.time()
    last_size = 0
    completed = False
    
    try:
        while not completed:
            current_time = time.strftime("%H:%M:%S")
            elapsed = int(time.time() - start_time)
            elapsed_str = f"{elapsed // 60}分{elapsed % 60}秒"
            
            print(f"[{current_time}] 已运行: {elapsed_str}")
            
            # 检查数据库目录
            if target_db.exists():
                size = get_dir_size(target_db)
                print(f"  📦 数据库大小: {size}")
                
                # 检查关键文件
                src_zip = target_db / "src.zip"
                db_yml = target_db / "codeql-database.yml"
                
                if src_zip.exists():
                    zip_size = get_file_size(src_zip)
                    print(f"  ✅ src.zip: {zip_size}")
                else:
                    print(f"  ⏳ src.zip: 生成中...")
                
                if db_yml.exists():
                    print(f"  ✅ codeql-database.yml: 存在")
                    print(f"\n{'='*60}")
                    print(f"🎉 构建完成！总耗时: {elapsed_str}")
                    print(f"{'='*60}")
                    completed = True
                    break
                else:
                    print(f"  ⏳ codeql-database.yml: 等待中...")
                
                # 检查日志文件
                log_file = db_dir / "docker_build.log"
                if log_file.exists():
                    log_size = get_file_size(log_file)
                    print(f"  📄 构建日志: {log_size}")
                    
                    # 读取最后几行
                    try:
                        with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
                            lines = f.readlines()
                            if lines:
                                # 显示最后一行非空行
                                for line in reversed(lines[-10:]):
                                    line = line.strip()
                                    if line:
                                        # 截断过长的行
                                        if len(line) > 80:
                                            line = line[:77] + "..."
                                        print(f"  💬 最新日志: {line}")
                                        break
                    except:
                        pass
            else:
                print(f"  ⏳ 等待数据库创建...")
            
            # 检查 Docker 容器状态
            if check_docker_container():
                print(f"  🐳 Docker 容器: 运行中")
                stats = get_docker_stats()
                if stats:
                    print(f"     CPU: {stats.get('cpu', 'N/A')} | 内存: {stats.get('mem', 'N/A')} ({stats.get('mem_percent', 'N/A')})")
            else:
                print(f"  🐳 Docker 容器: 未运行（可能已完成或使用本地构建）")
            
            print()  # 空行
            
            if not completed:
                time.sleep(interval)
                # 清除上面的输出（仅在终端支持时）
                if os.name != 'nt':  # Linux/Mac
                    # 向上移动光标并清除
                    lines_to_clear = 10  # 根据实际输出调整
                    print(f"\033[{lines_to_clear}A", end='')
                    for _ in range(lines_to_clear):
                        print("\033[K")  # 清除当前行
                    print(f"\033[{lines_to_clear}A", end='')
    
    except KeyboardInterrupt:
        print(f"\n\n⚠️  监控已停止（构建进程可能仍在后台运行）")
        print(f"总监控时间: {elapsed_str}")

def main():
    if len(sys.argv) < 2:
        print("用法: python monitor_build.py <case_id> [interval]")
        print("\n示例:")
        print("  python monitor_build.py CVE-2018-14618")
        print("  python monitor_build.py CVE-2018-14618 10  # 每10秒刷新")
        sys.exit(1)
    
    case_id = sys.argv[1]
    interval = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    
    monitor_build(case_id, interval)

if __name__ == "__main__":
    main()

