# ── Stage 1: Build ─────────────────────────────────────────────────────────────
# Separate pom.xml copy so Maven dependencies are cached as their own layer.
# Only invalidated when pom.xml changes, not on every source edit.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
# Minimal JRE on Alpine — final image is ~250 MB vs ~600 MB with JDK
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user — required by most production security policies
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# UseContainerSupport  — JVM respects cgroup memory limits (Java 11+ default, explicit here)
# MaxRAMPercentage=75  — heap gets 75% of the container's RAM (768 MB of the 1 GB task)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
