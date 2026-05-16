# syntax=docker/dockerfile:1.6
# Generic Dockerfile reused by every service module.
# Build with:  docker build --build-arg SERVICE=order-service -t order-service .
ARG SERVICE=order-service

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl common,order-service,inventory-service,payment-service,notification-service -am dependency:go-offline
COPY common/src common/src
COPY order-service/src order-service/src
COPY inventory-service/src inventory-service/src
COPY payment-service/src payment-service/src
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/*.jar app.jar
USER app
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
