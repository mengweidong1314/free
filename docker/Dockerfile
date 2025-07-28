# 多阶段构建Dockerfile
FROM maven:3.8.6-openjdk-17 AS builder

# 设置工作目录
WORKDIR /app

# 复制pom文件
COPY pom.xml .
COPY common/pom.xml common/
COPY user-service/pom.xml user-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY . .

# 构建应用
ARG SERVICE_NAME
RUN mvn clean package -pl ${SERVICE_NAME} -am -DskipTests

# 运行阶段
FROM openjdk:17-jre-alpine

# 安装必要的工具
RUN apk add --no-cache curl

# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar文件
ARG SERVICE_NAME
COPY --from=builder /app/${SERVICE_NAME}/target/*.jar app.jar

# 创建非root用户
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# 设置文件权限
RUN chown -R appuser:appgroup /app
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# JVM优化参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

# 暴露端口
EXPOSE 8081

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]