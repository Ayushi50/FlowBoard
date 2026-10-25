# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/flowboard-backend-*.jar extract --layers --destination /build/layers

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/layers/dependencies/ ./
COPY --from=build /build/layers/spring-boot-loader/ ./
COPY --from=build /build/layers/snapshot-dependencies/ ./
COPY --from=build /build/layers/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "flowboard-backend-1.0.0.jar"]
