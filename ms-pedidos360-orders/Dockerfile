# Build from this microservice directory, not from the repository root.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src/main ./src/main
RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

# The final image contains a JRE and the executable JAR, without Maven or sources.
FROM eclipse-temurin:17-jre-jammy AS runtime
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home \
       --home-dir /nonexistent --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chmod=0444 /workspace/target/ms-pedidos360-orders-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
