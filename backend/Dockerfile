# ============================================================
# Backend Dockerfile — 多阶段构建
# ============================================================
#
# 多阶段构建的好处：
# Stage 1 (build)：在 Maven 容器中编译，结果是 JAR 文件
# Stage 2 (runtime)：只把 JAR 拷进轻量 JRE 镜像，不包含 Maven/源码
# 最终镜像更小（约 200MB vs 800MB），生产环境安全性更高
# ============================================================

# ──────────────────────────────────────────────────────────
# Stage 1: 编译阶段
# ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

# 先只拷贝 pom.xml，利用 Docker layer 缓存：
# 只要 pom.xml 不变，Maven 依赖下载层就会命中缓存，大幅加快构建速度
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 再拷贝源码进行编译
COPY src ./src
RUN mvn package -DskipTests -q

# ──────────────────────────────────────────────────────────
# Stage 2: 运行阶段
# ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户运行应用（安全最佳实践）
RUN addgroup -g 1001 appgroup && \
    adduser -D -u 1001 -G appgroup appuser

# 从编译阶段拷贝 JAR
COPY --from=build /app/target/pia-server-*.jar app.jar

# 切换到非 root 用户
USER appuser

# 健康检查端口
EXPOSE 8080

# JVM 参数说明：
# -XX:+UseContainerSupport：感知容器 CPU/内存限制（Docker 环境必须）
# -XX:MaxRAMPercentage=75.0：最多使用容器内存的 75%
# -Djava.security.egd：加速 SecureRandom，避免应用启动缓慢
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
