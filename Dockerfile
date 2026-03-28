# Multi-stage build for Spring Boot Spring Batch application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage (use Debian base for broad platform support; Alpine lacks manifest on some platforms)
FROM eclipse-temurin:17-jre

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Create non-root user and temp directory with proper permissions
RUN groupadd -r spring && useradd -r -g spring spring && \
    mkdir -p /tmp/spring-boot-tomcat && \
    chown -R spring:spring /tmp/spring-boot-tomcat && \
    chown -R spring:spring /app

# Set temp directory environment variable (machine-agnostic)
ENV JAVA_TMPDIR=/tmp/spring-boot-tomcat
ENV TMPDIR=/tmp/spring-boot-tomcat

# Switch to non-root user
USER spring

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8081/batch-jobs/actuator/health || exit 1

# Run the application with temp directory set via JVM argument
# This ensures machine-agnostic temp directory handling
ENTRYPOINT ["sh", "-c", "java -Djava.io.tmpdir=${JAVA_TMPDIR} -jar app.jar"]






















