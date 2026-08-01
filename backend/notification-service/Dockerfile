# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache parent POM deps first (layer invalidates only if pom.xml changes)
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY advisor-service/pom.xml advisor-service/
COPY chat-service/pom.xml chat-service/
COPY booking-service/pom.xml booking-service/
COPY notification-service/pom.xml notification-service/
COPY admin-service/pom.xml admin-service/

# Mount Maven cache as a build cache for faster rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -pl notification-service -am -q

COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl notification-service -am -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && \
    addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app

COPY --from=build /workspace/notification-service/target/*.jar app.jar

EXPOSE 7187
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
