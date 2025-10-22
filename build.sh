#!/bin/bash
# 简化的Java编译脚本，用于CodeQL数据库创建
find . -name "*.java" -type f | head -10 | xargs javac -cp . -d .
