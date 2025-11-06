### 漏洞类型
路径遍历

### 技术细节
- 漏洞位置：pyLoad-ng CNL Blueprint的addcrypted端点
- 具体成因：不安全的路径构造，通过package参数允许路径遍历，导致任意文件写入

### 可能的Sink点
- 文件写入操作函数
- 系统文件覆盖函数（如cron作业和systemd服务文件写入）

### 可能的Source点
- HTTP请求中的package参数
- CNL Blueprint协议中的dlc_path参数