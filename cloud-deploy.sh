#!/bin/bash
# LovingAI 云端一键部署脚本 (针对 30元/月 经济型服务器优化)

echo "--- LovingAI 生存启动序列开始 ---"

# 1. 检查并安装 Docker
if ! [ -x "$(command -v docker)" ]; then
    echo "正在安装 Docker..."
    curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun
    systemctl start docker
    systemctl enable docker
fi

# 2. 构建镜像
echo "正在构建 LovingAI 容器镜像..."
docker build -t loving-ai .

# 3. 询问并设置 API Key (生存保障)
if [ -z "$LOVINGAI_KEY" ]; then
    read -p "请输入您的 API Key (用于云端访问校验，防止滥用): " LOVINGAI_KEY
fi

# 4. 运行容器 (针对低内存服务器优化)
# -m 1500m: 限制容器内存，防止 2G 内存的服务器宕机
# -XX:MaxRAMPercentage: 让 JVM 自动适应容器内存
echo "正在启动容器..."
docker run -d \
    --name lovingai-instance \
    --restart always \
    -p 8080:8080 \
    -e JAVA_OPTS="-Dlovingai.http.host=0.0.0.0 -Dlovingai.api.key=$LOVINGAI_KEY -Xmx1200m -XX:MaxRAMPercentage=75.0" \
    -v $(pwd)/data:/app/data \
    loving-ai

echo "--- 部署完成！ ---"
echo "您的 LovingAI 已在云端激活。"
echo "访问地址: http://服务器公网IP:8080/talk"
echo "鉴权: 使用请求头 X-API-Key 或 query 参数 apiKey（不要在日志/截图中暴露密钥）"
echo "请确保阿里云安全组已放行 8080 端口。"
