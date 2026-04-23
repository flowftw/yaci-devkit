# Multi-stage build for Cardano Operator

# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY buildSrc buildSrc
COPY settings.gradle .
COPY src src

RUN ./gradlew build --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Run as non-root user for security
RUN useradd -m -u 1001 appuser && \
    chown -R appuser:appuser /app
USER appuser

# Expose port (if needed for health checks or metrics)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
