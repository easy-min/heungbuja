# ========= Build stage =========
FROM gradle:8.10.2-jdk17-alpine AS builder
WORKDIR /workspace

# Gradle 캐시 최대 활용: 래퍼/메타 먼저 복사
COPY gradlew ./
COPY gradle gradle
COPY settings.gradle ./
COPY build.gradle ./
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew

# (옵션) BuildKit 사용 시 Gradle 캐시 마운트로 속도 ↑
# RUN --mount=type=cache,target=/home/gradle/.gradle ./gradlew --no-daemon build -x test || true

# 의존성 미리 당겨서 캐시층 생성
RUN ./gradlew --no-daemon dependencies || true

# 소스 복사 후 실제 빌드
COPY src src
RUN ./gradlew --no-daemon clean bootJar

# ========= Runtime stage =========
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 타임존 적용(Alpine은 tzdata 필요)
RUN apk add --no-cache tzdata curl \
    && ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone

# 빌드 산출물 복사
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# 포트 노출
EXPOSE 8080

# JVM 옵션/스프링 프로필은 환경변수로 조절
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

ENTRYPOINT ["/bin/sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
