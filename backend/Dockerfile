# Stage 1: Build the Spring Boot application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy Maven POM and pre-fetch dependencies for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source files and package application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Java Runtime Environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy compiled JAR artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Render exposes PORT environment variable (default fallback to 8081)
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
