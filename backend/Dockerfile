# Multi-stage Dockerfile for Pure Java 21 SE Backend
# Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source and build fat jar (skip tests during container build as requested)
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user and secure upload directory
RUN addgroup -S fitwell && adduser -S fitwell -G fitwell && \
    mkdir -p /app/uploads && chown -R fitwell:fitwell /app

# Copy artifact from builder
COPY --from=builder /build/target/fitwell-backend-1.0.0-SNAPSHOT.jar /app/app.jar

USER fitwell
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-jar", "/app/app.jar"]

