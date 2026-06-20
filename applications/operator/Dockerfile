# Multi-stage build for Cardano Operator

ARG APPLICATIONS_DIR=applications
ARG OPERATOR_DIR=$APPLICATIONS_DIR/operator

# Build stage
FROM eclipse-temurin:21-jdk AS builder

ARG OPERATOR_DIR

COPY . /app

WORKDIR /app/$OPERATOR_DIR

RUN ./gradlew build --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

ARG OPERATOR_DIR
ARG GENESIS_CONFIG_DIR

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/$OPERATOR_DIR/build/libs/*.jar app.jar

# Run as non-root user for security
RUN useradd -m -u 1001 appuser && \
    chown -R appuser:appuser /app
USER appuser

# Expose port (if needed for health checks or metrics)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
