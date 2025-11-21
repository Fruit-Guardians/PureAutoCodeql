# 基础镜像：使用 Ubuntu 22.04 作为坚实的底座
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# 替换为清华大学镜像源（通常比阿里云更稳定）
RUN echo "deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy main restricted universe multiverse" > /etc/apt/sources.list && \
    echo "deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-updates main restricted universe multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-backports main restricted universe multiverse" >> /etc/apt/sources.list && \
    echo "deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ jammy-security main restricted universe multiverse" >> /etc/apt/sources.list

# ============================================================================
# 1. 安装核心构建工具链与运行时
# ============================================================================
# 更新源并安装基础工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    cmake \
    automake \
    libtool \
    pkg-config \
    wget \
    curl \
    unzip \
    zip \
    ca-certificates \
    gnupg \
    lsb-release \
    software-properties-common \
    python3 \
    python3-pip \
    # Java 构建工具 (为了支持混合项目或作为构建依赖)
    maven \
    gradle \
    ant \
    # 现代构建系统
    ninja-build \
    && rm -rf /var/lib/apt/lists/*

# ============================================================================
# 2. 安装 CodeQL CLI
# ============================================================================
# 使用官方发布的二进制包 (v2.16.4 是一个稳定版本，可根据需要更新)
RUN wget -q https://github.com/github/codeql-cli-binaries/releases/download/v2.23.3/codeql-linux64.zip -O codeql.zip && \
    unzip -q codeql.zip -d /opt && \
    rm codeql.zip

# 将 CodeQL 添加到 PATH
ENV PATH="/opt/codeql:${PATH}"

# ============================================================================
# 3. "胖镜像"策略：预装高频 C/C++ 开发依赖库
# ============================================================================
# 这部分是为了解决绝大多数 "missing header/library" 错误
RUN apt-get update && apt-get install -y --no-install-recommends \
    # SSL/TLS
    libssl-dev \
    # 压缩库
    zlib1g-dev \
    libbz2-dev \
    liblzma-dev \
    # Boost (C++ 准标准库)
    libboost-all-dev \
    # 数据库连接
    libsqlite3-dev \
    libmysqlclient-dev \
    libpq-dev \
    # 网络与 XML
    libpcap-dev \
    libcurl4-openssl-dev \
    libxml2-dev \
    libxslt1-dev \
    libyaml-dev \
    # 图形与多媒体 (部分桌面应用需要)
    libpng-dev \
    libjpeg-dev \
    libfreetype6-dev \
    # 系统底层
    libncurses5-dev \
    libreadline-dev \
    libffi-dev \
    uuid-dev \
    && rm -rf /var/lib/apt/lists/*

# ============================================================================
# 4. 配置工作环境
# ============================================================================
WORKDIR /work

# 复制入口脚本
COPY builder_entrypoint.py /usr/local/bin/builder_entrypoint.py
RUN chmod +x /usr/local/bin/builder_entrypoint.py

# 设置入口点
ENTRYPOINT ["python3", "/usr/local/bin/builder_entrypoint.py"]

