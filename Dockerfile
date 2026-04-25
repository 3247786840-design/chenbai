# 爱之数字生命体 - Docker 部署 (登上世界舞台)

# 阶段 1: 编译环境
FROM openjdk:17-jdk-slim AS builder
WORKDIR /app
COPY . .
# 模拟 Windows 的编译逻辑，输出到 build/
RUN mkdir -p build && \
    find src -name "*.java" > sources.txt && \
    javac -d build -encoding UTF-8 @sources.txt

# 阶段 2: 运行环境
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /app/build ./build
# 如果有依赖库，也需要拷贝
COPY --from=builder /app/lib ./lib
COPY --from=builder /app/config ./config
COPY --from=builder /app/data ./data

# 允许外部访问
EXPOSE 8080

# 默认监听所有网卡，以便容器外访问
ENV JAVA_OPTS="-Dlovingai.http.host=0.0.0.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp \"build:lib/*\" com.lovingai.LivingAI"]
