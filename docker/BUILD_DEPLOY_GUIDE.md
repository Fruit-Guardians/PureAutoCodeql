# Docker 镜像构建与服务器部署指南

## 一、本地构建镜像

### 1. 基础构建命令

```bash
# 进入项目根目录
cd /path/to/PureAutoCodeql

# 构建镜像（默认标签）
docker build -t pure-codeql-cpp:latest -f docker/Dockerfile docker/

# 构建并指定版本号
docker build -t pure-codeql-cpp:v1.0.0 -f docker/Dockerfile docker/
```

### 2. 构建选项说明

```bash
# 使用国内镜像源（如果在国内网络环境）
docker build \
  --build-arg UBUNTU_MIRROR=mirrors.tuna.tsinghua.edu.cn \
  -t pure-codeql-cpp:latest \
  -f docker/Dockerfile docker/

# 指定 CodeQL 版本（修改 Dockerfile 第55行）
# 或者使用构建参数（需要先修改Dockerfile支持ARG）
```

### 3. 验证构建成功

```bash
# 查看镜像
docker images | grep pure-codeql-cpp

# 测试镜像
docker run --rm pure-codeql-cpp:latest --help
# 应该显示 CodeQL 相关的帮助信息
```

---

## 二、推送到镜像仓库

### 方案A：Docker Hub（公开仓库）

```bash
# 1. 登录 Docker Hub
docker login

# 2. 打标签
docker tag pure-codeql-cpp:latest your-username/pure-codeql-cpp:latest
docker tag pure-codeql-cpp:latest your-username/pure-codeql-cpp:v1.0.0

# 3. 推送
docker push your-username/pure-codeql-cpp:latest
docker push your-username/pure-codeql-cpp:v1.0.0
```

### 方案B：阿里云容器镜像服务（推荐国内）

```bash
# 1. 登录阿里云镜像仓库
docker login --username=your-aliyun-account registry.cn-hangzhou.aliyuncs.com

# 2. 打标签
docker tag pure-codeql-cpp:latest \
  registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest

# 3. 推送
docker push registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest
```

**创建阿里云仓库**：
1. 访问 https://cr.console.aliyun.com/
2. 创建命名空间（如 `codeql-tools`）
3. 创建镜像仓库 `pure-codeql-cpp`
4. 选择"公开"或"私有"

### 方案C：自建私有镜像仓库（企业方案）

```bash
# 1. 在服务器上启动 Registry
docker run -d -p 5000:5000 \
  --restart=always \
  --name registry \
  -v /data/registry:/var/lib/registry \
  registry:2

# 2. 打标签
docker tag pure-codeql-cpp:latest your-server.com:5000/pure-codeql-cpp:latest

# 3. 推送
docker push your-server.com:5000/pure-codeql-cpp:latest
```

### 方案D：Harbor（企业级推荐）

```bash
# 1. 登录 Harbor
docker login harbor.your-company.com

# 2. 推送到项目
docker tag pure-codeql-cpp:latest harbor.your-company.com/codeql/pure-codeql-cpp:latest
docker push harbor.your-company.com/codeql/pure-codeql-cpp:latest
```

---

## 三、服务器部署

### 部署方式1：直接使用Docker命令

#### 3.1 拉取镜像

```bash
# 从 Docker Hub
docker pull your-username/pure-codeql-cpp:latest

# 从阿里云
docker pull registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest

# 从私有仓库
docker pull your-server.com:5000/pure-codeql-cpp:latest
```

#### 3.2 配置系统

更新服务器上的配置文件：

```bash
# 编辑配置文件
vim ~/PureAutoCodeql/config/keys.toml
```

```toml
[settings]
# 启用 Docker 构建（服务器环境推荐）
use_docker_for_cpp = false  # 优先本地两步走
prefer_local_cpp_build = true

# 指定你的镜像
docker_builder_image = "your-username/pure-codeql-cpp:latest"
# 或
docker_builder_image = "registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest"
```

#### 3.3 测试运行

```bash
# 分析一个案例
cd ~/PureAutoCodeql
uv run Analyze.py --case /path/to/CVE-2024-XXXX
```

### 部署方式2：Docker Compose（推荐）

#### 3.4 创建 docker-compose.yml

在项目根目录创建：

```yaml
# docker-compose.yml
version: '3.8'

services:
  codeql-analyzer:
    image: your-username/pure-codeql-cpp:latest
    container_name: codeql-analyzer
    volumes:
      # 挂载项目目录
      - ./projects:/work/projects
      # 挂载源码目录（如果需要）
      - /data/cve-targets:/targets:ro
    environment:
      - CODEQL_LANG=cpp
    restart: unless-stopped
    # 如果需要持久化容器
    # command: tail -f /dev/null
```

#### 3.5 启动服务

```bash
# 启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 进入容器
docker-compose exec codeql-analyzer bash

# 停止
docker-compose down
```

### 部署方式3：Kubernetes（大规模部署）

#### 3.6 创建部署配置

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: codeql-analyzer
  namespace: codeql
spec:
  replicas: 3
  selector:
    matchLabels:
      app: codeql-analyzer
  template:
    metadata:
      labels:
        app: codeql-analyzer
    spec:
      containers:
      - name: analyzer
        image: your-username/pure-codeql-cpp:latest
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        volumeMounts:
        - name: projects
          mountPath: /work/projects
        - name: targets
          mountPath: /targets
          readOnly: true
      volumes:
      - name: projects
        persistentVolumeClaim:
          claimName: codeql-projects-pvc
      - name: targets
        nfs:
          server: nfs-server.example.com
          path: /exports/cve-targets
```

#### 3.7 部署到K8s

```bash
# 创建命名空间
kubectl create namespace codeql

# 应用部署
kubectl apply -f k8s/deployment.yaml

# 查看状态
kubectl get pods -n codeql

# 查看日志
kubectl logs -f -n codeql deployment/codeql-analyzer
```

---

## 四、镜像优化建议

### 4.1 减小镜像体积

创建 `.dockerignore` 文件：

```bash
# docker/.dockerignore
*.pyc
__pycache__
.git
.vscode
*.log
temp/
output/
projects/
```

### 4.2 多阶段构建（可选）

如果需要进一步优化：

```dockerfile
# 阶段1：构建阶段
FROM ubuntu:22.04 as builder
# ... 安装构建工具 ...
# ... 下载 CodeQL ...

# 阶段2：运行阶段
FROM ubuntu:22.04
COPY --from=builder /opt/codeql /opt/codeql
# 只安装运行时依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libssl-dev \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*
```

### 4.3 定期更新 CodeQL 版本

```bash
# 检查最新版本
# https://github.com/github/codeql-cli-binaries/releases

# 修改 Dockerfile 第55行的版本号
wget -q https://github.com/github/codeql-cli-binaries/releases/download/v2.XX.X/codeql-linux64.zip

# 重新构建
docker build -t pure-codeql-cpp:v2.XX.X -f docker/Dockerfile docker/
```

---

## 五、服务器环境配置

### 5.1 系统要求

```bash
# 最低配置
CPU: 4核
内存: 8GB
磁盘: 100GB（用于存储数据库和项目）

# 推荐配置
CPU: 8核+
内存: 16GB+
磁盘: 500GB SSD
```

### 5.2 安装 Docker

**Ubuntu/Debian**:
```bash
# 卸载旧版本
sudo apt-get remove docker docker-engine docker.io containerd runc

# 安装依赖
sudo apt-get update
sudo apt-get install ca-certificates curl gnupg

# 添加 Docker 官方 GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# 添加仓库
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 安装 Docker Engine
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker

# 添加当前用户到 docker 组（避免每次 sudo）
sudo usermod -aG docker $USER
# 重新登录后生效
```

**CentOS/RHEL**:
```bash
# 安装
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install docker-ce docker-ce-cli containerd.io
sudo systemctl start docker
sudo systemctl enable docker
```

### 5.3 配置 Docker 加速（国内）

```bash
# 编辑 daemon.json
sudo vim /etc/docker/daemon.json
```

```json
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
```

```bash
# 重启 Docker
sudo systemctl daemon-reload
sudo systemctl restart docker
```

---

## 六、快速部署脚本

### 6.1 一键部署脚本

创建 `scripts/deploy-server.sh`：

```bash
#!/bin/bash
set -e

echo "🚀 PureAutoCodeQL 服务器部署脚本"

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker"
    exit 1
fi

# 2. 拉取镜像
REGISTRY="${REGISTRY:-registry.cn-hangzhou.aliyuncs.com}"
NAMESPACE="${NAMESPACE:-your-namespace}"
IMAGE="${REGISTRY}/${NAMESPACE}/pure-codeql-cpp:latest"

echo "📦 拉取镜像: ${IMAGE}"
docker pull "${IMAGE}"

# 3. 更新配置
CONFIG_FILE="config/keys.toml"
if [ -f "${CONFIG_FILE}" ]; then
    echo "⚙️ 更新配置文件..."
    # 使用 sed 更新镜像名称
    sed -i "s|docker_builder_image = .*|docker_builder_image = \"${IMAGE}\"|" "${CONFIG_FILE}"
else
    echo "⚠️ 配置文件不存在，跳过更新"
fi

# 4. 测试镜像
echo "🧪 测试镜像..."
docker run --rm "${IMAGE}" codeql version

echo "✅ 部署完成！"
echo ""
echo "使用方法："
echo "  uv run Analyze.py --case /path/to/CVE-XXXX"
```

使用方法：

```bash
# 给予执行权限
chmod +x scripts/deploy-server.sh

# 执行部署（使用默认镜像）
./scripts/deploy-server.sh

# 或指定自定义镜像仓库
REGISTRY=your-server.com:5000 NAMESPACE=codeql ./scripts/deploy-server.sh
```

---

## 七、常见问题

### Q1: 镜像构建太慢？

**A**: 使用国内镜像源和构建缓存：

```bash
# 使用阿里云构建机器
# 或使用 buildx 和缓存
docker buildx build \
  --cache-from type=registry,ref=your-repo/pure-codeql-cpp:cache \
  --cache-to type=registry,ref=your-repo/pure-codeql-cpp:cache \
  -t pure-codeql-cpp:latest \
  -f docker/Dockerfile docker/
```

### Q2: 服务器上拉取镜像失败？

**A**: 检查网络和配置加速器：

```bash
# 测试网络
docker pull hello-world

# 如果失败，配置镜像加速（见5.3节）
```

### Q3: 容器内存不足？

**A**: 调整 Docker 资源限制：

```bash
# 运行时指定
docker run --rm \
  --memory=16g \
  --cpus=8 \
  -v ./projects:/work/projects \
  pure-codeql-cpp:latest
```

### Q4: 想要批量分析多个项目？

**A**: 使用脚本循环处理：

```bash
#!/bin/bash
# scripts/batch-analyze.sh
for case_dir in /data/cve-targets/*/; do
    echo "分析: ${case_dir}"
    uv run Analyze.py --case "${case_dir}"
done
```

---

## 八、监控和日志

### 8.1 查看容器日志

```bash
# 实时查看
docker logs -f <container-id>

# 导出日志
docker logs <container-id> > /var/log/codeql-build.log 2>&1
```

### 8.2 资源监控

```bash
# 查看容器资源使用
docker stats

# 持续监控
watch -n 1 docker stats
```

---

## 总结

**快速开始（推荐流程）**：

```bash
# 1. 构建镜像
docker build -t pure-codeql-cpp:latest -f docker/Dockerfile docker/

# 2. 推送到阿里云（国内服务器）
docker tag pure-codeql-cpp:latest \
  registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest
docker push registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest

# 3. 服务器上拉取
docker pull registry.cn-hangzhou.aliyuncs.com/your-namespace/pure-codeql-cpp:latest

# 4. 更新配置
vim ~/PureAutoCodeql/config/keys.toml
# 修改 docker_builder_image

# 5. 开始分析
uv run Analyze.py --case /path/to/CVE-XXXX
```

**🎉 现在你的服务器已经可以自动化分析C/CPP漏洞了！**

