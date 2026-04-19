# Multi-module build — build context must be the repo root.
# Compose reference:
#   build:
#     context: .
#     dockerfile: backend/Dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

COPY backend/settings.xml /root/.m2/settings.xml

# parent + module POMs first for dependency cache
COPY pom.xml ./
COPY backend/pom.xml ./backend/
COPY backend-ops/pom.xml ./backend-ops/

RUN mvn -B -e -pl backend -am -DskipTests dependency:go-offline

COPY backend/src ./backend/src

RUN mvn -B -e -pl backend -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/backend/target/backend-exec.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
