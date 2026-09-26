# 사용: docker build --build-arg TARGET=<service> . (예: TARGET=post-service)
ARG TARGET

# ------------------------------- builder -------------------------------------
FROM amazoncorretto:21.0.4 AS builder
WORKDIR /app
ARG TARGET
RUN test -n "$TARGET" || (echo "ERROR: --build-arg TARGET=<service> 가 필요합니다" && exit 1)

# (1) Gradle 래퍼와 빌드 스크립트만 먼저 — 이 레이어는 의존성이 바뀔 때만 무효화됩니다.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY common/core/build.gradle            common/core/
COPY common/api/build.gradle             common/api/
COPY common/db/build.gradle              common/db/
COPY api-gateway/build.gradle            api-gateway/
COPY discovery-service/build.gradle      discovery-service/
COPY member-service/build.gradle         member-service/
COPY post-service/build.gradle           post-service/
COPY search-service/build.gradle         search-service/
COPY user-activity-service/build.gradle  user-activity-service/
COPY web-api-service/build.gradle        web-api-service/

# 의존성만 미리 내려받습니다. 소스가 없어 일부 태스크가 실패할 수 있으므로 결과는 무시합니다.
RUN chmod +x gradlew \
 && ./gradlew --no-daemon --console=plain \
      :${TARGET}:dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

# (2) 소스 복사 후 실제 빌드
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon --console=plain :${TARGET}:bootJar

# ------------------------------- runtime -------------------------------------
FROM amazoncorretto:21.0.4
WORKDIR /app
ARG TARGET

# bootJar 만 만들었으므로 libs 아래 JAR 은 하나입니다.
COPY --from=builder /app/${TARGET}/build/libs/*.jar app.jar

# exec 형식 유지 — 셸 변수 확장이 없습니다.
# JVM 옵션은 ECS 태스크 정의의 JAVA_TOOL_OPTIONS 환경 변수로 들어갑니다.
ENTRYPOINT ["java", "-jar", "app.jar"]
