# syntax=docker/dockerfile:1
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY --chmod=0755 mvnw mvnw
COPY pom.xml pom.xml
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:25.0.5_8-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/target/order-inventory-system-*.jar app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=deploy
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
