# syntax=docker/dockerfile:1
# Build: Maven inside the image, so `docker compose up --build` needs no local JDK or Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline || true
COPY src ./src
# Tests run in CI (mvn verify); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && cp target/secure-order-mcp-server-1.0.0.jar /build/app.jar

# Run: JRE only, non-root user, sample data for the engineering tools.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S mcp && adduser -S -G mcp -u 10001 mcp
WORKDIR /app
COPY --from=build /build/app.jar app.jar
COPY sample-data sample-data
COPY sample-app sample-app
USER mcp
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
