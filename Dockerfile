FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/secure-order-mcp-server-1.0.0.jar app.jar
COPY sample-data sample-data
COPY sample-app sample-app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
